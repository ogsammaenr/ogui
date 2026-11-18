package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.Ogui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Handshake packet sent by {@code ogui-client} upon play join announcing its protocol version.
 */
public record C2SClientHelloPayload(
	int protocolVersion,
	String clientModVersion
) implements CustomPacketPayload {
	public static final Type<C2SClientHelloPayload> TYPE = new Type<>(Ogui.id("client_hello"));
	public static final StreamCodec<RegistryFriendlyByteBuf, C2SClientHelloPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public C2SClientHelloPayload decode(RegistryFriendlyByteBuf buf) {
			int protocolVersion = buf.readVarInt();
			String version = buf.readUtf(64);
			return new C2SClientHelloPayload(protocolVersion, version);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, C2SClientHelloPayload payload) {
			buf.writeVarInt(payload.protocolVersion());
			buf.writeUtf(payload.clientModVersion(), 64);
		}
	};

	public C2SClientHelloPayload {
		clientModVersion = clientModVersion == null ? "" : clientModVersion;
	}

	@Override
	public Type<C2SClientHelloPayload> type() {
		return TYPE;
	}
}
