package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lightweight client launcher shown when the Quick Menu keybinding is pressed and multiple server shortcuts are available.
 */
public final class OguiQuickLauncherScreen extends Screen {
	private final List<S2CServerHelloPayload.ShortcutInfo> shortcuts;
	private final OguiClientPreferences preferences;
	private final Consumer<Identifier> onShortcutSelect;

	public OguiQuickLauncherScreen(
		List<S2CServerHelloPayload.ShortcutInfo> shortcuts,
		OguiClientPreferences preferences
	) {
		this(shortcuts, preferences, shortcutId -> {
			if (ClientPlayNetworking.canSend(C2SShortcutTriggerPayload.TYPE)) {
				ClientPlayNetworking.send(new C2SShortcutTriggerPayload(shortcutId));
			}
		});
	}

	public OguiQuickLauncherScreen(
		List<S2CServerHelloPayload.ShortcutInfo> shortcuts,
		OguiClientPreferences preferences,
		Consumer<Identifier> onShortcutSelect
	) {
		super(Component.translatable("ogui.screen.quick_launcher"));
		this.shortcuts = List.copyOf(shortcuts);
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.onShortcutSelect = Objects.requireNonNull(onShortcutSelect, "onShortcutSelect");
	}

	@Override
	protected void init() {
		int count = Math.min(shortcuts.size(), 8);
		int btnW = 200;
		int btnH = 22;
		int gap = 4;
		int totalH = count * (btnH + gap) + 34;
		int startY = (this.height - totalH) / 2 + 26;
		int cx = this.width / 2;

		for (int i = 0; i < count; i++) {
			S2CServerHelloPayload.ShortcutInfo info = shortcuts.get(i);
			Button.Builder builder = Button.builder(info.label(), btn -> {
				onClose();
				onShortcutSelect.accept(info.id());
			}).bounds(cx - btnW / 2, startY + i * (btnH + gap), btnW, btnH);
			if (!info.description().getString().isEmpty()) {
				builder.tooltip(Tooltip.create(info.description()));
			}
			addRenderableWidget(builder.build());
		}

		addRenderableWidget(Button.builder(Component.translatable("ogui.screen.close"), btn -> onClose())
			.bounds(cx - 50, startY + count * (btnH + gap) + 6, 100, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		var preset = preferences.themePreset();
		int count = Math.min(shortcuts.size(), 8);
		int boxW = 236;
		int boxH = count * 26 + 72;
		int boxX = (this.width - boxW) / 2;
		int boxY = (this.height - boxH) / 2;
		OguiMinecraftWidgets.drawWindowFrame(extractor, boxX, boxY, boxW, boxH, preset, 0xFF38BDF8);
		OguiMinecraftWidgets.drawGrooveDivider(extractor, boxX + 8, boxX + boxW - 8, boxY + 23, preset);
		int titleX = boxX + (boxW - this.font.width(this.title)) / 2;
		extractor.text(this.font, this.title, titleX, boxY + 10, preset.primaryText(), !preset.lightSurface());
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
