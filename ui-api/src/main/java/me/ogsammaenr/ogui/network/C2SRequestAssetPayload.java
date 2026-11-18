package me.ogsammaenr.ogui.network;

import io.netty.handler.codec.DecoderException;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server request asking for the binary bytes of an asset missing from client cache.
 */
public record C2SRequestAssetPayload(
	String sha256
) implements CustomPacketPayload {
	public static final Type<C2SRequestAssetPayload> TYPE = new Type<>(Ogui.id("request_asset"));

	public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestAssetPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SRequestAssetPayload decode(RegistryFriendlyByteBuf buf) {
			String raw = buf.readUtf(OguiAssetRef.SHA256_HEX_LENGTH);
			try {
				return new C2SRequestAssetPayload(OguiAssetRef.normalizeAndValidateSha256(raw));
			} catch (IllegalArgumentException e) {
				throw new DecoderException("Invalid SHA-256 in C2SRequestAssetPayload: " + e.getMessage());
			}
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, C2SRequestAssetPayload payload) {
			buf.writeUtf(payload.sha256(), OguiAssetRef.SHA256_HEX_LENGTH);
		}
	};

	public C2SRequestAssetPayload {
		sha256 = OguiAssetRef.normalizeAndValidateSha256(sha256);
	}

	@Override
	public Type<C2SRequestAssetPayload> type() {
		return TYPE;
	}
}
