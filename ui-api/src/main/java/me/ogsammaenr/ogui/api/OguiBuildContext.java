package me.ogsammaenr.ogui.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Contextual parameters supplied to an {@link OguiScreenProvider} when constructing or refreshing a screen.
 */
public record OguiBuildContext(
	ServerPlayer viewer,
	@Nullable String contextKey,
	int requestedPage,
	Map<String, String> state,
	@Nullable Identifier backScreenId,
	@Nullable String backContextKey
) {
	public OguiBuildContext {
		Objects.requireNonNull(viewer, "viewer");
		requestedPage = Math.max(0, requestedPage);
		state = state == null ? Map.of() : Map.copyOf(state);
	}

	public String stateOrDefault(String key, String defaultValue) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(defaultValue, "defaultValue");
		return state.getOrDefault(key, defaultValue);
	}

	public int stateInt(String key, int defaultValue) {
		Objects.requireNonNull(key, "key");
		String raw = state.get(key);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}
}
