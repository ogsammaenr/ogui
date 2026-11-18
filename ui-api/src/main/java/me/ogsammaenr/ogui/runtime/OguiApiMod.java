package me.ogsammaenr.ogui.runtime;

import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SCloseNotifyPayload;
import me.ogsammaenr.ogui.network.C2SRequestAssetPayload;
import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.C2SWidgetInteractPayload;
import me.ogsammaenr.ogui.network.OguiPayloads;
import me.ogsammaenr.ogui.vanilla.FakeSignInputManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Common Fabric entrypoint for {@code ogui-api}, registering network payload codecs,
 * server packet receivers, and lifecycle cleanup hooks.
 */
public final class OguiApiMod implements ModInitializer {
	@Override
	public void onInitialize() {
		OguiPayloads.registerAll();
		OguiSessionManager manager = OguiSessionManager.instance();

		ServerPlayNetworking.registerGlobalReceiver(
			C2SClientHelloPayload.TYPE,
			(payload, context) -> manager.handleClientHello(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			C2SWidgetInteractPayload.TYPE,
			(payload, context) -> manager.handleClientInteract(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			C2SCloseNotifyPayload.TYPE,
			(payload, context) -> manager.handleClientCloseNotify(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			C2SShortcutTriggerPayload.TYPE,
			(payload, context) -> manager.handleShortcutTrigger(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
			C2SRequestAssetPayload.TYPE,
			(payload, context) -> manager.handleAssetRequest(context.player(), payload)
		);

		ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> manager.onPlayerDisconnect(handler.getPlayer().getUUID())
		);

		ServerLifecycleEvents.SERVER_STARTING.register(OguiSessionManager::setCurrentServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			manager.clearRuntimeState();
			OguiSessionManager.setCurrentServer(null);
		});

		ServerTickEvents.END_SERVER_TICK.register(FakeSignInputManager.instance()::tickCleanup);
	}
}
