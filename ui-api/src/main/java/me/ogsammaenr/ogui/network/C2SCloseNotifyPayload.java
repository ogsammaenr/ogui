package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Sent by {@code ogui-client} when the player closes the client screen locally so the server releases session RAM.
 */
public record C2SCloseNotifyPayload(UUID sessionId) implements CustomPacketPayload {
	public static final Type<C2SCloseNotifyPayload> TYPE = new Type<>(Ogui.id("close_notify"));
	public static final StreamCodec<RegistryFriendlyByteBuf, C2SCloseNotifyPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SCloseNotifyPayload decode(RegistryFriendlyByteBuf buf) {
			return new C2SCloseNotifyPayload(UUIDUtil.STREAM_CODEC.decode(buf));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, C2SCloseNotifyPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
		}
	};

	public C2SCloseNotifyPayload {
		Objects.requireNonNull(sessionId, "sessionId");
	}

	@Override
	public Type<C2SCloseNotifyPayload> type() {
		return TYPE;
	}
}
