package me.ogsammaenr.ogui.api;

/**
 * Normalized user interaction types forwarded from both the custom client screen and the vanilla ChestMenu fallback.
 */
public enum OguiClickAction {
	LEFT,
	RIGHT,
	SHIFT_LEFT,
	SHIFT_RIGHT,
	CLIENT_INPUT;

	public boolean isLeft() {
		return this == LEFT || this == SHIFT_LEFT;
	}

	public boolean isRight() {
		return this == RIGHT || this == SHIFT_RIGHT;
	}

	public boolean isShift() {
		return this == SHIFT_LEFT || this == SHIFT_RIGHT;
	}

	public boolean isInput() {
		return this == CLIENT_INPUT;
	}
}
