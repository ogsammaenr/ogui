package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.network.OguiTabDto;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import me.ogsammaenr.ogui.runtime.OguiSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, deterministic responsive pixel-layout calculator for all four {@link LayoutArchetype}s.
 * Contains zero OpenGL or Minecraft screen dependencies so layout math is fully unit-testable.
 */
public final class OguiLayoutEngine {
	public record Rect(int x, int y, int width, int height) {
		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(double mx, double my) {
			return mx >= x && mx < right() && my >= y && my < bottom();
		}
	}

	public enum Region {
		HEADER,
		ENTRY,
		FOOTER,
		HERO,
		BADGE,
		FORM_CONTROL,
		ACTION,
		FIXED_SLOT
	}

	public record PositionedTab(OguiTabDto tab, Rect bounds, boolean active) {
	}

	public record PositionedSummary(OguiSummaryCard card, Rect bounds) {
	}

	public record PositionedQuantityStep(String label, int targetValue, Rect bounds, boolean positive) {
	}

	public record PositionedWidget(
		OguiWidgetDto widget,
		Region region,
		Rect bounds,
		boolean scrollable,
		List<PositionedQuantityStep> quantitySteps
	) {
	}

	public record PositionedNav(String actionId, String labelKey, Rect bounds, boolean enabled) {
	}

	public record ComputedLayout(
		Rect panelRect,
		Rect headerRect,
		Rect bodyRect,
		Rect scrollViewportRect,
		Rect footerRect,
		Rect prefButtonRect,
		List<PositionedTab> tabs,
		List<PositionedSummary> summaries,
		List<PositionedWidget> widgets,
		List<PositionedNav> navButtons,
		int maxScrollOffset
	) {
	}

	private OguiLayoutEngine() {
	}

