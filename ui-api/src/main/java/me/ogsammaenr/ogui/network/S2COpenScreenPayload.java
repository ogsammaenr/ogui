package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Instructs {@code ogui-client} to open a new server-authoritative {@link ScreenSnapshotDto}.
 */
public record S2COpenScreenPayload(
	UUID sessionId,
	int revision,
	ScreenSnapshotDto snapshot
) implements CustomPacketPayload {
	public static final Type<S2COpenScreenPayload> TYPE = new Type<>(Ogui.id("open_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2COpenScreenPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2COpenScreenPayload decode(RegistryFriendlyByteBuf buf) {
			UUID sessionId = UUIDUtil.STREAM_CODEC.decode(buf);
			int revision = buf.readVarInt();
			ScreenSnapshotDto snapshot = OguiCodecSupport.SNAPSHOT_CODEC.decode(buf);
			return new S2COpenScreenPayload(sessionId, revision, snapshot);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2COpenScreenPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
			buf.writeVarInt(payload.revision());
			OguiCodecSupport.SNAPSHOT_CODEC.encode(buf, payload.snapshot());
		}
	};

	public S2COpenScreenPayload {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(snapshot, "snapshot");
	}

	@Override
	public Type<S2COpenScreenPayload> type() {
		return TYPE;
	}
}
