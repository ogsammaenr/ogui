package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Handshake acknowledgment sent by {@code ogui-api} server containing supported protocol version and registered shortcuts.
 */
public record S2CServerHelloPayload(
	int protocolVersion,
	String serverModVersion,
	List<ShortcutInfo> shortcuts
) implements CustomPacketPayload {
	public record ShortcutInfo(Identifier id, Component label, Component description) {
		public ShortcutInfo {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(label, "label");
			description = description == null ? Component.empty() : description;
		}
	}

	public static final Type<S2CServerHelloPayload> TYPE = new Type<>(Ogui.id("server_hello"));
	private static final StreamCodec<RegistryFriendlyByteBuf, ShortcutInfo> SHORTCUT_CODEC = new StreamCodec<>() {
		@Override
		public ShortcutInfo decode(RegistryFriendlyByteBuf buf) {
			Identifier id = Identifier.STREAM_CODEC.decode(buf);
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component desc = ComponentSerialization.STREAM_CODEC.decode(buf);
			return new ShortcutInfo(id, label, desc);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, ShortcutInfo info) {
			Identifier.STREAM_CODEC.encode(buf, info.id());
			ComponentSerialization.STREAM_CODEC.encode(buf, info.label());
			ComponentSerialization.STREAM_CODEC.encode(buf, info.description());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, S2CServerHelloPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public S2CServerHelloPayload decode(RegistryFriendlyByteBuf buf) {
			int protocolVersion = buf.readVarInt();
			String version = buf.readUtf(64);
			List<ShortcutInfo> shortcuts = OguiCodecSupport.readList(buf, SHORTCUT_CODEC, 32);
			return new S2CServerHelloPayload(protocolVersion, version, shortcuts);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, S2CServerHelloPayload payload) {
			buf.writeVarInt(payload.protocolVersion());
			buf.writeUtf(payload.serverModVersion(), 64);
			OguiCodecSupport.writeList(buf, payload.shortcuts(), SHORTCUT_CODEC);
		}
	};

	public S2CServerHelloPayload {
		serverModVersion = serverModVersion == null ? "" : serverModVersion;
		shortcuts = shortcuts == null ? List.of() : List.copyOf(shortcuts);
	}

	@Override
	public Type<S2CServerHelloPayload> type() {
		return TYPE;
	}
}
