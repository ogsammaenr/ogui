package me.ogsammaenr.ogui.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-local visual and audio preferences persisted in {@code config/ogui-client.json}.
 */
public final class OguiClientPreferences {
	private static final Logger LOGGER = LoggerFactory.getLogger(OguiClientPreferences.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum ThemePreset {
		OBSIDIAN("Vanilla Stone", 0xD81B1D24, 0xCC242731, 0xFF282B36, 0xFF5A6070, 0xFFFFFFFF, 0xFFCBD5E1, 0xFFE6EAF2, 0xFFE6EAF2, false),
		VANILLA_CLASSIC("Minecraft Classic", 0xFFC6C6C6, 0xFFC6C6C6, 0xFF8B8B8B, 0xFF373737, 0xFF404040, 0xFF555555, 0xFFFFFFFF, 0xFFFFFFFF, true),
		SLATE("Slate Stone", 0xCE20242E, 0xC4282E3A, 0xFF2E3442, 0xFF64748B, 0xFFFFFFFF, 0xFFD0D8E4, 0xFFD8E2EE, 0xFFDCE4F0, false),
		MIDNIGHT_GOLD("Midnight Gold", 0xDC1C1822, 0xCC26212E, 0xFF2B2434, 0xFFA17D35, 0xFFFEF9C3, 0xFFE2C98F, 0xFFF5E4BF, 0xFFEDE0C8, false),
		HIGH_CONTRAST("High Contrast", 0xF00B0D12, 0xEE141820, 0xFF1A1F29, 0xFFE2E8F0, 0xFFFFFFFF, 0xFFE5E7EB, 0xFFB8C0CC, 0xFFCBD5E1, false);

		private final String displayName;
		private final int panelBg;
		private final int headerBg;
		private final int cardBg;
		private final int borderColor;
		private final int primaryText;
		private final int secondaryText;
		private final int frameTint;
		private final int buttonTint;
		private final boolean lightSurface;

		ThemePreset(
			String displayName,
			int panelBg,
			int headerBg,
			int cardBg,
			int borderColor,
			int primaryText,
			int secondaryText,
			int frameTint,
			int buttonTint,
			boolean lightSurface
		) {
			this.displayName = displayName;
			this.panelBg = panelBg;
			this.headerBg = headerBg;
			this.cardBg = cardBg;
			this.borderColor = borderColor;
			this.primaryText = primaryText;
			this.secondaryText = secondaryText;
			this.frameTint = frameTint;
			this.buttonTint = buttonTint;
			this.lightSurface = lightSurface;
		}

		public String displayName() {
			return displayName;
		}

		public int panelBg() {
			return panelBg;
		}

		public int headerBg() {
			return headerBg;
		}

		public int cardBg() {
			return cardBg;
		}

		public int borderColor() {
			return borderColor;
		}

		public int primaryText() {
			return primaryText;
		}

		public int secondaryText() {
			return secondaryText;
		}

		public int frameTint() {
			return frameTint;
		}

		public int buttonTint() {
			return buttonTint;
		}

		public boolean lightSurface() {
			return lightSurface;
		}

		public ThemePreset next() {
			ThemePreset[] all = values();
			return all[(ordinal() + 1) % all.length];
		}
	}

	public enum UiScaleMode {
		AUTO("Auto"),
		COMPACT("Compact"),
		NORMAL("Normal"),
		LARGE("Large");

		private final String displayName;

		UiScaleMode(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		public UiScaleMode next() {
			UiScaleMode[] all = values();
			return all[(ordinal() + 1) % all.length];
		}

		public float scaleFactor(int screenWidth) {
			return switch (this) {
				case AUTO -> screenWidth < 500 ? 0.90f : (screenWidth > 860 ? 1.08f : 1.00f);
				case COMPACT -> 0.88f;
				case NORMAL -> 1.00f;
				case LARGE -> 1.12f;
			};
		}
	}

	private static final class Data {
		ThemePreset themePreset = ThemePreset.OBSIDIAN;
		UiScaleMode uiScale = UiScaleMode.AUTO;
		boolean uiSoundsEnabled = true;
		float soundVolume = 0.7f;
	}

	private ThemePreset themePreset = ThemePreset.OBSIDIAN;
	private UiScaleMode uiScale = UiScaleMode.AUTO;
	private boolean uiSoundsEnabled = true;
	private float soundVolume = 0.7f;

	public ThemePreset themePreset() {
		return themePreset;
	}

	public void setThemePreset(ThemePreset themePreset) {
		this.themePreset = themePreset == null ? ThemePreset.OBSIDIAN : themePreset;
	}

	public UiScaleMode uiScale() {
		return uiScale;
	}

	public void setUiScale(UiScaleMode uiScale) {
		this.uiScale = uiScale == null ? UiScaleMode.AUTO : uiScale;
	}

	public boolean uiSoundsEnabled() {
		return uiSoundsEnabled;
	}

	public void setUiSoundsEnabled(boolean uiSoundsEnabled) {
		this.uiSoundsEnabled = uiSoundsEnabled;
	}

	public float soundVolume() {
		return soundVolume;
	}

	public void setSoundVolume(float soundVolume) {
		this.soundVolume = Math.clamp(Float.isFinite(soundVolume) ? soundVolume : 0.7f, 0.0f, 1.0f);
	}

	public String toJson() {
		Data data = new Data();
		data.themePreset = this.themePreset;
		data.uiScale = this.uiScale;
		data.uiSoundsEnabled = this.uiSoundsEnabled;
		data.soundVolume = this.soundVolume;
		return GSON.toJson(data);
	}

	public static OguiClientPreferences fromJson(String raw) {
		OguiClientPreferences prefs = new OguiClientPreferences();
		if (raw == null || raw.isBlank()) {
			return prefs;
		}
		try {
			Data parsed = GSON.fromJson(raw, Data.class);
			if (parsed != null) {
				prefs.setThemePreset(parsed.themePreset);
				prefs.setUiScale(parsed.uiScale);
				prefs.setUiSoundsEnabled(parsed.uiSoundsEnabled);
				prefs.setSoundVolume(parsed.soundVolume);
			}
		} catch (JsonSyntaxException ex) {
			LOGGER.warn("Ignoring malformed ogui-client preferences JSON; using defaults.", ex);
		}
		return prefs;
	}

	public static OguiClientPreferences loadFromConfigDir() {
		Path path = configPath();
		if (!Files.exists(path)) {
			OguiClientPreferences defaults = new OguiClientPreferences();
			defaults.saveToConfigDir();
			return defaults;
		}
		try {
			return fromJson(Files.readString(path));
		} catch (IOException ex) {
			LOGGER.warn("Failed to read {}; using defaults.", path, ex);
			return new OguiClientPreferences();
		}
	}

	public void saveToConfigDir() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, toJson());
		} catch (IOException ex) {
			LOGGER.warn("Failed to save {}", path, ex);
		}
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("ogui-client.json");
	}
}
