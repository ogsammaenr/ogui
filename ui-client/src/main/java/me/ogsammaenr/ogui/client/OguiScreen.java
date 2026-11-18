package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.client.assets.OguiClientAssetManager;
import me.ogsammaenr.ogui.client.render.OguiGuiGraphicsBridge;
import me.ogsammaenr.ogui.client.render.OguiStructureMesh;
import me.ogsammaenr.ogui.client.render.OguiStructurePipRenderState;
import me.ogsammaenr.ogui.network.C2SCloseNotifyPayload;
import me.ogsammaenr.ogui.network.C2SWidgetInteractPayload;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import me.ogsammaenr.ogui.runtime.OguiSession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure server-driven ViewModel renderer for {@link ScreenSnapshotDto}.
 * Caches zero server catalogs locally and forwards all interactions as {@link C2SWidgetInteractPayload}.
 */
public final class OguiScreen extends Screen {
	/**
	 * Abstraction for forwarding client widget interactions and screen close events
	 * (network packets by default, or local client preview handlers during standalone testing).
	 */
	@FunctionalInterface
	public interface InteractionSink {
		void sendInteraction(
			UUID sessionId,
			int revision,
			String widgetId,
			OguiClickAction action,
			String inputText,
			int numericValue
		);

		default void onScreenClosed(UUID sessionId) {
		}
	}

	private static final int DEBOUNCE_TICKS = 5;

	private static final class EditBoxEntry {
		final OguiLayoutEngine.PositionedWidget positioned;
		final OguiWidgetDto.TextInputDto dto;
		final EditBox editBox;
		final int baseX;
		final int baseY;
		int debounceTicks = -1;

		EditBoxEntry(OguiLayoutEngine.PositionedWidget positioned, OguiWidgetDto.TextInputDto dto, EditBox editBox, int baseX, int baseY) {
			this.positioned = positioned;
			this.dto = dto;
			this.editBox = editBox;
			this.baseX = baseX;
			this.baseY = baseY;
		}
	}

	private static final class StructureViewportState {
		float yaw;
		float pitch;
		float zoom = 1.0F;
		boolean autoRotate;
		long lastFrameMillis;

		StructureViewportState(OguiWidgetDto.StructurePreviewDto dto) {
			this.yaw = dto.initialYaw();
			this.pitch = dto.initialPitch();
			this.autoRotate = dto.autoRotate();
			this.lastFrameMillis = System.currentTimeMillis();
		}

		void resetFrom(OguiWidgetDto.StructurePreviewDto dto) {
			this.yaw = dto.initialYaw();
			this.pitch = dto.initialPitch();
			this.zoom = 1.0F;
			this.autoRotate = dto.autoRotate();
			this.lastFrameMillis = System.currentTimeMillis();
		}
	}

	private record CameraControls(
		OguiLayoutEngine.Rect reset,
		OguiLayoutEngine.Rect autoRotate,
		OguiLayoutEngine.Rect zoomOut,
		OguiLayoutEngine.Rect zoomIn
	) {
		boolean containsAny(double mx, double my) {
			return reset.contains(mx, my) || autoRotate.contains(mx, my)
				|| zoomOut.contains(mx, my) || zoomIn.contains(mx, my);
		}
	}

	private final OguiClientSessionState sessionState;
	private final OguiClientPreferences preferences;
	private final OguiClientAssetManager assetManager;
	private final InteractionSink interactionSink;
	private UUID sessionId;
	private int revision;
	private ScreenSnapshotDto snapshot;
	private OguiLayoutEngine.ComputedLayout layout;
	private final Map<String, EditBoxEntry> editBoxes = new LinkedHashMap<>();
	private final Map<String, StructureViewportState> viewportStates = new LinkedHashMap<>();
	private @Nullable String activeDragWidgetId;
	private OguiLayoutEngine.@Nullable Rect activeDragBounds;
	private double dragDistance;
	private int scrollOffset;
	private boolean suppressCloseNotify;

	public OguiScreen(
		OguiClientSessionState sessionState,
		OguiClientPreferences preferences,
		UUID sessionId,
		int revision,
		ScreenSnapshotDto snapshot
	) {
		this(
			sessionState,
			preferences,
			sessionId,
			revision,
			snapshot,
			new InteractionSink() {
				@Override
				public void sendInteraction(
					UUID sid,
					int rev,
					String widgetId,
					OguiClickAction action,
					String inputText,
					int numericValue
				) {
					if (ClientPlayNetworking.canSend(C2SWidgetInteractPayload.TYPE)) {
						ClientPlayNetworking.send(new C2SWidgetInteractPayload(
							sid,
							rev,
							widgetId,
							action,
							inputText == null ? "" : inputText,
							numericValue
						));
					}
				}

				@Override
				public void onScreenClosed(UUID sid) {
					sessionState.clearActiveScreenIfMatching(sid);
					if (ClientPlayNetworking.canSend(C2SCloseNotifyPayload.TYPE)) {
						ClientPlayNetworking.send(new C2SCloseNotifyPayload(sid));
					}
				}
			}
		);
	}

	public OguiScreen(
		OguiClientSessionState sessionState,
		OguiClientPreferences preferences,
		UUID sessionId,
		int revision,
		ScreenSnapshotDto snapshot,
		InteractionSink interactionSink
	) {
		this(sessionState, preferences, OguiClientAssetManager.shared(), sessionId, revision, snapshot, interactionSink);
	}

	public OguiScreen(
		OguiClientSessionState sessionState,
		OguiClientPreferences preferences,
		OguiClientAssetManager assetManager,
		UUID sessionId,
		int revision,
		ScreenSnapshotDto snapshot,
		InteractionSink interactionSink
	) {
		super(snapshot.title());
		this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
		this.preferences = Objects.requireNonNull(preferences, "preferences");
		this.assetManager = Objects.requireNonNull(assetManager, "assetManager");
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.revision = revision;
		this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
		this.interactionSink = Objects.requireNonNull(interactionSink, "interactionSink");
	}

	public UUID sessionId() {
		return sessionId;
	}

	public void applyLiveUpdate(UUID nextSessionId, int nextRevision, ScreenSnapshotDto nextSnapshot) {
		boolean sameScreen = this.sessionId.equals(nextSessionId)
			&& this.snapshot.screenId().equals(nextSnapshot.screenId());
		this.sessionId = nextSessionId;
		this.revision = nextRevision;
		this.snapshot = nextSnapshot;
		if (!sameScreen) {
			this.scrollOffset = 0;
			this.viewportStates.clear();
			this.activeDragWidgetId = null;
			this.activeDragBounds = null;
			this.dragDistance = 0.0;
		}
		rebuildLayoutAndInputs(true);
	}

