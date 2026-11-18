package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * UI-agnostic declarative screen model built by an {@link OguiScreenProvider}.
 */
public record OguiScreenModel(
	Component title,
	LayoutArchetype archetype,
	OguiTheme theme,
	List<OguiTab> tabs,
	@Nullable String activeTabId,
	List<OguiWidget> headerWidgets,
	List<OguiWidget> entries,
	List<OguiWidget> footerWidgets,
	@Nullable OguiWidget heroCard,
	List<OguiWidget> infoBadges,
	List<OguiWidget> formControls,
	List<OguiWidget> actionButtons,
	int fixedRows,
	Map<Integer, OguiWidget> slottedWidgets,
	@Nullable OguiWidget emptyState,
	Map<String, Integer> vanillaSlotHints,
	@Nullable Component backButtonLabel
) {
	public static final int DEFAULT_PAGE_SIZE = 28;
	public static final int MIN_FIXED_ROWS = 1;
	public static final int MAX_FIXED_ROWS = 6;
	public static final int MAX_CHEST_SLOTS = MAX_FIXED_ROWS * 9;

	public OguiScreenModel(
		Component title,
		LayoutArchetype archetype,
		OguiTheme theme,
		List<OguiTab> tabs,
		@Nullable String activeTabId,
		List<OguiWidget> headerWidgets,
		List<OguiWidget> entries,
		List<OguiWidget> footerWidgets,
		@Nullable OguiWidget heroCard,
		List<OguiWidget> infoBadges,
		List<OguiWidget> formControls,
		List<OguiWidget> actionButtons,
		int fixedRows,
		Map<Integer, OguiWidget> slottedWidgets,
		@Nullable OguiWidget emptyState
	) {
		this(
			title, archetype, theme, tabs, activeTabId,
			headerWidgets, entries, footerWidgets,
			heroCard, infoBadges, formControls, actionButtons,
			fixedRows, slottedWidgets, emptyState,
			Map.of(), null
		);
	}

	public OguiScreenModel {
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(archetype, "archetype");
		theme = theme == null ? OguiTheme.DEFAULT : theme;
		tabs = tabs == null ? List.of() : List.copyOf(tabs);
		headerWidgets = headerWidgets == null ? List.of() : List.copyOf(headerWidgets);
		entries = entries == null ? List.of() : List.copyOf(entries);
		footerWidgets = footerWidgets == null ? List.of() : List.copyOf(footerWidgets);
		infoBadges = infoBadges == null ? List.of() : List.copyOf(infoBadges);
		formControls = formControls == null ? List.of() : List.copyOf(formControls);
		actionButtons = actionButtons == null ? List.of() : List.copyOf(actionButtons);
		fixedRows = archetype == LayoutArchetype.FIXED_GRID
			? Math.clamp(fixedRows, MIN_FIXED_ROWS, MAX_FIXED_ROWS)
			: MAX_FIXED_ROWS;
		if (slottedWidgets == null || slottedWidgets.isEmpty()) {
			slottedWidgets = Map.of();
		} else {
			int maxSlot = fixedRows * 9;
			for (Integer slot : slottedWidgets.keySet()) {
				if (slot == null || slot < 0 || slot >= maxSlot) {
					throw new IllegalArgumentException(
						"Fixed slot index " + slot + " is out of bounds (0.." + (maxSlot - 1) + ")"
					);
				}
			}
			slottedWidgets = Map.copyOf(slottedWidgets);
		}
		if (vanillaSlotHints == null || vanillaSlotHints.isEmpty()) {
			vanillaSlotHints = Map.of();
		} else {
			for (Map.Entry<String, Integer> entry : vanillaSlotHints.entrySet()) {
				Integer slot = entry.getValue();
				if (entry.getKey() == null || slot == null || slot < 0 || slot >= MAX_CHEST_SLOTS) {
					throw new IllegalArgumentException(
						"Vanilla slot hint for '" + entry.getKey() + "' is out of bounds (0.." + (MAX_CHEST_SLOTS - 1) + "): " + slot
					);
				}
			}
			vanillaSlotHints = Map.copyOf(vanillaSlotHints);
		}
	}

	public static TabbedPanelBuilder tabbedPanel(Component title) {
		return new TabbedPanelBuilder(title);
	}

	public static TabbedPanelBuilder tabbedPanel(String title) {
		return tabbedPanel(OguiText.nonItalic(title));
	}

	public static PagedGridBuilder pagedGrid(Component title) {
		return new PagedGridBuilder(title);
	}

	public static PagedGridBuilder pagedGrid(String title) {
		return pagedGrid(OguiText.nonItalic(title));
	}

	public static DetailFormBuilder detailForm(Component title) {
		return new DetailFormBuilder(title);
	}

	public static DetailFormBuilder detailForm(String title) {
		return detailForm(OguiText.nonItalic(title));
	}

	public static FixedGridBuilder fixedGrid(Component title, int rows) {
		return new FixedGridBuilder(title, rows);
	}

	public static FixedGridBuilder fixedGrid(String title, int rows) {
		return fixedGrid(OguiText.nonItalic(title), rows);
	}

	public static final class TabbedPanelBuilder {
		private final Component title;
		private OguiTheme theme = OguiTheme.DEFAULT;
		private final List<OguiTab> tabs = new ArrayList<>();
		private @Nullable String activeTabId;
		private final List<OguiWidget> headerWidgets = new ArrayList<>();
		private final List<OguiWidget> entries = new ArrayList<>();
		private final List<OguiWidget> footerWidgets = new ArrayList<>();
		private @Nullable OguiWidget emptyState;
		private final Map<String, Integer> vanillaSlotHints = new LinkedHashMap<>();
		private @Nullable Component backButtonLabel;

		private TabbedPanelBuilder(Component title) {
			this.title = Objects.requireNonNull(title, "title");
		}

		public TabbedPanelBuilder theme(OguiTheme theme) {
			this.theme = Objects.requireNonNull(theme, "theme");
			return this;
		}

		public TabbedPanelBuilder addTab(OguiTab tab) {
			this.tabs.add(Objects.requireNonNull(tab, "tab"));
			return this;
		}

		public TabbedPanelBuilder activeTabId(@Nullable String activeTabId) {
			this.activeTabId = activeTabId;
			return this;
		}

		public TabbedPanelBuilder addHeaderWidget(OguiWidget widget) {
			this.headerWidgets.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public TabbedPanelBuilder addHeaderWidget(OguiWidget widget, int preferredVanillaSlot) {
			Objects.requireNonNull(widget, "widget");
			this.headerWidgets.add(widget);
			this.vanillaSlotHints.put(widget.widgetId(), preferredVanillaSlot);
			return this;
		}

		public TabbedPanelBuilder addEntry(OguiWidget widget) {
			this.entries.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public TabbedPanelBuilder addEntries(List<? extends OguiWidget> widgets) {
			Objects.requireNonNull(widgets, "widgets");
			for (OguiWidget widget : widgets) {
				this.entries.add(Objects.requireNonNull(widget, "widget"));
			}
			return this;
		}

		public TabbedPanelBuilder addFooterWidget(OguiWidget widget) {
			this.footerWidgets.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public TabbedPanelBuilder addFooterWidget(OguiWidget widget, int preferredVanillaSlot) {
			Objects.requireNonNull(widget, "widget");
			this.footerWidgets.add(widget);
			this.vanillaSlotHints.put(widget.widgetId(), preferredVanillaSlot);
			return this;
		}

		public TabbedPanelBuilder vanillaSlotHint(String widgetId, int preferredVanillaSlot) {
			Objects.requireNonNull(widgetId, "widgetId");
			this.vanillaSlotHints.put(widgetId, preferredVanillaSlot);
			return this;
		}

		public TabbedPanelBuilder backLabel(@Nullable Component backButtonLabel) {
			this.backButtonLabel = backButtonLabel;
			return this;
		}

		public TabbedPanelBuilder backLabel(@Nullable String backButtonLabel) {
			this.backButtonLabel = backButtonLabel == null
				? null
				: OguiText.nonItalic(backButtonLabel, net.minecraft.ChatFormatting.YELLOW);
			return this;
		}

		public TabbedPanelBuilder emptyState(@Nullable OguiWidget emptyState) {
			this.emptyState = emptyState;
			return this;
		}

		public OguiScreenModel build() {
			return new OguiScreenModel(
				title, LayoutArchetype.TABBED_PANEL, theme,
				tabs, activeTabId, headerWidgets, entries, footerWidgets,
				null, List.of(), List.of(), List.of(),
				MAX_FIXED_ROWS, Map.of(), emptyState,
				vanillaSlotHints, backButtonLabel
			);
		}
	}

	public static final class PagedGridBuilder {
		private final Component title;
		private OguiTheme theme = OguiTheme.DEFAULT;
		private final List<OguiWidget> headerWidgets = new ArrayList<>();
		private final List<OguiWidget> entries = new ArrayList<>();
		private final List<OguiWidget> footerWidgets = new ArrayList<>();
		private @Nullable OguiWidget emptyState;
		private final Map<String, Integer> vanillaSlotHints = new LinkedHashMap<>();
		private @Nullable Component backButtonLabel;

		private PagedGridBuilder(Component title) {
			this.title = Objects.requireNonNull(title, "title");
		}

		public PagedGridBuilder theme(OguiTheme theme) {
			this.theme = Objects.requireNonNull(theme, "theme");
			return this;
		}

		public PagedGridBuilder addHeaderWidget(OguiWidget widget) {
			this.headerWidgets.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public PagedGridBuilder addHeaderWidget(OguiWidget widget, int preferredVanillaSlot) {
			Objects.requireNonNull(widget, "widget");
			this.headerWidgets.add(widget);
			this.vanillaSlotHints.put(widget.widgetId(), preferredVanillaSlot);
			return this;
		}

		public PagedGridBuilder addEntry(OguiWidget widget) {
			this.entries.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public PagedGridBuilder addEntries(List<? extends OguiWidget> widgets) {
			Objects.requireNonNull(widgets, "widgets");
			for (OguiWidget widget : widgets) {
				this.entries.add(Objects.requireNonNull(widget, "widget"));
			}
			return this;
		}

		public PagedGridBuilder addFooterWidget(OguiWidget widget) {
			this.footerWidgets.add(Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public PagedGridBuilder addFooterWidget(OguiWidget widget, int preferredVanillaSlot) {
			Objects.requireNonNull(widget, "widget");
			this.footerWidgets.add(widget);
			this.vanillaSlotHints.put(widget.widgetId(), preferredVanillaSlot);
			return this;
		}

		public PagedGridBuilder vanillaSlotHint(String widgetId, int preferredVanillaSlot) {
			Objects.requireNonNull(widgetId, "widgetId");
			this.vanillaSlotHints.put(widgetId, preferredVanillaSlot);
			return this;
		}

		public PagedGridBuilder backLabel(@Nullable Component backButtonLabel) {
			this.backButtonLabel = backButtonLabel;
			return this;
		}

		public PagedGridBuilder backLabel(@Nullable String backButtonLabel) {
			this.backButtonLabel = backButtonLabel == null
				? null
				: OguiText.nonItalic(backButtonLabel, net.minecraft.ChatFormatting.YELLOW);
			return this;
		}

		public PagedGridBuilder emptyState(@Nullable OguiWidget emptyState) {
			this.emptyState = emptyState;
			return this;
		}

		public OguiScreenModel build() {
			return new OguiScreenModel(
				title, LayoutArchetype.PAGED_GRID, theme,
				List.of(), null, headerWidgets, entries, footerWidgets,
				null, List.of(), List.of(), List.of(),
				MAX_FIXED_ROWS, Map.of(), emptyState,
				vanillaSlotHints, backButtonLabel
			);
		}
	}

	public static final class DetailFormBuilder {
		private final Component title;
		private OguiTheme theme = OguiTheme.DEFAULT;
		private @Nullable OguiWidget heroCard;
		private final List<OguiWidget> infoBadges = new ArrayList<>();
		private final List<OguiWidget> formControls = new ArrayList<>();
		private final List<OguiWidget> actionButtons = new ArrayList<>();
		private @Nullable Component backButtonLabel;

		private DetailFormBuilder(Component title) {
			this.title = Objects.requireNonNull(title, "title");
		}

		public DetailFormBuilder theme(OguiTheme theme) {
			this.theme = Objects.requireNonNull(theme, "theme");
			return this;
		}

		public DetailFormBuilder heroCard(@Nullable OguiWidget heroCard) {
			this.heroCard = heroCard;
			return this;
		}

		public DetailFormBuilder addInfoBadge(OguiWidget badge) {
			this.infoBadges.add(Objects.requireNonNull(badge, "badge"));
			return this;
		}

		public DetailFormBuilder addControl(OguiWidget control) {
			this.formControls.add(Objects.requireNonNull(control, "control"));
			return this;
		}

		public DetailFormBuilder addAction(OguiWidget action) {
			this.actionButtons.add(Objects.requireNonNull(action, "action"));
			return this;
		}

		public DetailFormBuilder backLabel(@Nullable Component backButtonLabel) {
			this.backButtonLabel = backButtonLabel;
			return this;
		}

		public OguiScreenModel build() {
			return new OguiScreenModel(
				title, LayoutArchetype.DETAIL_FORM, theme,
				List.of(), null, List.of(), List.of(), List.of(),
				heroCard, infoBadges, formControls, actionButtons,
				MAX_FIXED_ROWS, Map.of(), null,
				Map.of(), backButtonLabel
			);
		}
	}

	public static final class FixedGridBuilder {
		private final Component title;
		private final int rows;
		private OguiTheme theme = OguiTheme.DEFAULT;
		private final Map<Integer, OguiWidget> slottedWidgets = new LinkedHashMap<>();

		private FixedGridBuilder(Component title, int rows) {
			this.title = Objects.requireNonNull(title, "title");
			this.rows = Math.clamp(rows, MIN_FIXED_ROWS, MAX_FIXED_ROWS);
		}

		public FixedGridBuilder theme(OguiTheme theme) {
			this.theme = Objects.requireNonNull(theme, "theme");
			return this;
		}

		public FixedGridBuilder slot(int slotIndex, OguiWidget widget) {
			this.slottedWidgets.put(slotIndex, Objects.requireNonNull(widget, "widget"));
			return this;
		}

		public OguiScreenModel build() {
			return new OguiScreenModel(
				title, LayoutArchetype.FIXED_GRID, theme,
				List.of(), null, List.of(), List.of(), List.of(),
				null, List.of(), List.of(), List.of(),
				rows, slottedWidgets, null,
				Map.of(), null
			);
		}
	}
}