	public static ComputedLayout compute(
		ScreenSnapshotDto snap,
		int screenWidth,
		int screenHeight,
		OguiClientPreferences.UiScaleMode scaleMode
	) {
		float scale = scaleMode.scaleFactor(screenWidth);
		int pad = 10;

		int panelW;
		int panelH;
		if (snap.archetype() == LayoutArchetype.FIXED_GRID) {
			int tile = Math.clamp(Math.round(30 * scale), 24, 36);
			int gap = 4;
			int gridW = 9 * tile + 8 * gap;
			int gridH = snap.fixedRows() * tile + Math.max(0, snap.fixedRows() - 1) * gap;
			int baseHeaderH = snap.theme().subtitle() != null ? 38 : 28;
			panelW = Math.min(Math.max(240, screenWidth - 16), gridW + pad * 2 + 8);
			panelH = Math.min(Math.max(120, screenHeight - 16), baseHeaderH + gridH + 34 + pad);
		} else {
			panelW = Math.clamp(Math.round(448 * scale), 300, Math.max(300, screenWidth - 20));
			panelH = Math.clamp(Math.round(272 * scale), 204, Math.max(204, screenHeight - 20));
		}

		int panelX = Math.max(4, (screenWidth - panelW) / 2);
		int panelY = Math.max(4, (screenHeight - panelH) / 2);
		Rect panelRect = new Rect(panelX, panelY, panelW, panelH);

		int headerBaseH = snap.theme().subtitle() != null ? 38 : 28;
		boolean hasTabs = snap.archetype() == LayoutArchetype.TABBED_PANEL && !snap.tabs().isEmpty();
		int tabBarH = hasTabs ? 24 : 0;
		int headerH = headerBaseH + tabBarH;
		Rect headerRect = new Rect(panelX, panelY, panelW, headerH);
		Rect prefButtonRect = new Rect(panelRect.right() - 26, panelY + 5, 18, 18);

		List<PositionedTab> tabs = new ArrayList<>();
		if (hasTabs) {
			int tabCount = snap.tabs().size();
			int availW = panelW - pad * 2;
			int gap = 4;
			int tabW = Math.max(46, Math.min(96, (availW - gap * (tabCount - 1)) / tabCount));
			int cursorX = panelX + pad;
			int tabY = panelY + headerBaseH + 1;
			for (OguiTabDto tab : snap.tabs()) {
				if (cursorX + 36 > panelRect.right() - pad) {
					break;
				}
				int w = Math.min(tabW, panelRect.right() - pad - cursorX);
				boolean active = tab.id().equals(snap.activeTabId());
				tabs.add(new PositionedTab(tab, new Rect(cursorX, tabY, w, 20), active));
				cursorX += w + gap;
			}
		}

		int footerH = 30;
		Rect footerRect = new Rect(panelX, panelRect.bottom() - footerH, panelW, footerH);
		Rect bodyRect = new Rect(panelX + pad, headerRect.bottom() + 6, panelW - pad * 2, Math.max(40, footerRect.y() - headerRect.bottom() - 10));

		List<PositionedSummary> summaries = new ArrayList<>();
		List<PositionedWidget> widgets = new ArrayList<>();
		List<PositionedNav> navButtons = new ArrayList<>();
		Rect scrollViewportRect = bodyRect;
		int maxScrollOffset = 0;

		switch (snap.archetype()) {
			case TABBED_PANEL, PAGED_GRID -> {
				Rect workRect = bodyRect;
				if (!snap.theme().summaryCards().isEmpty() && bodyRect.width() >= 360) {
					int sidebarW = Math.min(132, Math.max(106, bodyRect.width() / 3));
					Rect sidebarRect = new Rect(bodyRect.right() - sidebarW, bodyRect.y(), sidebarW, bodyRect.height());
					workRect = new Rect(bodyRect.x(), bodyRect.y(), bodyRect.width() - sidebarW - 8, bodyRect.height());
					layoutSummaries(snap.theme().summaryCards(), sidebarRect, summaries);
				}

				int toolbarH = 0;
				if (!snap.headerWidgets().isEmpty()) {
					toolbarH = 26;
					layoutToolbar(snap.headerWidgets(), new Rect(workRect.x(), workRect.y(), workRect.width(), 22), Region.HEADER, widgets);
				}

				scrollViewportRect = new Rect(
					workRect.x(),
					workRect.y() + toolbarH,
					workRect.width(),
					Math.max(24, workRect.height() - toolbarH)
				);

				if (snap.entries().isEmpty() && snap.emptyState() != null) {
					int ew = Math.min(200, scrollViewportRect.width() - 16);
					int eh = 42;
					int ex = scrollViewportRect.x() + (scrollViewportRect.width() - ew) / 2;
					int ey = scrollViewportRect.y() + Math.max(4, (scrollViewportRect.height() - eh) / 2);
					widgets.add(new PositionedWidget(snap.emptyState(), Region.ENTRY, new Rect(ex, ey, ew, eh), false, List.of()));
				} else if (!snap.entries().isEmpty()) {
					boolean galleryMode = hasGalleryImages(snap.entries());
					int cols = galleryMode
						? (scrollViewportRect.width() >= 340 ? 3 : 2)
						: (scrollViewportRect.width() >= 310 ? 3 : 2);
					int gap = galleryMode ? 6 : 5;
					int cardW = (scrollViewportRect.width() - gap * (cols - 1)) / cols;
					int cardH = galleryMode ? 90 : 34;
					int totalRows = (snap.entries().size() + cols - 1) / cols;
					for (int i = 0; i < snap.entries().size(); i++) {
						int r = i / cols;
						int c = i % cols;
						int cx = scrollViewportRect.x() + c * (cardW + gap);
						int cy = scrollViewportRect.y() + r * (cardH + gap);
						widgets.add(new PositionedWidget(snap.entries().get(i), Region.ENTRY, new Rect(cx, cy, cardW, cardH), true, List.of()));
					}
					int contentH = totalRows * cardH + Math.max(0, totalRows - 1) * gap;
					maxScrollOffset = Math.max(0, contentH - scrollViewportRect.height());
				}

				layoutPagedFooter(snap, footerRect, pad, widgets, navButtons);
			}
			case DETAIL_FORM -> {
				boolean hasStructureHero = snap.heroCard() instanceof OguiWidgetDto.StructurePreviewDto;
				float leftShare = hasStructureHero ? 0.56f : 0.37f;
				int leftW = Math.max(hasStructureHero ? 150 : 106, Math.round(bodyRect.width() * leftShare));
				Rect leftPane = new Rect(bodyRect.x(), bodyRect.y(), leftW, bodyRect.height());
				Rect rightPane = new Rect(leftPane.right() + 8, bodyRect.y(), bodyRect.width() - leftW - 8, bodyRect.height());
				scrollViewportRect = rightPane;

				int ly = leftPane.y();
				if (snap.heroCard() != null) {
					int heroH;
					if (hasStructureHero) {
						int badgeReserve = Math.min(2, snap.infoBadges().size()) * 25;
						int summaryReserve = snap.theme().summaryCards().isEmpty() ? 0 : 48;
						int minBottom = badgeReserve + summaryReserve;
						heroH = Math.clamp(leftPane.height() - minBottom, 92, leftPane.height());
					} else {
						heroH = Math.min(64, Math.max(44, leftPane.height() / 3));
					}
					widgets.add(new PositionedWidget(snap.heroCard(), Region.HERO, new Rect(leftPane.x(), ly, leftPane.width(), heroH), false, List.of()));
					ly += heroH + 5;
				}
				for (OguiWidgetDto badge : snap.infoBadges()) {
					if (ly + 20 > leftPane.bottom()) {
						break;
					}
					widgets.add(new PositionedWidget(badge, Region.BADGE, new Rect(leftPane.x(), ly, leftPane.width(), 22), false, List.of()));
					ly += 25;
				}
				if (!snap.theme().summaryCards().isEmpty() && ly + 36 <= leftPane.bottom()) {
					layoutSummaries(snap.theme().summaryCards(), new Rect(leftPane.x(), ly, leftPane.width(), leftPane.bottom() - ly), summaries);
				}

				int ry = rightPane.y();
				for (OguiWidgetDto control : snap.formControls()) {
					int ch = switch (control) {
						case OguiWidgetDto.QuantityDto ignored -> 46;
						case OguiWidgetDto.TextInputDto ignored -> 40;
						default -> 30;
					};
					Rect cBounds = new Rect(rightPane.x(), ry, rightPane.width(), ch);
					List<PositionedQuantityStep> steps = control instanceof OguiWidgetDto.QuantityDto q
						? computeQuantitySteps(q, cBounds)
						: List.of();
					widgets.add(new PositionedWidget(control, Region.FORM_CONTROL, cBounds, true, steps));
					ry += ch + 6;
				}
				int totalFormH = Math.max(0, ry - rightPane.y() - 6);
				maxScrollOffset = Math.max(0, totalFormH - rightPane.height());

				layoutDetailFooter(snap, footerRect, pad, widgets, navButtons);
			}
			case FIXED_GRID -> {
				int rows = snap.fixedRows();
				int gap = 4;
				int maxTileW = (bodyRect.width() - gap * 8) / 9;
				int maxTileH = (bodyRect.height() - gap * Math.max(0, rows - 1)) / rows;
				int tile = Math.clamp(Math.min(maxTileW, maxTileH), 20, 36);
				int gridW = 9 * tile + 8 * gap;
				int gridH = rows * tile + Math.max(0, rows - 1) * gap;
				int startX = bodyRect.x() + Math.max(0, (bodyRect.width() - gridW) / 2);
				int startY = bodyRect.y() + Math.max(0, (bodyRect.height() - gridH) / 2);

				for (Map.Entry<Integer, OguiWidgetDto> entry : snap.slottedWidgets().entrySet()) {
					int slot = entry.getKey();
					if (slot < 0 || slot >= rows * 9) {
						continue;
					}
					int r = slot / 9;
					int c = slot % 9;
					Rect slotRect = new Rect(startX + c * (tile + gap), startY + r * (tile + gap), tile, tile);
					widgets.add(new PositionedWidget(entry.getValue(), Region.FIXED_SLOT, slotRect, false, List.of()));
				}
				layoutFixedFooter(snap, footerRect, pad, navButtons);
			}
		}

		return new ComputedLayout(
			panelRect, headerRect, bodyRect, scrollViewportRect, footerRect, prefButtonRect,
			List.copyOf(tabs), List.copyOf(summaries), List.copyOf(widgets), List.copyOf(navButtons),
			maxScrollOffset
		);
	}