	public void suppressNextCloseNotify() {
		this.suppressCloseNotify = true;
	}

	public void onReturnedFromChildModal() {
		this.suppressCloseNotify = false;
		rebuildLayoutAndInputs(true);
	}

	@Override
	protected void init() {
		rebuildLayoutAndInputs(false);
	}

	private void rebuildLayoutAndInputs(boolean preserveEditFocus) {
		if (this.width <= 0 || this.height <= 0) {
			return;
		}
		Map<String, String> preservedText = new LinkedHashMap<>();
		String focusedWidgetId = null;
		for (var entry : editBoxes.entrySet()) {
			preservedText.put(entry.getKey(), entry.getValue().editBox.getValue());
			if (preserveEditFocus && entry.getValue().editBox.isFocused()) {
				focusedWidgetId = entry.getKey();
			}
		}

		clearWidgets();
		editBoxes.clear();
		this.layout = OguiLayoutEngine.compute(snapshot, this.width, this.height, preferences.uiScale());
		this.scrollOffset = Math.clamp(this.scrollOffset, 0, this.layout.maxScrollOffset());

		for (OguiLayoutEngine.PositionedWidget pw : this.layout.widgets()) {
			if (pw.widget() instanceof OguiWidgetDto.TextInputDto ti) {
				OguiLayoutEngine.Rect r = pw.bounds();
				boolean inForm = pw.region() == OguiLayoutEngine.Region.FORM_CONTROL;
				int bx = r.x() + (inForm ? 6 : 2);
				int by = r.y() + (inForm ? 16 : 2);
				int bw = Math.max(40, r.width() - (inForm ? 12 : 4));
				int bh = Math.max(16, inForm ? 18 : r.height() - 4);

				EditBox box = new EditBox(this.font, bx, by, bw, bh, ti.label());
				box.setMaxLength(ti.maxLength());
				box.setHint(ti.placeholder());
				box.setEditable(ti.enabled());
				String currentValue = (preserveEditFocus && ti.widgetId().equals(focusedWidgetId))
					? preservedText.getOrDefault(ti.widgetId(), ti.value())
					: ti.value();
				box.setValue(currentValue);

				EditBoxEntry entry = new EditBoxEntry(pw, ti, box, bx, by);
				box.setResponder(text -> {
					if (ti.liveDebounce()) {
						entry.debounceTicks = DEBOUNCE_TICKS;
					}
				});
				if (ti.widgetId().equals(focusedWidgetId)) {
					box.setFocused(true);
					setFocused(box);
				}
				addRenderableWidget(box);
				editBoxes.put(ti.widgetId(), entry);
			}
		}
		syncScrollableEditBoxes();
	}

