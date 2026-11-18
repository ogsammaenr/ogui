package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiClickAction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

/**
 * Zero-trust client interaction packet forwarding only the target widget ID, click action, and raw input values.
 */
public record C2SWidgetInteractPayload(
	UUID sessionId,
	int revision,
	String widgetId,
	OguiClickAction clickAction,
	String inputText,
	int numericValue
) implements CustomPacketPayload {
	public static final Type<C2SWidgetInteractPayload> TYPE = new Type<>(Ogui.id("widget_interact"));
	public static final StreamCodec<RegistryFriendlyByteBuf, C2SWidgetInteractPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SWidgetInteractPayload decode(RegistryFriendlyByteBuf buf) {
			UUID sessionId = UUIDUtil.STREAM_CODEC.decode(buf);
			int revision = buf.readVarInt();
			String widgetId = buf.readUtf(OguiCodecSupport.MAX_SHORT_TEXT);
			OguiClickAction action = OguiCodecSupport.readEnum(buf, OguiClickAction.class);
			String inputText = buf.readUtf(OguiCodecSupport.MAX_INPUT_TEXT);
			int numericValue = buf.readVarInt();
			return new C2SWidgetInteractPayload(sessionId, revision, widgetId, action, inputText, numericValue);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, C2SWidgetInteractPayload payload) {
			UUIDUtil.STREAM_CODEC.encode(buf, payload.sessionId());
			buf.writeVarInt(payload.revision());
			buf.writeUtf(payload.widgetId(), OguiCodecSupport.MAX_SHORT_TEXT);
			buf.writeEnum(payload.clickAction());
			buf.writeUtf(payload.inputText(), OguiCodecSupport.MAX_INPUT_TEXT);
			buf.writeVarInt(payload.numericValue());
		}
	};

	public C2SWidgetInteractPayload {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(widgetId, "widgetId");
		clickAction = clickAction == null ? OguiClickAction.LEFT : clickAction;
		inputText = inputText == null ? "" : inputText;
	}

	@Override
	public Type<C2SWidgetInteractPayload> type() {
		return TYPE;
	}
}
