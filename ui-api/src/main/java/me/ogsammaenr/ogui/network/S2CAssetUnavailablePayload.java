package me.ogsammaenr.ogui.network;

import io.netty.handler.codec.DecoderException;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-to-client notification sent when a requested asset hash is unknown or unauthorized for the session.
 */
public record S2CAssetUnavailablePayload(
	String sha256
) implements CustomPacketPayload {
	public static final Type<S2CAssetUnavailablePayload> TYPE = new Type<>(Ogui.id("asset_unavailable"));

	public static final StreamCodec<RegistryFriendlyByteBuf, S2CAssetUnavailablePayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CAssetUnavailablePayload decode(RegistryFriendlyByteBuf buf) {
			String raw = buf.readUtf(OguiAssetRef.SHA256_HEX_LENGTH);
			try {
				return new S2CAssetUnavailablePayload(OguiAssetRef.normalizeAndValidateSha256(raw));
			} catch (IllegalArgumentException e) {
				throw new DecoderException("Invalid SHA-256 in S2CAssetUnavailablePayload: " + e.getMessage());
			}
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CAssetUnavailablePayload payload) {
			buf.writeUtf(payload.sha256(), OguiAssetRef.SHA256_HEX_LENGTH);
		}
	};

	public S2CAssetUnavailablePayload {
		sha256 = OguiAssetRef.normalizeAndValidateSha256(sha256);
	}

	@Override
	public Type<S2CAssetUnavailablePayload> type() {
		return TYPE;
	}
}
