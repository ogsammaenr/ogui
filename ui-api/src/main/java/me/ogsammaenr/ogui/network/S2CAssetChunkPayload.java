package me.ogsammaenr.ogui.network;

import io.netty.handler.codec.DecoderException;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.runtime.OguiAssetRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

/**
 * Server-to-client chunked transfer packet delivering binary PNG or structure-shell asset fragments.
 */
public record S2CAssetChunkPayload(
	String sha256,
	OguiAssetKind kind,
	int totalBytes,
	int chunkIndex,
	int totalChunks,
	byte[] data
) implements CustomPacketPayload {
	public static final int MAX_CHUNK_BYTES = OguiAssetRegistry.CHUNK_SIZE_BYTES;
	public static final int MAX_CHUNKS = (OguiAssetRef.MAX_ASSET_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
	public static final Type<S2CAssetChunkPayload> TYPE = new Type<>(Ogui.id("asset_chunk"));

	public static final StreamCodec<RegistryFriendlyByteBuf, S2CAssetChunkPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CAssetChunkPayload decode(RegistryFriendlyByteBuf buf) {
			String sha256;
			try {
				sha256 = OguiAssetRef.normalizeAndValidateSha256(buf.readUtf(OguiAssetRef.SHA256_HEX_LENGTH));
			} catch (IllegalArgumentException e) {
				throw new DecoderException("Invalid SHA-256 in S2CAssetChunkPayload: " + e.getMessage());
			}
			OguiAssetKind kind = OguiCodecSupport.readEnum(buf, OguiAssetKind.class);
			int totalBytes = buf.readVarInt();
			int chunkIndex = buf.readVarInt();
			int totalChunks = buf.readVarInt();
			int len = buf.readVarInt();
			if (totalBytes <= 0 || totalBytes > OguiAssetRef.MAX_ASSET_BYTES) {
				throw new DecoderException("Asset totalBytes out of bounds: " + totalBytes);
			}
			if (totalChunks <= 0 || totalChunks > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks) {
				throw new DecoderException("Chunk index/count out of bounds: " + chunkIndex + "/" + totalChunks);
			}
			if (len < 0 || len > MAX_CHUNK_BYTES) {
				throw new DecoderException("Chunk byte length out of bounds: " + len);
			}
			byte[] data = new byte[len];
			buf.readBytes(data);
			return new S2CAssetChunkPayload(sha256, kind, totalBytes, chunkIndex, totalChunks, data);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CAssetChunkPayload payload) {
			buf.writeUtf(payload.sha256(), OguiAssetRef.SHA256_HEX_LENGTH);
			buf.writeEnum(payload.kind());
			buf.writeVarInt(payload.totalBytes());
			buf.writeVarInt(payload.chunkIndex());
			buf.writeVarInt(payload.totalChunks());
			buf.writeVarInt(payload.data().length);
			buf.writeBytes(payload.data());
		}
	};

	public S2CAssetChunkPayload {
		sha256 = OguiAssetRef.normalizeAndValidateSha256(sha256);
		Objects.requireNonNull(kind, "kind");
		if (totalBytes <= 0 || totalBytes > OguiAssetRef.MAX_ASSET_BYTES) {
			throw new IllegalArgumentException("Asset totalBytes out of bounds: " + totalBytes);
		}
		if (totalChunks <= 0 || totalChunks > MAX_CHUNKS || chunkIndex < 0 || chunkIndex >= totalChunks) {
			throw new IllegalArgumentException("Chunk index/count out of bounds: " + chunkIndex + "/" + totalChunks);
		}
		Objects.requireNonNull(data, "data");
		if (data.length > MAX_CHUNK_BYTES) {
			throw new IllegalArgumentException("Chunk byte length exceeds limit: " + data.length);
		}
		data = data.clone();
	}

	@Override
	public byte[] data() {
		return data.clone();
	}

	@Override
	public Type<S2CAssetChunkPayload> type() {
		return TYPE;
	}
}
