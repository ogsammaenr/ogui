package me.ogsammaenr.ogui;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SCloseNotifyPayload;
import me.ogsammaenr.ogui.network.C2SRequestAssetPayload;
import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.C2SWidgetInteractPayload;
import me.ogsammaenr.ogui.network.OguiCodecSupport;
import me.ogsammaenr.ogui.network.OguiTabDto;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.network.S2CAssetUnavailablePayload;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiCodecTest {
	private static RegistryAccess registryAccess;

	@BeforeAll
	static void bootstrap() {
		registryAccess = OguiTestBootstrap.ensureBootstrapped();
	}

	private static RegistryFriendlyByteBuf newBuf() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
	}

	@Test
	void roundTripsAllNetworkPayloadsAndWidgetDtos() {
		UUID sessionId = UUID.randomUUID();
		UUID playerHead = UUID.randomUUID();

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFF10B981)
			.subtitle("Market Catalog")
			.styleTag("market")
			.addSummaryCard(OguiSummaryCard.of(
				OguiIcon.item(Items.EMERALD),
				OguiText.nonItalic("Balance", ChatFormatting.GREEN),
				List.of(OguiText.nonItalic("12,450 Coins", ChatFormatting.GOLD))
			))
			.build();

		OguiWidgetDto btn = new OguiWidgetDto.ButtonDto(
			"buy_diamond",
			OguiIcon.itemStack(new ItemStack(Items.DIAMOND, 4)),
			OguiText.nonItalic("Diamond", ChatFormatting.AQUA),
			List.of(OguiText.nonItalic("Click to inspect", ChatFormatting.GRAY)),
			OguiText.nonItalic("500g", ChatFormatting.GOLD),
			true,
			OguiConfirmSpec.of("confirm_buy", "Click again to purchase")
		);
		OguiWidgetDto toggle = new OguiWidgetDto.ToggleDto(
			"in_stock",
			OguiIcon.item(Items.REDSTONE_TORCH),
			OguiIcon.item(Items.GLOWSTONE_DUST),
			OguiText.nonItalic("In Stock Only"),
			true,
			OguiText.nonItalic("On", ChatFormatting.GREEN),
			OguiText.nonItalic("Off", ChatFormatting.RED),
			List.of(),
			true,
			null
		);
		OguiWidgetDto cycle = new OguiWidgetDto.CycleDto(
			"sort",
			OguiIcon.item(Items.HOPPER),
			OguiText.nonItalic("Sort"),
			List.of(
				new OguiWidgetDto.CycleOptionDto("price", OguiText.nonItalic("Price"), null),
				new OguiWidgetDto.CycleOptionDto("name", OguiText.nonItalic("Name"), OguiIcon.item(Items.NAME_TAG))
			),
			1,
			List.of(),
			true
		);
		OguiWidgetDto qty = new OguiWidgetDto.QuantityDto(
			"qty",
			OguiIcon.item(Items.CHEST),
			OguiText.nonItalic("Amount"),
			16,
			1,
			640,
			List.of(1, 16, 64),
			OguiText.nonItalic("Total: 8,000g", ChatFormatting.GOLD),
			List.of(),
			true
		);
		OguiWidgetDto input = new OguiWidgetDto.TextInputDto(
			"search",
			"search",
			OguiIcon.item(Items.COMPASS),
			OguiText.nonItalic("Search"),
			OguiText.nonItalic("Type item name..."),
			"dia",
			48,
			OguiWidget.InputMode.SEARCH,
			true,
			List.of(),
			true
		);
		OguiWidgetDto headBtn = new OguiWidgetDto.ButtonDto(
			"seller",
			OguiIcon.playerHead(ResolvableProfile.createUnresolved(playerHead)),
			OguiText.nonItalic("Seller"),
			List.of(),
			null,
			true,
			null
		);

		ScreenSnapshotDto snap = new ScreenSnapshotDto(
			Ogui.id("test_market"),
			"global",
			OguiText.nonItalic("Server Market", ChatFormatting.AQUA),
			LayoutArchetype.TABBED_PANEL,
			theme,
			List.of(new OguiTabDto("ores", OguiIcon.item(Items.DIAMOND_ORE), OguiText.nonItalic("Ores"), List.of(), true)),
			"ores",
			0,
			3,
			70,
			true,
			List.of(input, cycle, toggle),
			List.of(btn, headBtn),
			List.of(qty),
			btn,
			List.of(),
			List.of(qty, input),
			List.of(btn),
			6,
			Map.of(0, btn),
			null,
			"confirm_buy"
		);

		// S2COpenScreenPayload
		RegistryFriendlyByteBuf openBuf = newBuf();
		S2COpenScreenPayload openIn = new S2COpenScreenPayload(sessionId, 2, snap);
		S2COpenScreenPayload.STREAM_CODEC.encode(openBuf, openIn);
		S2COpenScreenPayload openOut = S2COpenScreenPayload.STREAM_CODEC.decode(openBuf);
		assertEquals(sessionId, openOut.sessionId());
		assertEquals(2, openOut.revision());
		assertEquals(LayoutArchetype.TABBED_PANEL, openOut.snapshot().archetype());
		assertEquals(0xFF10B981, openOut.snapshot().theme().accentColor());
		assertEquals("ores", openOut.snapshot().activeTabId());
		assertEquals("confirm_buy", openOut.snapshot().pendingConfirmKey());
		assertEquals(2, openOut.snapshot().entries().size());
		OguiWidgetDto.ButtonDto decodedBtn = assertInstanceOf(OguiWidgetDto.ButtonDto.class, openOut.snapshot().entries().getFirst());
		assertNotNull(decodedBtn.icon().itemStackOverride());
		assertTrue(decodedBtn.icon().itemStackOverride().is(Items.DIAMOND));
		assertEquals(4, decodedBtn.icon().count());

		// S2CUpdateScreenPayload
		RegistryFriendlyByteBuf updateBuf = newBuf();
		S2CUpdateScreenPayload updateIn = new S2CUpdateScreenPayload(sessionId, 3, snap);
		S2CUpdateScreenPayload.STREAM_CODEC.encode(updateBuf, updateIn);
		assertEquals(3, S2CUpdateScreenPayload.STREAM_CODEC.decode(updateBuf).revision());

		// Handshake payloads
		RegistryFriendlyByteBuf helloC2s = newBuf();
		C2SClientHelloPayload.STREAM_CODEC.encode(helloC2s, new C2SClientHelloPayload(1, "1.0.0"));
		C2SClientHelloPayload decodedHello = C2SClientHelloPayload.STREAM_CODEC.decode(helloC2s);
		assertEquals(1, decodedHello.protocolVersion());
		assertEquals("1.0.0", decodedHello.clientModVersion());

		RegistryFriendlyByteBuf helloS2c = newBuf();
		S2CServerHelloPayload.STREAM_CODEC.encode(helloS2c, new S2CServerHelloPayload(
			1, "1.0.0", List.of(new S2CServerHelloPayload.ShortcutInfo(Ogui.id("menu"), Component.literal("Main Menu"), Component.literal("Open")))
		));
		S2CServerHelloPayload decodedServerHello = S2CServerHelloPayload.STREAM_CODEC.decode(helloS2c);
		assertEquals(1, decodedServerHello.shortcuts().size());
		assertEquals(Ogui.id("menu"), decodedServerHello.shortcuts().getFirst().id());

		// C2SWidgetInteractPayload
		RegistryFriendlyByteBuf interactBuf = newBuf();
		C2SWidgetInteractPayload interactIn = new C2SWidgetInteractPayload(sessionId, 4, "qty", OguiClickAction.CLIENT_INPUT, "64", 64);
		C2SWidgetInteractPayload.STREAM_CODEC.encode(interactBuf, interactIn);
		C2SWidgetInteractPayload interactOut = C2SWidgetInteractPayload.STREAM_CODEC.decode(interactBuf);
		assertEquals(OguiClickAction.CLIENT_INPUT, interactOut.clickAction());
		assertEquals(64, interactOut.numericValue());

		// Close, Feedback, Shortcut payloads
		RegistryFriendlyByteBuf closeS2c = newBuf();
		S2CCloseScreenPayload.STREAM_CODEC.encode(closeS2c, new S2CCloseScreenPayload(sessionId));
		assertEquals(sessionId, S2CCloseScreenPayload.STREAM_CODEC.decode(closeS2c).sessionId());

		RegistryFriendlyByteBuf closeC2s = newBuf();
		C2SCloseNotifyPayload.STREAM_CODEC.encode(closeC2s, new C2SCloseNotifyPayload(sessionId));
		assertEquals(sessionId, C2SCloseNotifyPayload.STREAM_CODEC.decode(closeC2s).sessionId());

		RegistryFriendlyByteBuf fbBuf = newBuf();
		S2CFeedbackPayload.STREAM_CODEC.encode(fbBuf, new S2CFeedbackPayload(sessionId, true, Component.literal("Purchased!"), S2CFeedbackPayload.FeedbackSound.SUCCESS));
		S2CFeedbackPayload fbOut = S2CFeedbackPayload.STREAM_CODEC.decode(fbBuf);
		assertTrue(fbOut.success());
		assertEquals(S2CFeedbackPayload.FeedbackSound.SUCCESS, fbOut.sound());

		RegistryFriendlyByteBuf shortcutBuf = newBuf();
		C2SShortcutTriggerPayload.STREAM_CODEC.encode(shortcutBuf, new C2SShortcutTriggerPayload(Ogui.id("market")));
		assertEquals(Ogui.id("market"), C2SShortcutTriggerPayload.STREAM_CODEC.decode(shortcutBuf).shortcutId());

		// Media ButtonDto + StructurePreviewDto + Asset payloads
		String fakePngHash = "0123456789abcdef".repeat(4);
		String fakeShellHash = "fedcba9876543210".repeat(4);
		OguiAssetRef pngRef = new OguiAssetRef(fakePngHash, OguiAssetKind.PNG, 4096);
		OguiAssetRef shellRef = new OguiAssetRef(fakeShellHash, OguiAssetKind.STRUCTURE_SHELL, 2048);

		OguiWidgetDto mediaBtn = new OguiWidgetDto.ButtonDto(
			"island_card", OguiIcon.item(Items.GRASS_BLOCK), OguiText.nonItalic("Classic"),
			List.of(), null, true, null, pngRef
		);
		RegistryFriendlyByteBuf mediaBtnBuf = newBuf();
		OguiCodecSupport.WIDGET_CODEC.encode(mediaBtnBuf, mediaBtn);
		OguiWidgetDto.ButtonDto mediaBtnOut = assertInstanceOf(OguiWidgetDto.ButtonDto.class, OguiCodecSupport.WIDGET_CODEC.decode(mediaBtnBuf));
		assertEquals(pngRef, mediaBtnOut.previewImage());

		OguiWidgetDto preview3d = new OguiWidgetDto.StructurePreviewDto(
			"struct_view", OguiIcon.item(Items.OAK_SAPLING), OguiText.nonItalic("3D View"),
			List.of(OguiText.nonItalic("Drag to orbit")), shellRef, 55.0F, 22.0F, true, true
		);
		RegistryFriendlyByteBuf previewBuf = newBuf();
		OguiCodecSupport.WIDGET_CODEC.encode(previewBuf, preview3d);
		OguiWidgetDto.StructurePreviewDto previewOut = assertInstanceOf(OguiWidgetDto.StructurePreviewDto.class, OguiCodecSupport.WIDGET_CODEC.decode(previewBuf));
		assertEquals(shellRef, previewOut.structureAsset());
		assertEquals(55.0F, previewOut.initialYaw());
		assertEquals(22.0F, previewOut.initialPitch());
		assertTrue(previewOut.autoRotate());

		RegistryFriendlyByteBuf reqAssetBuf = newBuf();
		C2SRequestAssetPayload.STREAM_CODEC.encode(reqAssetBuf, new C2SRequestAssetPayload(fakePngHash));
		assertEquals(fakePngHash, C2SRequestAssetPayload.STREAM_CODEC.decode(reqAssetBuf).sha256());

		RegistryFriendlyByteBuf chunkBuf = newBuf();
		byte[] chunkData = new byte[]{10, 20, 30, 40};
		S2CAssetChunkPayload.STREAM_CODEC.encode(chunkBuf, new S2CAssetChunkPayload(fakePngHash, OguiAssetKind.PNG, 100, 1, 4, chunkData));
		S2CAssetChunkPayload chunkOut = S2CAssetChunkPayload.STREAM_CODEC.decode(chunkBuf);
		assertEquals(fakePngHash, chunkOut.sha256());
		assertEquals(1, chunkOut.chunkIndex());
		assertEquals(4, chunkOut.totalChunks());
		assertEquals(4, chunkOut.data().length);

		RegistryFriendlyByteBuf unavailBuf = newBuf();
		S2CAssetUnavailablePayload.STREAM_CODEC.encode(unavailBuf, new S2CAssetUnavailablePayload(fakeShellHash));
		assertEquals(fakeShellHash, S2CAssetUnavailablePayload.STREAM_CODEC.decode(unavailBuf).sha256());
	}

	@Test
	void rejectsInvalidEnumOrdinalsAndOversizedLists() {
		RegistryFriendlyByteBuf badEnum = newBuf();
		badEnum.writeVarInt(99);
		assertThrows(DecoderException.class, () -> OguiCodecSupport.readEnum(badEnum, LayoutArchetype.class));

		RegistryFriendlyByteBuf badList = newBuf();
		badList.writeVarInt(9999);
		assertThrows(DecoderException.class, () -> OguiCodecSupport.readList(badList, OguiCodecSupport.WIDGET_CODEC, 16));

		RegistryFriendlyByteBuf badHash = newBuf();
		badHash.writeUtf("not-a-sha256");
		assertThrows(DecoderException.class, () -> C2SRequestAssetPayload.STREAM_CODEC.decode(badHash));
	}
}
