package me.ogsammaenr.ogui.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiShortcut;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SCloseNotifyPayload;
import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.C2SWidgetInteractPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import me.ogsammaenr.ogui.runtime.OguiSessionManager;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies protocol handshake, incompatible-client automatic ChestMenu fallback,
 * zero-trust session/revision validation, and quick-menu shortcut execution.
 */
public final class OguiProtocolGameTests {
	@GameTest
	public void protocolHandshakeMismatchFallbackAndZeroTrustSessionWork(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Identifier screenId = Identifier.fromNamespaceAndPath("ogui_test", "proto_" + suffix);
		Identifier shortcutId = Identifier.fromNamespaceAndPath("ogui_test", "shortcut_" + suffix);

		AtomicInteger clicks = new AtomicInteger();
		AtomicBoolean shortcutTriggered = new AtomicBoolean();

		Ogui.service().registerScreen(screenId, ctx -> OguiScreenModel.fixedGrid("Proto Test", 3)
			.slot(13, OguiWidget.button("btn_action", OguiIcon.item("minecraft:diamond"), OguiText.nonItalic("Action", ChatFormatting.AQUA))
				.onClick(click -> {
					int next = clicks.incrementAndGet();
					click.sendFeedback(true, "Count: " + next);
					click.refreshWithState("count", Integer.toString(next));
				})
				.build())
			.build());
		Ogui.service().registerShortcut(new OguiShortcut(
			shortcutId,
			OguiText.nonItalic("Quick Test"),
			OguiText.nonItalic("Opens proto screen"),
			p -> {
				shortcutTriggered.set(true);
				Ogui.service().openScreen(p, screenId);
			}
		));

		AtomicReference<EmbeddedChannel> vanillaChRef = new AtomicReference<>();
		AtomicReference<EmbeddedChannel> mismatchChRef = new AtomicReference<>();
		AtomicReference<EmbeddedChannel> modernChRef = new AtomicReference<>();

		ServerPlayer vanilla = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiVanilla", GameType.SURVIVAL, vanillaChRef);
		ServerPlayer mismatch = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiOld", GameType.SURVIVAL, mismatchChRef);
		ServerPlayer modern = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiClient", GameType.SURVIVAL, modernChRef);

		try {
			// 1. Vanilla client without ogui-client falls back directly to ChestMenu
			helper.assertTrue(!Ogui.service().isClientRendererAvailable(vanilla), "Expected vanilla client unavailable");
			helper.assertTrue(Ogui.service().openScreen(vanilla, screenId), "Expected openScreen true for vanilla");
			helper.assertTrue(vanilla.containerMenu instanceof OguiChestMenu, "Expected OguiChestMenu fallback for vanilla");

			// 2. Client with channels registered but incompatible PROTOCOL_VERSION falls back cleanly without disconnect
			OguiGameTestSupport.registerOguiClientChannels(mismatch);
			OguiGameTestSupport.drainOutboundPackets(mismatchChRef.get());
			mismatch.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SClientHelloPayload(999, "9.9.9")));
			List<Packet<?>> mismatchHelloPackets = OguiGameTestSupport.drainOutboundPackets(mismatchChRef.get());
			S2CServerHelloPayload mismatchAck = mismatchHelloPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.filter(S2CServerHelloPayload.class::isInstance)
				.map(S2CServerHelloPayload.class::cast)
				.findFirst()
				.orElse(null);
			helper.assertTrue(mismatchAck != null && mismatchAck.shortcuts().isEmpty(),
				"Expected S2CServerHelloPayload with empty shortcuts on protocol mismatch");
			helper.assertTrue(!mismatch.hasDisconnected(), "Mismatch player must NOT be disconnected");
			helper.assertTrue(!Ogui.service().isClientRendererAvailable(mismatch), "Expected mismatch client ineligible for S2C screen");
			helper.assertTrue(Ogui.service().openScreen(mismatch, screenId), "Expected openScreen fallback for mismatch client");
			helper.assertTrue(mismatch.containerMenu instanceof OguiChestMenu, "Expected automatic ChestMenu fallback on protocol mismatch");

