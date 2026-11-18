package me.ogsammaenr.ogui.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared vanilla Minecraft 26.3 sprite and pixel-bevel drawing primitives for {@code ogui-client} screens.
 */
public final class OguiMinecraftWidgets {
	public static final Identifier POPUP_BACKGROUND = Identifier.withDefaultNamespace("popup/background");
	public static final Identifier BUNDLE_SLOT_BG = Identifier.withDefaultNamespace("container/bundle/slot_background");
	public static final Identifier CONTAINER_SLOT = Identifier.withDefaultNamespace("container/slot");
	public static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
	public static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

	public static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	public static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
	public static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");

	public static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
	public static final Identifier CHECKBOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
	public static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
	public static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");

	public static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");
	public static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");

	private OguiMinecraftWidgets() {
	}

	/**
	 * Draws a Minecraft-native window frame: classic beveled {@code #C6C6C6} container when {@code VANILLA_CLASSIC}
	 * is active, or vanilla 9-slice {@code popup/background} with subtle accent trim otherwise.
	 */
	public static void drawWindowFrame(
		GuiGraphicsExtractor extractor,
		int x,
		int y,
		int width,
		int height,
		OguiClientPreferences.ThemePreset preset,
		int accentColor
	) {
		if (preset.lightSurface()) {
			// Classic Minecraft container bezel (#000000 border, #FFFFFF top/left, #555555 bottom/right, #C6C6C6 body)
			extractor.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFC6C6C6);
			extractor.outline(x, y, width, height, 0xFF000000);
			extractor.fill(x + 1, y + 1, x + width - 2, y + 3, 0xFFFFFFFF);
			extractor.fill(x + 1, y + 1, x + 3, y + height - 2, 0xFFFFFFFF);
			extractor.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, 0xFF555555);
			extractor.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, 0xFF555555);
			extractor.fill(x + 3, y + 3, x + width - 3, y + 5, 0xFF000000 | (accentColor & 0x00FFFFFF));
		} else {
			extractor.blitSprite(RenderPipelines.GUI_TEXTURED, POPUP_BACKGROUND, x, y, width, height, preset.frameTint());
			extractor.fill(x + 4, y + 3, x + width - 4, y + 5, 0xFF000000 | (accentColor & 0x00FFFFFF));
		}
	}

	/**
	 * Draws a recessed Minecraft inner container well (recessed chest cavity or dark 9-slice bundle well).
	 */
	public static void drawSunkenWell(
		GuiGraphicsExtractor extractor,
		int x,
		int y,
		int width,
		int height,
		OguiClientPreferences.ThemePreset preset
	) {
		if (width < 4 || height < 4) {
			return;
		}
		if (preset.lightSurface()) {
			extractor.fill(x, y, x + width, y + height, 0xFFAFAFAF);
			extractor.fill(x, y, x + width - 1, y + 1, 0xFF373737);
			extractor.fill(x, y, x + 1, y + height - 1, 0xFF373737);
			extractor.fill(x + 1, y + height - 1, x + width, y + height, 0xFFFFFFFF);
			extractor.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
		} else {
			extractor.blitSprite(RenderPipelines.GUI_TEXTURED, BUNDLE_SLOT_BG, x, y, width, height, 0xFFFFFFFF);
		}
	}

	/**
	 * Draws Minecraft's classic two-tone embossed groove divider line.
	 */
	public static void drawGrooveDivider(
		GuiGraphicsExtractor extractor,
		int x1,
		int x2,
		int y,
		OguiClientPreferences.ThemePreset preset
	) {
		int dark = preset.lightSurface() ? 0xFF555555 : 0xFF1B1E24;
		int light = preset.lightSurface() ? 0xFFFFFFFF : 0x668A94A6;
		extractor.horizontalLine(x1, x2, y, dark);
		extractor.horizontalLine(x1, x2, y + 1, light);
	}

	/**
	 * Draws Minecraft's 9-slice stone widget button with normal/highlighted/disabled states and optional tint.
	 */
	public static void drawStoneButton(
		GuiGraphicsExtractor extractor,
		int x,
		int y,
		int width,
		int height,
		boolean enabled,
		boolean hovered,
		int tintArgb
	) {
		Identifier sprite = !enabled ? BUTTON_DISABLED : (hovered ? BUTTON_HIGHLIGHTED : BUTTON);
		extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, tintArgb);
	}

	/**
	 * Draws Minecraft's native checkbox sprite ({@code widget/checkbox*}).
	 */
	public static void drawCheckbox(
		GuiGraphicsExtractor extractor,
		int x,
		int y,
		int size,
		boolean checked,
		boolean hovered
	) {
		Identifier sprite;
		if (checked) {
			sprite = hovered ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED;
		} else {
			sprite = hovered ? CHECKBOX_HIGHLIGHTED : CHECKBOX;
		}
		extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size);
	}

	/**
	 * Draws an authentic 18x18 vanilla {@code container/slot} frame and centers the 16x16 {@link ItemStack},
	 * including vanilla {@code slot_highlight_back} and {@code slot_highlight_front} when hovered.
	 */
	public static void drawItemSlot(
		GuiGraphicsExtractor extractor,
		Font font,
		ItemStack stack,
		int slotX,
		int slotY,
		boolean hovered,
		boolean awaitingConfirm
	) {
		extractor.blitSprite(RenderPipelines.GUI_TEXTURED, CONTAINER_SLOT, slotX, slotY, 18, 18);
		int itemX = slotX + 1;
		int itemY = slotY + 1;
		if (hovered) {
			int highlightTint = awaitingConfirm ? 0xFFFFB454 : 0xFFFFFFFF;
			extractor.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, itemX - 4, itemY - 4, 24, 24, highlightTint);
		}
		extractor.item(stack, itemX, itemY);
		extractor.itemDecorations(font, stack, itemX, itemY);
		if (hovered) {
			int highlightTint = awaitingConfirm ? 0xFFFFB454 : 0xFFFFFFFF;
			extractor.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, itemX - 4, itemY - 4, 24, 24, highlightTint);
		}
	}

	/**
	 * Draws Minecraft's 9-slice {@code widget/scroller_background} and {@code widget/scroller} thumb.
	 */
	public static void drawScrollbar(
		GuiGraphicsExtractor extractor,
		int x,
		int y,
		int trackHeight,
		int scrollOffset,
		int maxScrollOffset
	) {
		if (maxScrollOffset <= 0 || trackHeight < 12) {
			return;
		}
		int barWidth = 6;
		extractor.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BG, x, y, barWidth, trackHeight);
		float ratio = Math.clamp((float) scrollOffset / maxScrollOffset, 0.0F, 1.0F);
		int thumbH = Math.clamp(trackHeight / 3, 14, trackHeight);
		int thumbY = y + Math.round((trackHeight - thumbH) * ratio);
		extractor.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x, thumbY, barWidth, thumbH);
	}
}
