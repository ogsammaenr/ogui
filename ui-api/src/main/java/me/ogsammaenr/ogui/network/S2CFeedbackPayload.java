package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Displays an inline toast/banner notification and sound cue inside the active {@code ogui-client} screen.
 */
public record S2CFeedbackPayload(
	UUID sessionId,
	boolean success,
	Component message,
	FeedbackSound sound
) implements CustomPacketPayload {
	public enum FeedbackSound {
		CLICK,
		SUCCESS,
		ERROR,
		NONE
	}

	public static final Type<S2CFeedbackPayload> TYPE = new Type<>(Ogui.id("feedback"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2CFeedbackPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CFeedbackPayload decode(RegistryFriendlyByteBuf buf) {
			UUID sessionId = UUIDUtil.STREAM_CODEC.decode(buf);
			boolean success = buf.readBoolean();
			Component message = ComponentSerialization.STREAM_CODEC.decode(buf);
			FeedbackSound sound = OguiCodecSupport.readEnum(buf, FeedbackSound.class);
			return new S2CFeedbackPayload(sessionId, success, message, sound);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CFeedbackPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
			buf.writeBoolean(payload.success());
			ComponentSerialization.STREAM_CODEC.encode(buf, payload.message());
			buf.writeEnum(payload.sound());
		}
	};

	public S2CFeedbackPayload {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(message, "message");
		sound = sound == null ? FeedbackSound.NONE : sound;
	}

	@Override
	public Type<S2CFeedbackPayload> type() {
		return TYPE;
	}
}
