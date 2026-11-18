package me.ogsammaenr.ogui.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SRequestAssetPayload;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.network.S2CAssetUnavailablePayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.runtime.OguiAssetRegistry;
import me.ogsammaenr.ogui.vanilla.OguiChestMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies server asset registration, authorized multi-chunk PNG & structure shell transfers,
 * rejection of unreferenced assets, and v1-to-v2 legacy fallback protection.
 */
public final class OguiAssetProtocolGameTests {
	@GameTest
	public void authorizedMultiChunkAssetTransferAndV1FallbackWork(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		// Build a 60 KiB valid PNG-header blob (> 24 KiB chunk size -> forces 3 chunks)
		byte[] bigPng = new byte[60_000];
		bigPng[0] = (byte) 0x89;
		bigPng[1] = 0x50;
		bigPng[2] = 0x4E;
		bigPng[3] = 0x47;
		bigPng[4] = 0x0D;
		bigPng[5] = 0x0A;
		bigPng[6] = 0x1A;
		bigPng[7] = 0x0A;
		for (int i = 8; i < bigPng.length; i++) {
			bigPng[i] = (byte) ((i * 31) & 0xFF);
		}
		OguiAssetRef pngRef = Ogui.service().registerPngAsset(bigPng);

		// Unreferenced secret asset that the screen never exposes
		byte[] secretPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6};
		OguiAssetRef unreferencedRef = Ogui.service().registerPngAsset(secretPng);

		OguiAssetRef structRef = Ogui.service().registerStructureAsset(OguiStructureShell.fromBlocks(List.of(
			new OguiStructureShell.PositionedBlock(0, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState()),
			new OguiStructureShell.PositionedBlock(0, 1, 0, Blocks.CHEST.defaultBlockState())
		)));

		Identifier galleryScreenId = Identifier.fromNamespaceAndPath("ogui_test", "gallery_" + suffix);
		Ogui.service().registerScreen(galleryScreenId, ctx -> OguiScreenModel.pagedGrid("Asset Gallery")
			.addEntry(OguiWidget.button("card_1", OguiIcon.item("minecraft:grass_block"), OguiText.nonItalic("Starter", ChatFormatting.GREEN))
				.previewImage(pngRef)
				.build())
			.addFooterWidget(OguiWidget.structurePreview("footer_struct", OguiIcon.item("minecraft:oak_sapling"), OguiText.nonItalic("Shell"))
				.structureAsset(structRef)
				.build())
			.build());

		AtomicReference<EmbeddedChannel> legacyChRef = new AtomicReference<>();
		AtomicReference<EmbeddedChannel> v2ChRef = new AtomicReference<>();
		ServerPlayer legacyV1 = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiLegacyV1", GameType.SURVIVAL, legacyChRef);
		ServerPlayer modernV2 = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiModernV2", GameType.SURVIVAL, v2ChRef);

		try {
			// Legacy PROTOCOL_VERSION=1 client must NOT receive v2 custom payloads -> seamless ChestMenu fallback
			OguiGameTestSupport.registerOguiClientChannels(legacyV1);
			legacyV1.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SClientHelloPayload(1, "1.0.0")));
			helper.assertTrue(!Ogui.service().isClientRendererAvailable(legacyV1), "v1 client must be incompatible with v2 protocol");
			helper.assertTrue(Ogui.service().openScreen(legacyV1, galleryScreenId), "Expected v1 fallback openScreen true");
			helper.assertTrue(legacyV1.containerMenu instanceof OguiChestMenu, "Expected v1 client to fall back to OguiChestMenu");

			// Modern v2 client opens gallery, receives S2COpenScreenPayload with asset refs
			OguiGameTestSupport.registerOguiClientChannels(modernV2);
			modernV2.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SClientHelloPayload(Ogui.PROTOCOL_VERSION, "1.0.0")));
			OguiGameTestSupport.drainOutboundPackets(v2ChRef.get());

			helper.assertTrue(Ogui.service().openScreen(modernV2, galleryScreenId), "Expected v2 openScreen true");
			List<Packet<?>> openPackets = OguiGameTestSupport.drainOutboundPackets(v2ChRef.get());
			S2COpenScreenPayload openPayload = openPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.filter(S2COpenScreenPayload.class::isInstance)
				.map(S2COpenScreenPayload.class::cast)
				.findFirst()
				.orElse(null);
			helper.assertTrue(openPayload != null, "Expected S2COpenScreenPayload for v2 client");

			// Request authorized multi-chunk PNG asset
			modernV2.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SRequestAssetPayload(pngRef.sha256())));
			List<S2CAssetChunkPayload> chunks = OguiGameTestSupport.drainOutboundPackets(v2ChRef.get()).stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.filter(S2CAssetChunkPayload.class::isInstance)
				.map(S2CAssetChunkPayload.class::cast)
				.sorted(Comparator.comparingInt(S2CAssetChunkPayload::chunkIndex))
				.toList();
			helper.assertTrue(chunks.size() == 3, "Expected 60KB PNG to stream in 3 chunks, got " + chunks.size());

			ByteArrayOutputStream assembled = new ByteArrayOutputStream();
			for (S2CAssetChunkPayload chunk : chunks) {
				helper.assertTrue(chunk.kind() == OguiAssetKind.PNG, "Expected PNG chunk kind");
				assembled.writeBytes(chunk.data());
			}
			helper.assertTrue(OguiAssetRegistry.sha256Hex(assembled.toByteArray()).equals(pngRef.sha256()),
				"Reassembled chunk SHA-256 must match registered PNG");

			// Request unauthorized unreferenced asset -> must receive S2CAssetUnavailablePayload
			modernV2.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SRequestAssetPayload(unreferencedRef.sha256())));
			boolean gotUnavailable = OguiGameTestSupport.drainOutboundPackets(v2ChRef.get()).stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.anyMatch(p -> p instanceof S2CAssetUnavailablePayload u && u.sha256().equals(unreferencedRef.sha256()));
			helper.assertTrue(gotUnavailable, "Expected S2CAssetUnavailablePayload for unreferenced asset");

			helper.succeed();
		} finally {
			OguiGameTestSupport.cleanupPlayers(server, legacyV1, modernV2);
		}
	}
}
