package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Central server-side registry and dispatcher for hybrid Ogui screens and quick-menu shortcuts.
 */
public interface OguiService {
	void registerScreen(Identifier screenId, OguiScreenProvider provider);

	Optional<OguiScreenProvider> findScreen(Identifier screenId);

	void registerShortcut(OguiShortcut shortcut);

	Collection<OguiShortcut> shortcuts();

	OguiAssetRef registerPngAsset(byte[] pngBytes);

	OguiAssetRef registerStructureAsset(OguiStructureShell shell);

	Optional<byte[]> findAssetBytes(OguiAssetRef ref);

	default boolean openScreen(ServerPlayer player, Identifier screenId) {
		return openScreen(player, OguiOpenRequest.of(screenId));
	}

	default boolean openScreen(ServerPlayer player, Identifier screenId, @Nullable String contextKey) {
		return openScreen(player, OguiOpenRequest.of(screenId, contextKey));
	}

	boolean openScreen(ServerPlayer player, OguiOpenRequest request);

	Optional<OguiOpenRequest> activeRequest(ServerPlayer player);

	void refreshPlayerScreen(ServerPlayer player, Identifier screenId);

	int refreshMatchingScreens(Predicate<OguiOpenRequest> filter);

	int closeMatchingScreens(BiPredicate<UUID, OguiOpenRequest> filter, boolean successFeedback, @Nullable Component reason);

	void closePlayerScreen(ServerPlayer player);

	default void sendFeedback(ServerPlayer player, boolean success, String message) {
		sendFeedback(player, success, OguiText.nonItalic(message));
	}

	void sendFeedback(ServerPlayer player, boolean success, Component message);

	boolean isClientRendererAvailable(ServerPlayer player);
}