	public static boolean isGalleryGrid(ScreenSnapshotDto snap) {
		return (snap.archetype() == LayoutArchetype.TABBED_PANEL || snap.archetype() == LayoutArchetype.PAGED_GRID)
			&& hasGalleryImages(snap.entries());
	}

	private static boolean hasGalleryImages(List<OguiWidgetDto> entries) {
		for (OguiWidgetDto widget : entries) {
			if (widget instanceof OguiWidgetDto.ButtonDto button && button.previewImage() != null) {
				return true;
			}
		}
		return false;
	}

	private static List<PositionedQuantityStep> computeQuantitySteps(OguiWidgetDto.QuantityDto q, Rect bounds) {
		List<PositionedQuantityStep> list = new ArrayList<>();
		List<Integer> presets = q.stepPresets();
		int count = Math.min(3, presets.size());
		int pillCount = count * 2;
		if (pillCount == 0) {
			return list;
		}
		int gap = 3;
		int rowX = bounds.x() + 6;
		int rowW = bounds.width() - 12;
		int pillW = Math.max(22, (rowW - gap * (pillCount - 1)) / pillCount);
		int pillY = bounds.y() + 23;
		int pillH = 17;

		int cx = rowX;
		for (int i = count - 1; i >= 0; i--) {
			int step = presets.get(i);
			int target = Math.clamp((long) q.value() - step, q.min(), q.max());
			list.add(new PositionedQuantityStep("-" + step, target, new Rect(cx, pillY, pillW, pillH), false));
			cx += pillW + gap;
		}
		for (int i = 0; i < count; i++) {
			int step = presets.get(i);
			int target = Math.clamp((long) q.value() + step, q.min(), q.max());
			list.add(new PositionedQuantityStep("+" + step, target, new Rect(cx, pillY, pillW, pillH), true));
			cx += pillW + gap;
		}
		return List.copyOf(list);
	}

