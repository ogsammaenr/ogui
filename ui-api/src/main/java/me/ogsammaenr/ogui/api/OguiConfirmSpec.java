package me.ogsammaenr.ogui.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Optional two-click confirmation gate attached to high-impact buttons or toggles.
 */
public record OguiConfirmSpec(
	String targetKey,
	Component promptLine,
	Duration ttl
) {
	public static final Duration DEFAULT_TTL = Duration.ofSeconds(10);

	public OguiConfirmSpec {
		if (targetKey == null || targetKey.isBlank()) {
			throw new IllegalArgumentException("Confirmation targetKey must not be blank.");
		}
		Objects.requireNonNull(promptLine, "promptLine");
		ttl = (ttl == null || ttl.isNegative() || ttl.isZero()) ? DEFAULT_TTL : ttl;
	}

	public static OguiConfirmSpec of(String targetKey, String promptText) {
		Objects.requireNonNull(promptText, "promptText");
		return new OguiConfirmSpec(
			targetKey,
			OguiText.nonItalic(promptText, ChatFormatting.YELLOW),
			DEFAULT_TTL
		);
	}

	public static OguiConfirmSpec of(String targetKey, Component promptLine) {
		return new OguiConfirmSpec(targetKey, promptLine, DEFAULT_TTL);
	}
}
