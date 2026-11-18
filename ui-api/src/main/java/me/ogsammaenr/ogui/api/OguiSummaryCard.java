package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Informational summary card rendered in the header/sidebar of client screens and summary slots of vanilla menus.
 */
public record OguiSummaryCard(
	OguiIcon icon,
	Component title,
	List<Component> lines,
	int accentColor
) {
	public OguiSummaryCard {
		Objects.requireNonNull(icon, "icon");
		Objects.requireNonNull(title, "title");
		lines = lines == null ? List.of() : List.copyOf(lines);
	}

	public static OguiSummaryCard of(OguiIcon icon, Component title, List<Component> lines) {
		return new OguiSummaryCard(icon, title, lines, OguiTheme.DEFAULT_ACCENT_COLOR);
	}
}