	private static void layoutSummaries(
		List<OguiSummaryCard> cards,
		Rect area,
		List<PositionedSummary> out
	) {
		int y = area.y();
		for (OguiSummaryCard card : cards) {
			int linesH = Math.min(6, card.lines().size()) * 11;
			int h = Math.min(area.bottom() - y, 26 + linesH);
			if (h < 24) {
				break;
			}
			out.add(new PositionedSummary(card, new Rect(area.x(), y, area.width(), h)));
			y += h + 6;
		}
	}

	private static void layoutToolbar(
		List<OguiWidgetDto> widgets,
		Rect toolbarRect,
		Region region,
		List<PositionedWidget> out
	) {
		int gap = 5;
		int x = toolbarRect.x();
		for (OguiWidgetDto widget : widgets) {
			int remaining = toolbarRect.right() - x;
			if (remaining < 48) {
				break;
			}
			int preferredW = widget instanceof OguiWidgetDto.TextInputDto ? 132 : 92;
			int w = Math.min(preferredW, remaining);
			out.add(new PositionedWidget(widget, region, new Rect(x, toolbarRect.y(), w, toolbarRect.height()), false, List.of()));
			x += w + gap;
		}
	}

	private static void layoutPagedFooter(
		ScreenSnapshotDto snap,
		Rect footerRect,
		int pad,
		List<PositionedWidget> widgets,
		List<PositionedNav> navButtons
	) {
		int y = footerRect.y() + 5;
		int leftX = footerRect.x() + pad;
		navButtons.add(new PositionedNav(
			snap.hasBack() ? OguiSession.NAV_BACK : OguiSession.NAV_CLOSE,
			snap.hasBack() ? "ogui.screen.back" : "ogui.screen.close",
			new Rect(leftX, y, 52, 20),
			true
		));
		leftX += 56;

		int rightX = footerRect.right() - pad;
		navButtons.add(new PositionedNav(OguiSession.NAV_REFRESH, "ogui.screen.refresh", new Rect(rightX - 54, y, 54, 20), true));
		rightX -= 58;

		int nextW = 48;
		int prevW = 52;
		int pageLabelW = 46;
		int pagerTotalW = prevW + 4 + pageLabelW + 4 + nextW;
		int pagerX = Math.max(leftX, footerRect.x() + (footerRect.width() - pagerTotalW) / 2);

		navButtons.add(new PositionedNav(OguiSession.NAV_PREV, "ogui.screen.prev_page", new Rect(pagerX, y, prevW, 20), snap.hasPreviousPage()));
		navButtons.add(new PositionedNav(OguiSession.NAV_NEXT, "ogui.screen.next_page", new Rect(pagerX + prevW + 4 + pageLabelW + 4, y, nextW, 20), snap.hasNextPage()));

		int extraAvail = Math.min(pagerX - 4, rightX - pagerTotalW) - leftX;
		if (!snap.footerWidgets().isEmpty() && extraAvail >= 60) {
			layoutToolbar(snap.footerWidgets(), new Rect(leftX, y, pagerX - 6 - leftX, 20), Region.FOOTER, widgets);
		}
	}

