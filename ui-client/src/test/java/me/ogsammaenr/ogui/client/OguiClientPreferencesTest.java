package me.ogsammaenr.ogui.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiClientPreferencesTest {
	@Test
	void roundTripsJsonAndFallsBackSafelyOnMalformedInput() {
		OguiClientPreferences prefs = new OguiClientPreferences();
		prefs.setThemePreset(OguiClientPreferences.ThemePreset.MIDNIGHT_GOLD);
		prefs.setUiScale(OguiClientPreferences.UiScaleMode.LARGE);
		prefs.setUiSoundsEnabled(false);
		prefs.setSoundVolume(1.5f);

		assertEquals(1.0f, prefs.soundVolume());
		String json = prefs.toJson();

		OguiClientPreferences parsed = OguiClientPreferences.fromJson(json);
		assertEquals(OguiClientPreferences.ThemePreset.MIDNIGHT_GOLD, parsed.themePreset());
		assertEquals(OguiClientPreferences.UiScaleMode.LARGE, parsed.uiScale());
		assertFalse(parsed.uiSoundsEnabled());
		assertEquals(1.0f, parsed.soundVolume());

		OguiClientPreferences malformed = OguiClientPreferences.fromJson("{not-valid-json");
		assertEquals(OguiClientPreferences.ThemePreset.OBSIDIAN, malformed.themePreset());
		assertEquals(OguiClientPreferences.UiScaleMode.AUTO, malformed.uiScale());
		assertTrue(malformed.uiSoundsEnabled());
	}
}