			// 3. Compatible client completes handshake, receives S2C_OpenScreenPayload, and interacts via C2S
			OguiGameTestSupport.registerOguiClientChannels(modern);
			OguiGameTestSupport.drainOutboundPackets(modernChRef.get());
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SClientHelloPayload(Ogui.PROTOCOL_VERSION, "1.0.0")));
			List<Packet<?>> modernHelloPackets = OguiGameTestSupport.drainOutboundPackets(modernChRef.get());
			S2CServerHelloPayload modernAck = modernHelloPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.filter(S2CServerHelloPayload.class::isInstance)
				.map(S2CServerHelloPayload.class::cast)
				.findFirst()
				.orElse(null);
			helper.assertTrue(modernAck != null && modernAck.shortcuts().stream().anyMatch(s -> s.id().equals(shortcutId)),
				"Expected compatible client to receive registered shortcuts");
			helper.assertTrue(Ogui.service().isClientRendererAvailable(modern), "Expected compatible client renderer available");

			// Trigger via shortcut C2S packet
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SShortcutTriggerPayload(shortcutId)));
			helper.assertTrue(shortcutTriggered.get(), "Expected shortcut callback to execute");
			helper.assertTrue(!(modern.containerMenu instanceof OguiChestMenu), "Expected client-UI mode not to open ChestMenu");

			List<Packet<?>> openPackets = OguiGameTestSupport.drainOutboundPackets(modernChRef.get());
			S2COpenScreenPayload openPayload = openPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.filter(S2COpenScreenPayload.class::isInstance)
				.map(S2COpenScreenPayload.class::cast)
				.findFirst()
				.orElse(null);
			helper.assertTrue(openPayload != null && openPayload.revision() == 1, "Expected S2COpenScreenPayload(rev=1)");

			UUID sid = openPayload.sessionId();

			// Zero-trust checks: wrong sessionId, wrong revision, unknown widgetId must all be rejected
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(
				new C2SWidgetInteractPayload(UUID.randomUUID(), 1, "btn_action", OguiClickAction.LEFT, "", 0)));
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(
				new C2SWidgetInteractPayload(sid, 99, "btn_action", OguiClickAction.LEFT, "", 0)));
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(
				new C2SWidgetInteractPayload(sid, 1, "forged_widget", OguiClickAction.LEFT, "", 0)));
			helper.assertTrue(clicks.get() == 0, "Invalid/forged C2S interaction packets must be rejected");

			// Valid interaction triggers callback, S2CFeedbackPayload, and S2CUpdateScreenPayload(rev=2)
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(
				new C2SWidgetInteractPayload(sid, 1, "btn_action", OguiClickAction.LEFT, "", 0)));
			helper.assertTrue(clicks.get() == 1, "Valid C2S interaction must invoke server callback");

			List<Packet<?>> afterClickPackets = OguiGameTestSupport.drainOutboundPackets(modernChRef.get());
			boolean hasFeedback = afterClickPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.anyMatch(p -> p instanceof S2CFeedbackPayload fb && fb.success() && fb.message().getString().contains("Count: 1"));
			boolean hasUpdate = afterClickPackets.stream()
				.filter(ClientboundCustomPayloadPacket.class::isInstance)
				.map(p -> ((ClientboundCustomPayloadPacket) p).payload())
				.anyMatch(p -> p instanceof S2CUpdateScreenPayload up && up.sessionId().equals(sid) && up.revision() == 2);
			helper.assertTrue(hasFeedback && hasUpdate, "Expected S2CFeedbackPayload and S2CUpdateScreenPayload(rev=2)");

			// Client close notify cleans up RAM session
			modern.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new C2SCloseNotifyPayload(sid)));
			helper.assertTrue(OguiSessionManager.instance().activeSession(modern.getUUID()) == null,
				"Expected C2SCloseNotifyPayload to release active RAM session");

			helper.succeed();
		} finally {
			OguiGameTestSupport.cleanupPlayers(server, vanilla, mismatch, modern);
		}
	}
}
