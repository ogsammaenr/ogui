package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sent by {@code ogui-client} when the player invokes a server-announced quick-menu shortcut.
 */
public record C2SShortcutTriggerPayload(Identifier shortcutId) implements CustomPacketPayload {
	public static final Type<C2SShortcutTriggerPayload> TYPE = new Type<>(Ogui.id("shortcut_trigger"));
	public static final StreamCodec<RegistryFriendlyByteBuf, C2SShortcutTriggerPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SShortcutTriggerPayload decode(RegistryFriendlyByteBuf buf) {
			return new C2SShortcutTriggerPayload(Identifier.STREAM_CODEC.decode(buf));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, C2SShortcutTriggerPayload payload) {
			Identifier.STREAM_CODEC.encode(buf, payload.shortcutId());
		}
	};

	public C2SShortcutTriggerPayload {
		Objects.requireNonNull(shortcutId, "shortcutId");
	}

	@Override
	public Type<C2SShortcutTriggerPayload> type() {
		return TYPE;
	}
}