	private static void layoutDetailFooter(
		ScreenSnapshotDto snap,
		Rect footerRect,
		int pad,
		List<PositionedWidget> widgets,
		List<PositionedNav> navButtons
	) {
		int y = footerRect.y() + 5;
		int leftX = footerRect.x() + pad;
		navButtons.add(new PositionedNav(
			snap.hasBack() ? OguiSession.NAV_BACK : OguiSession.NAV_CLOSE,
			snap.hasBack() ? "ogui.screen.back" : "ogui.screen.close",
			new Rect(leftX, y, 52, 20),
			true
		));

		int rightX = footerRect.right() - pad;
		navButtons.add(new PositionedNav(OguiSession.NAV_REFRESH, "ogui.screen.refresh", new Rect(rightX - 54, y, 54, 20), true));

		int availStart = leftX + 58;
		int availEnd = rightX - 60;
		List<OguiWidgetDto> actions = snap.actionButtons();
		if (!actions.isEmpty() && availEnd - availStart >= 64) {
			int count = Math.min(actions.size(), 3);
			int gap = 6;
			int maxPerBtn = count == 1 ? 168 : (count == 2 ? 128 : 112);
			int btnW = Math.min(maxPerBtn, (availEnd - availStart - gap * (count - 1)) / count);
			int totalW = count * btnW + (count - 1) * gap;
			int ax = availStart + Math.max(0, (availEnd - availStart - totalW) / 2);
			for (int i = 0; i < count; i++) {
				widgets.add(new PositionedWidget(actions.get(i), Region.ACTION, new Rect(ax, y, btnW, 20), false, List.of()));
				ax += btnW + gap;
			}
		}
	}

	private static void layoutFixedFooter(
		ScreenSnapshotDto snap,
		Rect footerRect,
		int pad,
		List<PositionedNav> navButtons
	) {
		int y = footerRect.y() + 5;
		navButtons.add(new PositionedNav(
			snap.hasBack() ? OguiSession.NAV_BACK : OguiSession.NAV_CLOSE,
			snap.hasBack() ? "ogui.screen.back" : "ogui.screen.close",
			new Rect(footerRect.x() + pad, y, 52, 20),
			true
		));
		navButtons.add(new PositionedNav(
			OguiSession.NAV_REFRESH,
			"ogui.screen.refresh",
			new Rect(footerRect.right() - pad - 54, y, 54, 20),
			true
		));
	}
}
