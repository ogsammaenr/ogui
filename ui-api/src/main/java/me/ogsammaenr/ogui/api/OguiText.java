package me.ogsammaenr.ogui.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;

/**
 * Text formatting helpers producing non-italic {@link Component} instances suitable for menus and tooltips.
 */
public final class OguiText {
	private OguiText() {
	}

	public static MutableComponent nonItalic(String text) {
		Objects.requireNonNull(text, "text");
		return Component.literal(text).withStyle(style -> style.withItalic(false));
	}

	public static MutableComponent nonItalic(String text, ChatFormatting formatting) {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(formatting, "formatting");
		return Component.literal(text)
			.withStyle(formatting)
			.withStyle(style -> style.withItalic(false));
	}

	public static MutableComponent nonItalic(Component component) {
		Objects.requireNonNull(component, "component");
		return component.copy().withStyle(style -> style.withItalic(false));
	}
}