	private void syncScrollableEditBoxes() {
		if (layout == null) {
			return;
		}
		OguiLayoutEngine.Rect vp = layout.scrollViewportRect();
		for (EditBoxEntry entry : editBoxes.values()) {
			if (entry.positioned.scrollable()) {
				int adjustedY = entry.baseY - scrollOffset;
				entry.editBox.setY(adjustedY);
				boolean vis = adjustedY >= vp.y() && adjustedY + entry.editBox.getHeight() <= vp.bottom();
				entry.editBox.visible = vis;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		for (EditBoxEntry entry : editBoxes.values()) {
			if (entry.debounceTicks > 0) {
				entry.debounceTicks--;
				if (entry.debounceTicks == 0) {
					entry.debounceTicks = -1;
					sendInteraction(entry.dto.widgetId(), OguiClickAction.CLIENT_INPUT, entry.editBox.getValue(), 0);
				}
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		if (layout == null) {
			super.extractRenderState(extractor, mouseX, mouseY, partialTick);
			return;
		}

		var theme = preferences.themePreset();
		boolean headerShadow = !theme.lightSurface();
		int accent = withFullAlpha(snapshot.theme().accentColor());
		OguiLayoutEngine.Rect panel = layout.panelRect();

		// Vanilla-aligned beveled window frame & header groove
		OguiMinecraftWidgets.drawWindowFrame(extractor, panel.x(), panel.y(), panel.width(), panel.height(), theme, accent);
		OguiMinecraftWidgets.drawGrooveDivider(extractor, panel.x() + 6, panel.right() - 6, layout.headerRect().bottom() - 1, theme);

		// Title & subtitle
		extractor.text(this.font, snapshot.title(), panel.x() + 10, panel.y() + 8, theme.primaryText(), headerShadow);
		if (snapshot.theme().subtitle() != null) {
			extractor.text(this.font, snapshot.theme().subtitle(), panel.x() + 10, panel.y() + 20, theme.secondaryText(), headerShadow);
		}

		// Preferences gear button (vanilla stone button)
		OguiLayoutEngine.Rect prefRect = layout.prefButtonRect();
		boolean prefHovered = prefRect.contains(mouseX, mouseY);
		OguiMinecraftWidgets.drawStoneButton(extractor, prefRect.x(), prefRect.y(), prefRect.width(), prefRect.height(), true, prefHovered, theme.buttonTint());
		extractor.centeredText(this.font, "⚙", prefRect.x() + prefRect.width() / 2, prefRect.y() + 5, 0xFFFFFFFF);
		if (prefHovered) {
			extractor.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("ogui.screen.preferences")), mouseX, mouseY);
		}

		// Tabs
		for (OguiLayoutEngine.PositionedTab tab : layout.tabs()) {
			renderTab(extractor, tab, mouseX, mouseY, accent, theme);
		}

		// Recessed inner body container well for scrollable grids / fixed slots
		OguiLayoutEngine.Rect vp = layout.scrollViewportRect();
		if (snapshot.archetype() == LayoutArchetype.FIXED_GRID) {
			OguiLayoutEngine.Rect body = layout.bodyRect();
			OguiMinecraftWidgets.drawSunkenWell(extractor, body.x() - 2, body.y() - 2, body.width() + 4, body.height() + 4, theme);
		} else {
			OguiMinecraftWidgets.drawSunkenWell(extractor, vp.x() - 2, vp.y() - 2, vp.width() + 4, vp.height() + 4, theme);
		}

		// Summary cards
		for (OguiLayoutEngine.PositionedSummary summary : layout.summaries()) {
			renderSummaryCard(extractor, summary, theme, accent);
		}

		// Non-scrollable widgets
		for (OguiLayoutEngine.PositionedWidget pw : layout.widgets()) {
			if (!pw.scrollable()) {
				renderPositionedWidget(extractor, pw, 0, mouseX, mouseY, accent, theme);
			}
		}

		// Scrollable body region with scissor clipping
		extractor.enableScissor(vp.x(), vp.y(), vp.right(), vp.bottom());
		for (OguiLayoutEngine.PositionedWidget pw : layout.widgets()) {
			if (pw.scrollable()) {
				renderPositionedWidget(extractor, pw, scrollOffset, mouseX, mouseY, accent, theme);
			}
		}
		extractor.disableScissor();

		// Vanilla scroller track & thumb outside right edge of viewport
		if (layout.maxScrollOffset() > 0) {
			int trackX = Math.min(panel.right() - 8, vp.right() + 2);
			int trackH = Math.max(12, vp.height());
			OguiMinecraftWidgets.drawScrollbar(extractor, trackX, vp.y(), trackH, scrollOffset, layout.maxScrollOffset());
		}

		// Footer bar groove & vanilla nav buttons
		OguiLayoutEngine.Rect footer = layout.footerRect();
		OguiMinecraftWidgets.drawGrooveDivider(extractor, panel.x() + 6, panel.right() - 6, footer.y(), theme);
		for (OguiLayoutEngine.PositionedNav nav : layout.navButtons()) {
			renderNavButton(extractor, nav, mouseX, mouseY, accent, theme);
		}
		if (snapshot.archetype() == LayoutArchetype.TABBED_PANEL || snapshot.archetype() == LayoutArchetype.PAGED_GRID) {
			String pageText = (snapshot.page() + 1) + " / " + snapshot.totalPages();
			extractor.text(
				this.font,
				pageText,
				footer.x() + (footer.width() - this.font.width(pageText)) / 2,
				footer.y() + 11,
				theme.primaryText(),
				headerShadow
			);
		}

		// Feedback banner (Minecraft stone pill with emerald/crimson accent)
		OguiClientSessionState.FeedbackBanner banner = sessionState.activeFeedback(System.currentTimeMillis());
		if (banner != null) {
			int bannerTint = banner.success() ? 0xFFA7F3D0 : 0xFFFCA5A5;
			int border = banner.success() ? 0xFF22C55E : 0xFFEF4444;
			int bw = Math.min(panel.width() - 24, this.font.width(banner.message()) + 24);
			int bx = panel.x() + (panel.width() - bw) / 2;
			int by = footer.y() - 23;
			OguiMinecraftWidgets.drawStoneButton(extractor, bx, by, bw, 19, true, false, bannerTint);
			extractor.outline(bx, by, bw, 19, border);
			extractor.centeredText(this.font, banner.message(), panel.x() + panel.width() / 2, by + 6, 0xFFFFFFFF);
		}

		// Render child Renderables (EditBox widgets) on top of card backgrounds
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}

	private void renderTab(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.PositionedTab tab,
		int mouseX,
		int mouseY,
		int accent,
		OguiClientPreferences.ThemePreset theme
	) {
		OguiLayoutEngine.Rect r = tab.bounds();
		boolean hovered = r.contains(mouseX, mouseY) && tab.tab().enabled();
		boolean active = tab.active();
		int tint = active ? 0xFFFFFFFF : (hovered ? theme.buttonTint() : darkenTint(theme.buttonTint()));
		OguiMinecraftWidgets.drawStoneButton(extractor, r.x(), r.y(), r.width(), r.height(), tab.tab().enabled(), active || hovered, tint);
		if (active) {
			extractor.fill(r.x() + 2, r.bottom() - 2, r.right() - 2, r.bottom(), accent);
		}

		ItemStack stack = OguiIconResolver.toRenderStack(tab.tab().icon());
		extractor.item(stack, r.x() + 3, r.y() + 2);
		int textColor = !tab.tab().enabled() ? 0xFFA0A0A0 : (active ? 0xFFFFFFA0 : 0xFFFFFFFF);
		String label = trimToWidth(tab.tab().title().getString(), Math.max(12, r.width() - 24));
		extractor.text(this.font, label, r.x() + 21, r.y() + 6, textColor, true);

		if (r.contains(mouseX, mouseY)) {
			List<Component> tip = new ArrayList<>();
			tip.add(tab.tab().title());
			tip.addAll(tab.tab().lore());
			extractor.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private void renderSummaryCard(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.PositionedSummary summary,
		OguiClientPreferences.ThemePreset theme,
		int defaultAccent
	) {
		OguiLayoutEngine.Rect r = summary.bounds();
		int cardAccent = summary.card().accentColor() != 0 ? withFullAlpha(summary.card().accentColor()) : defaultAccent;
		OguiMinecraftWidgets.drawSunkenWell(extractor, r.x(), r.y(), r.width(), r.height(), theme);
		extractor.fill(r.x() + 1, r.y() + 2, r.x() + 3, r.bottom() - 2, cardAccent);

		ItemStack stack = OguiIconResolver.toRenderStack(summary.card().icon());
		OguiMinecraftWidgets.drawItemSlot(extractor, this.font, stack, r.x() + 5, r.y() + 3, false, false);
		boolean shadow = !theme.lightSurface();
		int titleColor = theme.lightSurface() ? 0xFF202020 : theme.primaryText();
		int lineColor = theme.lightSurface() ? 0xFF404040 : theme.secondaryText();
		String title = trimToWidth(summary.card().title().getString(), Math.max(20, r.width() - 30));
		extractor.text(this.font, title, r.x() + 26, r.y() + 8, titleColor, shadow);

		int ly = r.y() + 24;
		for (Component line : summary.card().lines()) {
			if (ly + 9 > r.bottom() - 2) {
				break;
			}
			String trimmed = trimToWidth(line.getString(), Math.max(20, r.width() - 14));
			extractor.text(this.font, trimmed, r.x() + 7, ly, lineColor, shadow);
			ly += 11;
		}
	}

	private void renderPositionedWidget(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.PositionedWidget pw,
		int yOffset,
		int mouseX,
		int mouseY,
		int accent,
		OguiClientPreferences.ThemePreset theme
	) {
		OguiWidgetDto w = pw.widget();
		OguiLayoutEngine.Rect raw = pw.bounds();
		OguiLayoutEngine.Rect r = new OguiLayoutEngine.Rect(raw.x(), raw.y() - yOffset, raw.width(), raw.height());

		boolean withinViewport = !pw.scrollable() || layout.scrollViewportRect().contains(mouseX, mouseY);
		boolean hovered = withinViewport && w.enabled() && r.contains(mouseX, mouseY);
		boolean awaitingConfirm = isAwaitingConfirm(w);

		if (w instanceof OguiWidgetDto.StructurePreviewDto sp) {
			renderStructurePreview(extractor, pw, sp, r, withinViewport, mouseX, mouseY, accent, theme);
			return;
		}

		if (pw.region() == OguiLayoutEngine.Region.ENTRY
			&& OguiLayoutEngine.isGalleryGrid(snapshot)
			&& w instanceof OguiWidgetDto.ButtonDto b) {
			renderGalleryCard(extractor, b, r, withinViewport, hovered, awaitingConfirm, mouseX, mouseY, accent, theme);
			return;
		}

		if (pw.region() == OguiLayoutEngine.Region.FIXED_SLOT) {
			OguiMinecraftWidgets.drawStoneButton(
				extractor,
				r.x(),
				r.y(),
				r.width(),
				r.height(),
				w.enabled(),
				hovered,
				awaitingConfirm ? 0xFFFFB499 : theme.buttonTint()
			);
			ItemStack stack = OguiIconResolver.toRenderStack(w.icon());
			int slotX = r.x() + (r.width() - 18) / 2;
			int slotY = r.y() + (r.height() - 18) / 2;
			OguiMinecraftWidgets.drawItemSlot(extractor, this.font, stack, slotX, slotY, hovered, awaitingConfirm);
			if (withinViewport && r.contains(mouseX, mouseY)) {
				extractor.setComponentTooltipForNextFrame(this.font, buildTooltip(w, awaitingConfirm), mouseX, mouseY);
			}
			return;
		}

		if (w instanceof OguiWidgetDto.TextInputDto ti) {
			if (pw.region() == OguiLayoutEngine.Region.FORM_CONTROL) {
				OguiMinecraftWidgets.drawSunkenWell(extractor, r.x(), r.y(), r.width(), r.height(), theme);
				int labelColor = theme.lightSurface() ? 0xFF202020 : theme.primaryText();
				String lbl = trimToWidth(ti.label().getString(), Math.max(20, r.width() - 12));
				extractor.text(this.font, lbl, r.x() + 6, r.y() + 4, labelColor, !theme.lightSurface());
			}
			return;
		}

		if (w instanceof OguiWidgetDto.QuantityDto q && pw.region() == OguiLayoutEngine.Region.FORM_CONTROL) {
			OguiMinecraftWidgets.drawSunkenWell(extractor, r.x(), r.y(), r.width(), r.height(), theme);
			ItemStack stack = OguiIconResolver.toRenderStack(q.icon().withCount(Math.clamp(q.value(), 1, 99)));
			OguiMinecraftWidgets.drawItemSlot(extractor, this.font, stack, r.x() + 5, r.y() + 2, false, false);
			boolean shadow = !theme.lightSurface();
			int labelColor = theme.lightSurface() ? 0xFF202020 : theme.primaryText();
			String titleText = q.label().getString() + ": " + q.value();
			extractor.text(this.font, trimToWidth(titleText, r.width() - 32), r.x() + 26, r.y() + 4, labelColor, shadow);
			if (q.formattedSummary() != null) {
				int summaryColor = theme.lightSurface() ? 0xFF8A5A00 : accent;
				extractor.text(this.font, trimToWidth(q.formattedSummary().getString(), r.width() - 32), r.x() + 26, r.y() + 13, summaryColor, shadow);
			}
			for (OguiLayoutEngine.PositionedQuantityStep step : pw.quantitySteps()) {
				OguiLayoutEngine.Rect sb = new OguiLayoutEngine.Rect(
					step.bounds().x(), step.bounds().y() - yOffset, step.bounds().width(), step.bounds().height()
				);
				boolean stepHover = withinViewport && q.enabled() && sb.contains(mouseX, mouseY);
				int stepTint = step.positive() ? 0xFFB8F5C8 : 0xFFFBC4C4;
				OguiMinecraftWidgets.drawStoneButton(extractor, sb.x(), sb.y(), sb.width(), sb.height(), q.enabled(), stepHover, stepTint);
				extractor.centeredText(this.font, step.label(), sb.x() + sb.width() / 2, sb.y() + 5, q.enabled() ? 0xFFFFFFFF : 0xFFA0A0A0);
			}
			if (withinViewport && r.contains(mouseX, mouseY) && pw.quantitySteps().stream().noneMatch(s -> new OguiLayoutEngine.Rect(s.bounds().x(), s.bounds().y() - yOffset, s.bounds().width(), s.bounds().height()).contains(mouseX, mouseY))) {
				extractor.setComponentTooltipForNextFrame(this.font, buildTooltip(w, false), mouseX, mouseY);
			}
			return;
		}

		// Standard card rendering (Button, Toggle, Cycle, compact Quantity, Hero) via vanilla 9-slice stone button
		int cardTint = awaitingConfirm ? 0xFFFFB499 : theme.buttonTint();
		OguiMinecraftWidgets.drawStoneButton(extractor, r.x(), r.y(), r.width(), r.height(), w.enabled(), hovered, cardTint);
		if (awaitingConfirm) {
			extractor.outline(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, 0xFFF59E0B);
		}

		OguiIcon icon = w instanceof OguiWidgetDto.ToggleDto t ? t.effectiveIcon() : w.icon();
		ItemStack stack = OguiIconResolver.toRenderStack(icon);
		int slotX = r.x() + 4;
		int slotY = r.y() + Math.max(2, (r.height() - 18) / 2);
		OguiMinecraftWidgets.drawItemSlot(extractor, this.font, stack, slotX, slotY, hovered && w.enabled(), awaitingConfirm);

		int rightReserve = 6;
		if (w instanceof OguiWidgetDto.ToggleDto t) {
			int cbSize = r.height() >= 28 ? 16 : 14;
			int cbX = r.right() - cbSize - 5;
			int cbY = r.y() + (r.height() - cbSize) / 2;
			OguiMinecraftWidgets.drawCheckbox(extractor, cbX, cbY, cbSize, t.value(), hovered);
			rightReserve = cbSize + 10;
		} else if (w instanceof OguiWidgetDto.CycleDto) {
			int arrowY = r.y() + (r.height() >= 28 ? 6 : 7);
			extractor.text(this.font, "◀▶", r.right() - 18, arrowY, hovered ? 0xFFFFFFA0 : 0xFFE2E8F0, true);
			rightReserve = 22;
		}

		int textX = slotX + 21;
		int maxTextW = Math.max(16, r.right() - rightReserve - textX);
		int titleColor = !w.enabled() ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFA0 : 0xFFFFFFFF);
		String titleStr = trimToWidth(w.label().getString(), maxTextW);
		extractor.text(this.font, titleStr, textX, r.y() + (r.height() >= 28 ? 6 : 7), titleColor, true);

		if (r.height() >= 28) {
			Component subLine = switch (w) {
				case OguiWidgetDto.ButtonDto b -> b.badge() != null ? b.badge() : (b.lore().isEmpty() ? null : b.lore().getFirst());
				case OguiWidgetDto.ToggleDto t -> t.value() ? t.onLabel() : t.offLabel();
				case OguiWidgetDto.CycleDto c -> c.selectedOption().label();
				case OguiWidgetDto.QuantityDto q -> q.formattedSummary() != null ? q.formattedSummary() : Component.literal("x" + q.value());
				case OguiWidgetDto.TextInputDto ignored -> null;
				case OguiWidgetDto.StructurePreviewDto ignored -> null;
			};
			if (subLine != null) {
				int subColor = w.enabled() ? 0xFFE2E8F0 : 0xFF94A3B8;
				extractor.text(this.font, trimToWidth(subLine.getString(), maxTextW), textX, r.y() + 18, subColor, true);
			}
		}

		if (withinViewport && r.contains(mouseX, mouseY)) {
			extractor.setComponentTooltipForNextFrame(this.font, buildTooltip(w, awaitingConfirm), mouseX, mouseY);
		}
	}

	private void renderGalleryCard(
		GuiGraphicsExtractor extractor,
		OguiWidgetDto.ButtonDto b,
		OguiLayoutEngine.Rect r,
		boolean withinViewport,
		boolean hovered,
		boolean awaitingConfirm,
		int mouseX,
		int mouseY,
		int accent,
		OguiClientPreferences.ThemePreset theme
	) {
		int cardTint = awaitingConfirm ? 0xFFFFB499 : theme.buttonTint();
		OguiMinecraftWidgets.drawStoneButton(extractor, r.x(), r.y(), r.width(), r.height(), b.enabled(), hovered, cardTint);
		if (awaitingConfirm) {
			extractor.outline(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, 0xFFF59E0B);
		} else if (hovered) {
			extractor.outline(r.x() + 1, r.y() + 1, r.width() - 2, r.height() - 2, accent);
		}

		int thumbX = r.x() + 4;
		int thumbY = r.y() + 4;
		int thumbW = r.width() - 8;
		int thumbH = Math.max(24, r.height() - 32);
		OguiMinecraftWidgets.drawSunkenWell(extractor, thumbX, thumbY, thumbW, thumbH, theme);

		int innerX = thumbX + 2;
		int innerY = thumbY + 2;
		int innerW = Math.max(8, thumbW - 4);
		int innerH = Math.max(8, thumbH - 4);
		extractor.fillGradient(innerX, innerY, innerX + innerW, innerY + innerH, 0xFF141B29, 0xFF1E2634);

		ItemStack stack = OguiIconResolver.toRenderStack(b.icon());
		boolean drewPng = false;
		if (b.previewImage() != null) {
			OguiClientAssetManager.AssetSlot slot = assetManager.resolveOrRequest(b.previewImage());
			if (slot.status() == OguiClientAssetManager.Status.READY && slot.png() != null) {
				OguiClientAssetManager.PngAsset png = slot.png();
				float fitScale = Math.min((float) innerW / png.width(), (float) innerH / png.height());
				int drawW = Math.max(1, Math.round(png.width() * fitScale));
				int drawH = Math.max(1, Math.round(png.height() * fitScale));
				int drawX = innerX + (innerW - drawW) / 2;
				int drawY = innerY + (innerH - drawH) / 2;
				extractor.blit(
					RenderPipelines.GUI_TEXTURED,
					png.textureId(),
					drawX, drawY,
					0.0F, 0.0F,
					drawW, drawH,
					png.width(), png.height(),
					png.width(), png.height()
				);
				drewPng = true;
			} else if (slot.status() == OguiClientAssetManager.Status.LOADING) {
				extractor.item(stack, innerX + (innerW - 16) / 2, innerY + Math.max(2, (innerH - 22) / 2));
				int barW = Math.max(16, innerW - 12);
				int barX = innerX + (innerW - barW) / 2;
				int barY = innerY + innerH - 7;
				extractor.fill(barX, barY, barX + barW, barY + 3, 0xFF0F172A);
				int fillW = Math.max(2, Math.round(barW * Math.max(0.12F, slot.progress())));
				extractor.fill(barX, barY, barX + fillW, barY + 3, accent);
			} else {
				OguiMinecraftWidgets.drawItemSlot(
					extractor,
					this.font,
					stack,
					innerX + (innerW - 18) / 2,
					innerY + (innerH - 18) / 2,
					hovered,
					awaitingConfirm
				);
			}
		} else {
			OguiMinecraftWidgets.drawItemSlot(
				extractor,
				this.font,
				stack,
				innerX + (innerW - 18) / 2,
				innerY + (innerH - 18) / 2,
				hovered,
				awaitingConfirm
			);
		}

		if (drewPng) {
			int badgeX = innerX + innerW - 18;
			int badgeY = innerY + innerH - 18;
			extractor.fill(badgeX - 1, badgeY - 1, badgeX + 17, badgeY + 17, 0x990F172A);
			extractor.item(stack, badgeX, badgeY);
		}

		int textY = thumbY + thumbH + 3;
		int maxTextW = Math.max(16, r.width() - 10);
		int titleColor = !b.enabled() ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFA0 : 0xFFFFFFFF);
		extractor.text(this.font, trimToWidth(b.label().getString(), maxTextW), r.x() + 5, textY, titleColor, true);

		Component subLine = b.badge() != null ? b.badge() : (b.lore().isEmpty() ? null : b.lore().getFirst());
		if (subLine != null) {
			int subColor = b.enabled() ? 0xFFE2E8F0 : 0xFF94A3B8;
			extractor.text(this.font, trimToWidth(subLine.getString(), maxTextW), r.x() + 5, textY + 11, subColor, true);
		}

		if (withinViewport && r.contains(mouseX, mouseY)) {
			extractor.setComponentTooltipForNextFrame(this.font, buildTooltip(b, awaitingConfirm), mouseX, mouseY);
		}
	}

	private void renderStructurePreview(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.PositionedWidget pw,
		OguiWidgetDto.StructurePreviewDto sp,
		OguiLayoutEngine.Rect r,
		boolean withinViewport,
		int mouseX,
		int mouseY,
		int accent,
		OguiClientPreferences.ThemePreset theme
	) {
		OguiMinecraftWidgets.drawSunkenWell(extractor, r.x(), r.y(), r.width(), r.height(), theme);
		int vx = r.x() + 2;
		int vy = r.y() + 2;
		int vw = Math.max(16, r.width() - 4);
		int vh = Math.max(16, r.height() - 4);
		extractor.fillGradient(vx, vy, vx + vw, vy + vh, 0xFF101826, 0xFF1E2634);
		extractor.fill(vx, vy, vx + vw, vy + 1, accent);

		StructureViewportState vs = viewportStates.computeIfAbsent(sp.widgetId(), ignored -> new StructureViewportState(sp));
		long now = System.currentTimeMillis();
		long dtMillis = Math.clamp(now - vs.lastFrameMillis, 0L, 100L);
		vs.lastFrameMillis = now;

		CameraControls controls = computeCameraControls(r);
		String metaLabel = trimToWidth(sp.label().getString(), Math.max(24, vw - 68));
		extractor.text(this.font, metaLabel, vx + 5, vy + 4, 0xFFFFFFFF, true);

		if (sp.structureAsset() != null) {
			OguiClientAssetManager.AssetSlot slot = assetManager.resolveOrRequest(sp.structureAsset());
			if (slot.status() == OguiClientAssetManager.Status.READY) {
				OguiStructureMesh mesh = slot.ensureMesh(Minecraft.getInstance());
				if (mesh != null && !mesh.isEmpty()) {
					if (vs.autoRotate && !sp.widgetId().equals(activeDragWidgetId)) {
						vs.yaw = (vs.yaw + (dtMillis / 1000.0F) * 18.0F) % 360.0F;
					}
					String dims = mesh.sizeX() + "x" + mesh.sizeY() + "x" + mesh.sizeZ() + " • " + mesh.blockCount();
					extractor.text(this.font, dims, vx + 5, vy + 14, 0xFF94A3B8, true);

					int innerX0 = vx + 2;
					int innerY0 = vy + 16;
					int innerX1 = vx + vw - 2;
					int innerY1 = vy + vh - 13;
					if (innerX1 - innerX0 >= 16 && innerY1 - innerY0 >= 16) {
						int drawW = innerX1 - innerX0;
						int drawH = innerY1 - innerY0;
						float fitScale = (Math.min(drawW, drawH) * 0.42F / Math.max(1.2F, mesh.boundingRadius())) * vs.zoom;
						ScreenRectangle scissor = pw.scrollable()
							? new ScreenRectangle(
								layout.scrollViewportRect().x(),
								layout.scrollViewportRect().y(),
								layout.scrollViewportRect().width(),
								layout.scrollViewportRect().height()
							)
							: null;
						((OguiGuiGraphicsBridge) extractor).ogui$submitPip(new OguiStructurePipRenderState(
							mesh,
							sp.structureAsset().sha256(),
							vs.yaw,
							vs.pitch,
							innerX0,
							innerY0,
							innerX1,
							innerY1,
							fitScale,
							scissor
						));
					}
				} else {
					renderStructurePlaceholder(extractor, sp, vx, vy, vw, vh, Component.translatable("ogui.preview3d.unavailable"), 0.0F, accent);
				}
			} else if (slot.status() == OguiClientAssetManager.Status.LOADING) {
				renderStructurePlaceholder(extractor, sp, vx, vy, vw, vh, Component.translatable("ogui.preview3d.loading"), Math.max(0.10F, slot.progress()), accent);
			} else {
				renderStructurePlaceholder(extractor, sp, vx, vy, vw, vh, Component.translatable("ogui.preview3d.unavailable"), 0.0F, accent);
			}
		} else {
			renderStructurePlaceholder(extractor, sp, vx, vy, vw, vh, Component.translatable("ogui.preview3d.unavailable"), 0.0F, accent);
		}

		renderMiniCameraButton(extractor, controls.reset(), "R", false, withinViewport, mouseX, mouseY, theme, Component.translatable("ogui.preview3d.reset"));
		renderMiniCameraButton(extractor, controls.autoRotate(), vs.autoRotate ? "||" : "▶", vs.autoRotate, withinViewport, mouseX, mouseY, theme, Component.translatable("ogui.preview3d.autorotate"));
		renderMiniCameraButton(extractor, controls.zoomOut(), "-", false, withinViewport, mouseX, mouseY, theme, Component.translatable("ogui.preview3d.zoom_out"));
		renderMiniCameraButton(extractor, controls.zoomIn(), "+", false, withinViewport, mouseX, mouseY, theme, Component.translatable("ogui.preview3d.zoom_in"));

		String hint = trimToWidth(Component.translatable("ogui.preview3d.hint").getString(), Math.max(24, vw - 8));
		extractor.centeredText(this.font, hint, vx + vw / 2, vy + vh - 10, 0xFFCBD5E1);

		if (withinViewport && r.contains(mouseX, mouseY) && activeDragWidgetId == null && !controls.containsAny(mouseX, mouseY)) {
			extractor.setComponentTooltipForNextFrame(this.font, buildTooltip(sp, false), mouseX, mouseY);
		}
	}

	private void renderStructurePlaceholder(
		GuiGraphicsExtractor extractor,
		OguiWidgetDto.StructurePreviewDto sp,
		int vx,
		int vy,
		int vw,
		int vh,
		Component statusText,
		float progress,
		int accent
	) {
		ItemStack icon = OguiIconResolver.toRenderStack(sp.icon());
		int centerX = vx + vw / 2;
		int centerY = vy + vh / 2;
		extractor.item(icon, centerX - 8, centerY - 14);
		extractor.centeredText(this.font, trimToWidth(statusText.getString(), Math.max(24, vw - 12)), centerX, centerY + 6, 0xFFCBD5E1);
		if (progress > 0.0F) {
			int barW = Math.clamp(vw / 2, 48, 120);
			int barX = centerX - barW / 2;
			int barY = centerY + 18;
			extractor.fill(barX, barY, barX + barW, barY + 4, 0xFF0F172A);
			int fillW = Math.max(3, Math.round(barW * Math.clamp(progress, 0.0F, 1.0F)));
			extractor.fill(barX, barY, barX + fillW, barY + 4, accent);
		}
	}

	private void renderMiniCameraButton(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.Rect btn,
		String glyph,
		boolean active,
		boolean withinViewport,
		int mouseX,
		int mouseY,
		OguiClientPreferences.ThemePreset theme,
		Component tooltip
	) {
		boolean hover = withinViewport && btn.contains(mouseX, mouseY);
		int tint = active ? 0xFFA7F3D0 : theme.buttonTint();
		OguiMinecraftWidgets.drawStoneButton(extractor, btn.x(), btn.y(), btn.width(), btn.height(), true, hover || active, tint);
		extractor.centeredText(this.font, glyph, btn.x() + btn.width() / 2, btn.y() + 3, hover || active ? 0xFFFFFFA0 : 0xFFFFFFFF);
		if (hover) {
			extractor.setComponentTooltipForNextFrame(this.font, List.of(tooltip), mouseX, mouseY);
		}
	}

	private static CameraControls computeCameraControls(OguiLayoutEngine.Rect viewportRect) {
		int size = 13;
		int gap = 2;
		int y = viewportRect.y() + 4;
		int right = viewportRect.right() - 4;
		OguiLayoutEngine.Rect zoomIn = new OguiLayoutEngine.Rect(right - size, y, size, size);
		OguiLayoutEngine.Rect zoomOut = new OguiLayoutEngine.Rect(zoomIn.x() - gap - size, y, size, size);
		OguiLayoutEngine.Rect autoRot = new OguiLayoutEngine.Rect(zoomOut.x() - gap - size, y, size, size);
		OguiLayoutEngine.Rect reset = new OguiLayoutEngine.Rect(autoRot.x() - gap - size, y, size, size);
		return new CameraControls(reset, autoRot, zoomOut, zoomIn);
	}

	private void renderNavButton(
		GuiGraphicsExtractor extractor,
		OguiLayoutEngine.PositionedNav nav,
		int mouseX,
		int mouseY,
		int accent,
		OguiClientPreferences.ThemePreset theme
	) {
		OguiLayoutEngine.Rect r = nav.bounds();
		boolean hovered = nav.enabled() && r.contains(mouseX, mouseY);
		OguiMinecraftWidgets.drawStoneButton(extractor, r.x(), r.y(), r.width(), r.height(), nav.enabled(), hovered, theme.buttonTint());
		int textColor = !nav.enabled() ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFA0 : 0xFFFFFFFF);
		extractor.centeredText(this.font, Component.translatable(nav.labelKey()), r.x() + r.width() / 2, r.y() + 6, textColor);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (layout == null) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		OguiClickAction action = mapMouseAction(event);

		if (layout.prefButtonRect().contains(mx, my)) {
			playClickSound();
			if (this.minecraft != null) {
				this.suppressCloseNotify = true;
				this.minecraft.gui.setScreen(new OguiPreferencesScreen(this, preferences));
			}
			return true;
		}

		for (OguiLayoutEngine.PositionedTab tab : layout.tabs()) {
			if (tab.bounds().contains(mx, my) && tab.tab().enabled() && !tab.active()) {
				playClickSound();
				sendInteraction(OguiSession.NAV_TAB_PREFIX + tab.tab().id(), action, "", 0);
				return true;
			}
		}

		for (OguiLayoutEngine.PositionedNav nav : layout.navButtons()) {
			if (nav.enabled() && nav.bounds().contains(mx, my)) {
				playClickSound();
				sendInteraction(nav.actionId(), action, "", 0);
				return true;
			}
		}

		for (OguiLayoutEngine.PositionedWidget pw : layout.widgets()) {
			if (!pw.widget().enabled()) {
				continue;
			}
			if (pw.scrollable() && !layout.scrollViewportRect().contains(mx, my)) {
				continue;
			}
			int yOff = pw.scrollable() ? scrollOffset : 0;
			OguiLayoutEngine.Rect r = new OguiLayoutEngine.Rect(
				pw.bounds().x(), pw.bounds().y() - yOff, pw.bounds().width(), pw.bounds().height()
			);
			if (!r.contains(mx, my)) {
				continue;
			}

			if (pw.widget() instanceof OguiWidgetDto.TextInputDto) {
				return true;
			}

			if (pw.widget() instanceof OguiWidgetDto.StructurePreviewDto sp) {
				StructureViewportState vs = viewportStates.computeIfAbsent(sp.widgetId(), ignored -> new StructureViewportState(sp));
				CameraControls controls = computeCameraControls(r);
				if (controls.reset().contains(mx, my)) {
					playClickSound();
					vs.resetFrom(sp);
					return true;
				}
				if (controls.autoRotate().contains(mx, my)) {
					playClickSound();
					vs.autoRotate = !vs.autoRotate;
					vs.lastFrameMillis = System.currentTimeMillis();
					return true;
				}
				if (controls.zoomIn().contains(mx, my)) {
					playClickSound();
					vs.zoom = Math.clamp(vs.zoom + 0.22F, 0.45F, 2.50F);
					return true;
				}
				if (controls.zoomOut().contains(mx, my)) {
					playClickSound();
					vs.zoom = Math.clamp(vs.zoom - 0.22F, 0.45F, 2.50F);
					return true;
				}
				this.activeDragWidgetId = sp.widgetId();
				this.activeDragBounds = r;
				this.dragDistance = 0.0;
				return true;
			}

			if (pw.widget() instanceof OguiWidgetDto.QuantityDto q && !pw.quantitySteps().isEmpty()) {
				for (OguiLayoutEngine.PositionedQuantityStep step : pw.quantitySteps()) {
					OguiLayoutEngine.Rect sb = new OguiLayoutEngine.Rect(
						step.bounds().x(), step.bounds().y() - yOff, step.bounds().width(), step.bounds().height()
					);
					if (sb.contains(mx, my)) {
						playClickSound();
						sendInteraction(q.widgetId(), OguiClickAction.CLIENT_INPUT, "", step.targetValue());
						return true;
					}
				}
			}

			playClickSound();
			sendInteraction(pw.widget().widgetId(), action, "", 0);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (activeDragWidgetId != null) {
			StructureViewportState vs = viewportStates.get(activeDragWidgetId);
			if (vs != null) {
				vs.yaw = (vs.yaw + (float) (dragX * 0.85)) % 360.0F;
				vs.pitch = Math.clamp(vs.pitch + (float) (dragY * 0.65), -85.0F, 85.0F);
				vs.lastFrameMillis = System.currentTimeMillis();
				dragDistance += Math.hypot(dragX, dragY);
			}
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (activeDragWidgetId != null) {
			String widgetId = activeDragWidgetId;
			OguiLayoutEngine.Rect bounds = activeDragBounds;
			double moved = dragDistance;
			this.activeDragWidgetId = null;
			this.activeDragBounds = null;
			this.dragDistance = 0.0;
			if (moved < 4.0 && bounds != null && bounds.contains(event.x(), event.y())) {
				playClickSound();
				sendInteraction(widgetId, mapMouseAction(event), "", 0);
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (layout != null) {
			// Check 3D structure viewport zoom or quantity selector hover wheel stepping first.
			for (OguiLayoutEngine.PositionedWidget pw : layout.widgets()) {
				if (!pw.widget().enabled()) {
					continue;
				}
				if (pw.scrollable() && !layout.scrollViewportRect().contains(mouseX, mouseY)) {
					continue;
				}
				int yOff = pw.scrollable() ? scrollOffset : 0;
				OguiLayoutEngine.Rect r = new OguiLayoutEngine.Rect(
					pw.bounds().x(), pw.bounds().y() - yOff, pw.bounds().width(), pw.bounds().height()
				);
				if (!r.contains(mouseX, mouseY)) {
					continue;
				}
				if (pw.widget() instanceof OguiWidgetDto.StructurePreviewDto sp) {
					StructureViewportState vs = viewportStates.computeIfAbsent(sp.widgetId(), ignored -> new StructureViewportState(sp));
					vs.zoom = Math.clamp(vs.zoom + (float) verticalAmount * 0.14F, 0.45F, 2.50F);
					return true;
				}
				if (pw.widget() instanceof OguiWidgetDto.QuantityDto q) {
					int step = q.stepPresets().isEmpty() ? 1 : q.stepPresets().getFirst();
					int delta = verticalAmount > 0 ? step : -step;
					int target = Math.clamp((long) q.value() + delta, q.min(), q.max());
					if (target != q.value()) {
						playClickSound();
						sendInteraction(q.widgetId(), OguiClickAction.CLIENT_INPUT, "", target);
					}
					return true;
				}
			}

			if (layout.maxScrollOffset() > 0 && layout.scrollViewportRect().contains(mouseX, mouseY)) {
				scrollOffset = Math.clamp(scrollOffset - (int) Math.round(verticalAmount * 18.0), 0, layout.maxScrollOffset());
				syncScrollableEditBoxes();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && getFocused() instanceof EditBox focusedBox) {
			for (EditBoxEntry entry : editBoxes.values()) {
				if (entry.editBox == focusedBox) {
					entry.debounceTicks = -1;
					playClickSound();
					sendInteraction(entry.dto.widgetId(), OguiClickAction.CLIENT_INPUT, focusedBox.getValue(), 0);
					return true;
				}
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		super.removed();
		if (!suppressCloseNotify) {
			interactionSink.onScreenClosed(sessionId);
		}
		suppressCloseNotify = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void sendInteraction(String widgetId, OguiClickAction action, String inputText, int numericValue) {
		interactionSink.sendInteraction(
			sessionId,
			revision,
			widgetId,
			action,
			inputText == null ? "" : inputText,
			numericValue
		);
	}

	private void playClickSound() {
		if (!preferences.uiSoundsEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, preferences.soundVolume()));
	}

	private boolean isAwaitingConfirm(OguiWidgetDto w) {
		String key = snapshot.pendingConfirmKey();
		if (key == null) {
			return false;
		}
		return switch (w) {
			case OguiWidgetDto.ButtonDto b -> b.confirmSpec() != null && key.equals(b.confirmSpec().targetKey());
			case OguiWidgetDto.ToggleDto t -> t.confirmSpec() != null && key.equals(t.confirmSpec().targetKey());
			default -> false;
		};
	}

	private List<Component> buildTooltip(OguiWidgetDto w, boolean awaitingConfirm) {
		List<Component> tip = new ArrayList<>();
		tip.add(w.label());
		if (w instanceof OguiWidgetDto.ButtonDto b && b.badge() != null) {
			tip.add(b.badge());
		} else if (w instanceof OguiWidgetDto.ToggleDto t) {
			tip.add(Component.literal("State: ").withStyle(ChatFormatting.GRAY).append(t.value() ? t.onLabel() : t.offLabel()));
		} else if (w instanceof OguiWidgetDto.CycleDto c) {
			tip.add(Component.literal("Selected: ").withStyle(ChatFormatting.GRAY).append(c.selectedOption().label()));
		} else if (w instanceof OguiWidgetDto.QuantityDto q) {
			tip.add(Component.literal("Quantity: " + q.value() + " (" + q.min() + ".." + q.max() + ")").withStyle(ChatFormatting.AQUA));
			if (q.formattedSummary() != null) {
				tip.add(q.formattedSummary());
			}
		}
		tip.addAll(w.lore());
		if (awaitingConfirm) {
			if (w instanceof OguiWidgetDto.ButtonDto b && b.confirmSpec() != null) {
				tip.add(b.confirmSpec().promptLine());
			} else if (w instanceof OguiWidgetDto.ToggleDto t && t.confirmSpec() != null) {
				tip.add(t.confirmSpec().promptLine());
			}
		}
		return tip;
	}

	private static OguiClickAction mapMouseAction(MouseButtonEvent event) {
		boolean shift = event.hasShiftDown();
		if (event.button() == 1) {
			return shift ? OguiClickAction.SHIFT_RIGHT : OguiClickAction.RIGHT;
		}
		return shift ? OguiClickAction.SHIFT_LEFT : OguiClickAction.LEFT;
	}

	private String trimToWidth(String raw, int maxWidth) {
		if (maxWidth <= 8 || this.font.width(raw) <= maxWidth) {
			return raw;
		}
		String ellipsis = "..";
		int target = Math.max(4, maxWidth - this.font.width(ellipsis));
		return this.font.plainSubstrByWidth(raw, target) + ellipsis;
	}

	private static int withFullAlpha(int argb) {
		return 0xFF000000 | (argb & 0x00FFFFFF);
	}

	private static int blendWithAlpha(int rgb, int alpha) {
		return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
	}

	private static int darkenTint(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.round(((argb >>> 16) & 0xFF) * 0.78F);
		int g = Math.round(((argb >>> 8) & 0xFF) * 0.78F);
		int b = Math.round((argb & 0xFF) * 0.78F);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
