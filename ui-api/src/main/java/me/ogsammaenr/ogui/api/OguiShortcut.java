package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Server-announced optional quick-menu shortcut bound to {@code ogui-client}'s quick-menu keybinding.
 */
public record OguiShortcut(
	Identifier id,
	Component label,
	Component description,
	Consumer<ServerPlayer> onTrigger
) {
	public OguiShortcut {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(label, "label");
		description = description == null ? Component.empty() : description;
		Objects.requireNonNull(onTrigger, "onTrigger");
	}
}
