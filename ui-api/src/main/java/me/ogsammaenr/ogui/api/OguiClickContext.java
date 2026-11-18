package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Server-side interaction context passed to widget callbacks upon validated client or ChestMenu interactions.
 */
public interface OguiClickContext {
	ServerPlayer actor();

	@Nullable String contextKey();

	OguiClickAction clickAction();

	int currentPage();

	Map<String, String> state();

	default String stateOrDefault(String key, String defaultValue) {
		return state().getOrDefault(key, defaultValue);
	}

	default int stateInt(String key, int defaultValue) {
		String raw = state().get(key);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	/** Re-builds and re-renders the current screen preserving page and state. */
	void refresh();

	/** Updates a single state key, resets page to 0, and re-renders the current screen. */
	void refreshWithState(String key, String value);

	/** Updates a single state key while optionally preserving the current page index. */
	void refreshWithState(String key, String value, boolean preservePage);

	/** Applies multiple state mutations at once and re-renders the current screen. */
	void refreshWithStates(Map<String, String> updates);

	/** Removes a state key and re-renders the current screen. */
	void refreshWithoutState(String key);

	/** Navigates to {@code screenId} with optional context key, pushing onto the back-stack. */
	void openScreen(Identifier screenId, @Nullable String targetContextKey);

	/** Navigates using an explicit {@link OguiOpenRequest}. */
	void openScreen(OguiOpenRequest request);

	/** Returns to the previous screen in the back-stack, or closes the UI when at root. */
	void goBack();

	/** Closes the active UI session for {@link #actor()}. */
	void close();

	/** Delivers feedback to the open client banner (if active) or chat fallback. */
	void sendFeedback(boolean success, String message);

	/** Delivers component feedback to the open client banner (if active) or chat fallback. */
	void sendFeedback(boolean success, Component message);

	/** @return {@code true} if the viewer is currently using the {@code ogui-client} custom screen. */
	boolean isClientModActive();
}
