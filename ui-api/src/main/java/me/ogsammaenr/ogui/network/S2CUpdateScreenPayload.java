package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Live-updates an already open {@code ogui-client} screen without forcing cursor/focus reset when same screenId.
 */
public record S2CUpdateScreenPayload(
	UUID sessionId,
	int revision,
	ScreenSnapshotDto snapshot
) implements CustomPacketPayload {
	public static final Type<S2CUpdateScreenPayload> TYPE = new Type<>(Ogui.id("update_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2CUpdateScreenPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CUpdateScreenPayload decode(RegistryFriendlyByteBuf buf) {
			UUID sessionId = UUIDUtil.STREAM_CODEC.decode(buf);
			int revision = buf.readVarInt();
			ScreenSnapshotDto snapshot = OguiCodecSupport.SNAPSHOT_CODEC.decode(buf);
			return new S2CUpdateScreenPayload(sessionId, revision, snapshot);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CUpdateScreenPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
			buf.writeVarInt(payload.revision());
			OguiCodecSupport.SNAPSHOT_CODEC.encode(buf, payload.snapshot());
		}
	};

	public S2CUpdateScreenPayload {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(snapshot, "snapshot");
	}

	@Override
	public Type<S2CUpdateScreenPayload> type() {
		return TYPE;
	}
}
