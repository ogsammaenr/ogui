package me.ogsammaenr.ogui.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers all {@code ogui} clientbound and serverbound play payload codecs once per JVM.
 */
public final class OguiPayloads {
	public static final int MAX_SCREEN_PAYLOAD_BYTES = 1_048_576;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

	private OguiPayloads() {
	}

	public static void registerAll() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		var s2c = PayloadTypeRegistry.clientboundPlay();
		s2c.register(S2CServerHelloPayload.TYPE, S2CServerHelloPayload.STREAM_CODEC);
		s2c.registerLarge(S2COpenScreenPayload.TYPE, S2COpenScreenPayload.STREAM_CODEC, MAX_SCREEN_PAYLOAD_BYTES);
		s2c.registerLarge(S2CUpdateScreenPayload.TYPE, S2CUpdateScreenPayload.STREAM_CODEC, MAX_SCREEN_PAYLOAD_BYTES);
		s2c.register(S2CCloseScreenPayload.TYPE, S2CCloseScreenPayload.STREAM_CODEC);
		s2c.register(S2CFeedbackPayload.TYPE, S2CFeedbackPayload.STREAM_CODEC);
		s2c.register(S2CAssetChunkPayload.TYPE, S2CAssetChunkPayload.STREAM_CODEC);
		s2c.register(S2CAssetUnavailablePayload.TYPE, S2CAssetUnavailablePayload.STREAM_CODEC);

		var c2s = PayloadTypeRegistry.serverboundPlay();
		c2s.register(C2SClientHelloPayload.TYPE, C2SClientHelloPayload.STREAM_CODEC);
		c2s.register(C2SWidgetInteractPayload.TYPE, C2SWidgetInteractPayload.STREAM_CODEC);
		c2s.register(C2SCloseNotifyPayload.TYPE, C2SCloseNotifyPayload.STREAM_CODEC);
		c2s.register(C2SShortcutTriggerPayload.TYPE, C2SShortcutTriggerPayload.STREAM_CODEC);
		c2s.register(C2SRequestAssetPayload.TYPE, C2SRequestAssetPayload.STREAM_CODEC);
	}
}
