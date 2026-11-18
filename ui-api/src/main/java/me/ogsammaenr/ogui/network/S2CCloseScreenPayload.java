package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Closes the active {@code ogui-client} screen matching {@code sessionId}.
 */
public record S2CCloseScreenPayload(UUID sessionId) implements CustomPacketPayload {
	public static final Type<S2CCloseScreenPayload> TYPE = new Type<>(Ogui.id("close_screen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2CCloseScreenPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CCloseScreenPayload decode(RegistryFriendlyByteBuf buf) {
			return new S2CCloseScreenPayload(UUIDUtil.STREAM_CODEC.decode(buf));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CCloseScreenPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
		}
	};

	public S2CCloseScreenPayload {
		Objects.requireNonNull(sessionId, "sessionId");
	}

	@Override
	public Type<S2CCloseScreenPayload> type() {
		return TYPE;
	}
}
