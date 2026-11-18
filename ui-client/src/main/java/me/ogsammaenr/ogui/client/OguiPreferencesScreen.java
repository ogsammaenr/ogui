package me.ogsammaenr.ogui.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Local client preferences modal allowing players to customize Ogui theme, UI scale, and UI sound cues.
 */
public final class OguiPreferencesScreen extends Screen {
	private final Screen parent;
	private final OguiClientPreferences preferences;

	private Button themeButton;
	private Button scaleButton;
	private Button soundButton;
	private Button volumeButton;

	public OguiPreferencesScreen(Screen parent, OguiClientPreferences preferences) {
		super(Component.translatable("ogui.screen.preferences"));
		this.parent = parent;
		this.preferences = preferences;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2 - 52;
		int btnW = 210;
		int btnH = 20;

		this.themeButton = addRenderableWidget(Button.builder(themeLabel(), btn -> {
			preferences.setThemePreset(preferences.themePreset().next());
			btn.setMessage(themeLabel());
		}).bounds(cx - btnW / 2, cy, btnW, btnH).build());

		this.scaleButton = addRenderableWidget(Button.builder(scaleLabel(), btn -> {
			preferences.setUiScale(preferences.uiScale().next());
			btn.setMessage(scaleLabel());
		}).bounds(cx - btnW / 2, cy + 26, btnW, btnH).build());

		this.soundButton = addRenderableWidget(Button.builder(soundLabel(), btn -> {
			preferences.setUiSoundsEnabled(!preferences.uiSoundsEnabled());
			btn.setMessage(soundLabel());
		}).bounds(cx - btnW / 2, cy + 52, btnW, btnH).build());

		this.volumeButton = addRenderableWidget(Button.builder(volumeLabel(), btn -> {
			float next = preferences.soundVolume() >= 0.99f ? 0.2f : Math.min(1.0f, preferences.soundVolume() + 0.2f);
			preferences.setSoundVolume(Math.round(next * 10f) / 10f);
			btn.setMessage(volumeLabel());
		}).bounds(cx - btnW / 2, cy + 78, btnW, btnH).build());

		addRenderableWidget(Button.builder(Component.translatable("ogui.pref.done"), btn -> onClose())
			.bounds(cx - btnW / 2, cy + 112, btnW, btnH)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		var preset = preferences.themePreset();
		int boxW = 248;
		int boxH = 172;
		int boxX = (this.width - boxW) / 2;
		int boxY = (this.height - boxH) / 2 - 4;
		OguiMinecraftWidgets.drawWindowFrame(extractor, boxX, boxY, boxW, boxH, preset, 0xFF38BDF8);
		OguiMinecraftWidgets.drawGrooveDivider(extractor, boxX + 8, boxX + boxW - 8, boxY + 23, preset);
		int titleX = boxX + (boxW - this.font.width(this.title)) / 2;
		extractor.text(this.font, this.title, titleX, boxY + 10, preset.primaryText(), !preset.lightSurface());
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		preferences.saveToConfigDir();
		if (this.minecraft != null) {
			if (parent instanceof OguiScreen oguiParent) {
				oguiParent.onReturnedFromChildModal();
			}
			this.minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component themeLabel() {
		return Component.translatable("ogui.pref.theme").append(": " + preferences.themePreset().displayName());
	}

	private Component scaleLabel() {
		return Component.translatable("ogui.pref.scale").append(": " + preferences.uiScale().displayName());
	}

	private Component soundLabel() {
		return Component.translatable("ogui.pref.sounds").append(": " + (preferences.uiSoundsEnabled() ? "ON" : "OFF"));
	}

	private Component volumeLabel() {
		return Component.translatable("ogui.pref.volume").append(": " + Math.round(preferences.soundVolume() * 100) + "%");
	}
}
