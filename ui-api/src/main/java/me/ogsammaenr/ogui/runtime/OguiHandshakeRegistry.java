package me.ogsammaenr.ogui.runtime;

import me.ogsammaenr.ogui.api.Ogui;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player protocol handshake state so incompatible or vanilla clients seamlessly fall back to {@code ChestMenu}.
 */
public final class OguiHandshakeRegistry {
	public record ClientCapability(
		int protocolVersion,
		String clientModVersion,
		boolean compatible,
		Instant receivedAt
	) {
	}

	private final Map<UUID, ClientCapability> capabilities = new ConcurrentHashMap<>();

	public ClientCapability recordClientHello(UUID playerId, int protocolVersion, String clientModVersion) {
		boolean compatible = protocolVersion == Ogui.PROTOCOL_VERSION;
		ClientCapability capability = new ClientCapability(
			protocolVersion,
			clientModVersion == null ? "" : clientModVersion,
			compatible,
			Instant.now()
		);
		capabilities.put(playerId, capability);
		return capability;
	}

	public Optional<ClientCapability> find(UUID playerId) {
		return Optional.ofNullable(capabilities.get(playerId));
	}

	public boolean isCompatible(UUID playerId) {
		ClientCapability capability = capabilities.get(playerId);
		return capability != null && capability.compatible();
	}

	public void evict(UUID playerId) {
		capabilities.remove(playerId);
	}

	public void clear() {
		capabilities.clear();
	}
}
