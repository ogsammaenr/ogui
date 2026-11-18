package me.ogsammaenr.ogui.runtime;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.OguiTabDto;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative active UI session stored in RAM while a player views an Ogui screen.
 */
public final class OguiSession {
	public static final String NAV_BACK = OguiWidget.RESERVED_ID_PREFIX + "back";
	public static final String NAV_CLOSE = OguiWidget.RESERVED_ID_PREFIX + "close";
	public static final String NAV_PREV = OguiWidget.RESERVED_ID_PREFIX + "prev";
	public static final String NAV_NEXT = OguiWidget.RESERVED_ID_PREFIX + "next";
	public static final String NAV_REFRESH = OguiWidget.RESERVED_ID_PREFIX + "refresh";
	public static final String NAV_TAB_PREFIX = OguiWidget.RESERVED_ID_PREFIX + "tab:";

	public record PendingConfirm(String targetKey, Instant expiresAt) {
		public boolean isExpired() {
			return Instant.now().isAfter(expiresAt);
		}
	}

	private final UUID sessionId;
	private final UUID playerUuid;
	private final boolean clientModMode;
	private OguiOpenRequest request;
	private OguiScreenModel model;
	private int page;
	private int totalPages;
	private List<OguiWidget> pagedSlice;
	private int revision;
	private Map<String, OguiWidget> widgetIndex;
	private Map<String, OguiTab> tabIndex;
	private @Nullable PendingConfirm pendingConfirm;

	public OguiSession(
		UUID sessionId,
		UUID playerUuid,
		boolean clientModMode,
		OguiOpenRequest request,
		OguiScreenModel model
	) {
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
		this.clientModMode = clientModMode;
		this.revision = 1;
		updateView(request, model, false);
	}

	public void updateView(OguiOpenRequest nextRequest, OguiScreenModel nextModel, boolean incrementRevision) {
		Objects.requireNonNull(nextRequest, "nextRequest");
		Objects.requireNonNull(nextModel, "nextModel");
		this.model = nextModel;

		boolean paged = nextModel.archetype() == LayoutArchetype.TABBED_PANEL
			|| nextModel.archetype() == LayoutArchetype.PAGED_GRID;
		if (paged) {
			List<OguiWidget> all = nextModel.entries();
			int pageSize = OguiScreenModel.DEFAULT_PAGE_SIZE;
			this.totalPages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
			this.page = Math.clamp(nextRequest.page(), 0, this.totalPages - 1);
			int from = Math.min(this.page * pageSize, all.size());
			int to = Math.min(from + pageSize, all.size());
			this.pagedSlice = List.copyOf(all.subList(from, to));
			this.request = nextRequest.withPage(this.page);
		} else {
			this.totalPages = 1;
			this.page = 0;
			this.pagedSlice = List.of();
			this.request = nextRequest.withPage(0);
		}

		Map<String, OguiWidget> widgets = new LinkedHashMap<>();
		indexAll(widgets, nextModel.headerWidgets());
		indexAll(widgets, this.pagedSlice);
		indexAll(widgets, nextModel.footerWidgets());
		if (nextModel.heroCard() != null) {
			widgets.put(nextModel.heroCard().widgetId(), nextModel.heroCard());
		}
		indexAll(widgets, nextModel.infoBadges());
		indexAll(widgets, nextModel.formControls());
		indexAll(widgets, nextModel.actionButtons());
		indexAll(widgets, nextModel.slottedWidgets().values());
		if (nextModel.emptyState() != null) {
			widgets.put(nextModel.emptyState().widgetId(), nextModel.emptyState());
		}
		this.widgetIndex = Map.copyOf(widgets);

		Map<String, OguiTab> tabs = new LinkedHashMap<>();
		for (OguiTab tab : nextModel.tabs()) {
			tabs.put(tab.id(), tab);
		}
		this.tabIndex = Map.copyOf(tabs);

		if (incrementRevision) {
			this.revision++;
		}
	}

	private static void indexAll(Map<String, OguiWidget> map, Iterable<OguiWidget> widgets) {
		for (OguiWidget widget : widgets) {
			map.put(widget.widgetId(), widget);
		}
	}

	public UUID sessionId() {
		return sessionId;
	}

	public UUID playerUuid() {
		return playerUuid;
	}

	public boolean clientModMode() {
		return clientModMode;
	}

	public OguiOpenRequest request() {
		return request;
	}

	public OguiScreenModel model() {
		return model;
	}

	public int page() {
		return page;
	}

	public int totalPages() {
		return totalPages;
	}

	public boolean hasPreviousPage() {
		return page > 0;
	}

	public boolean hasNextPage() {
		return page + 1 < totalPages;
	}

	public List<OguiWidget> pagedSlice() {
		return pagedSlice;
	}

	public int revision() {
		return revision;
	}

	public @Nullable OguiWidget findWidget(String widgetId) {
		return widgetIndex.get(widgetId);
	}

	public @Nullable OguiTab findTab(String tabId) {
		return tabIndex.get(tabId);
	}

	public @Nullable String activeConfirmKey() {
		if (pendingConfirm == null) {
			return null;
		}
		if (pendingConfirm.isExpired()) {
			pendingConfirm = null;
			return null;
		}
		return pendingConfirm.targetKey();
	}

	public boolean checkOrArmConfirmation(@Nullable OguiConfirmSpec spec) {
		if (spec == null) {
			return true;
		}
		String active = activeConfirmKey();
		if (spec.targetKey().equals(active)) {
			pendingConfirm = null;
			return true;
		}
		pendingConfirm = new PendingConfirm(spec.targetKey(), Instant.now().plus(spec.ttl()));
		return false;
	}

	public void clearConfirmation() {
		this.pendingConfirm = null;
	}

	public ScreenSnapshotDto toDto() {
		List<OguiTabDto> tabDtos = model.tabs().stream()
			.map(t -> new OguiTabDto(t.id(), t.icon(), t.title(), t.lore(), t.enabled()))
			.toList();
		Map<Integer, OguiWidgetDto> slottedDtos = new LinkedHashMap<>();
		model.slottedWidgets().forEach((slot, widget) -> slottedDtos.put(slot, OguiWidgetDto.fromWidget(widget)));

		return new ScreenSnapshotDto(
			request.screenId(),
			request.contextKey(),
			model.title(),
			model.archetype(),
			model.theme(),
			tabDtos,
			model.activeTabId(),
			page,
			totalPages,
			model.entries().size(),
			request.backRequest() != null,
			model.headerWidgets().stream().map(OguiWidgetDto::fromWidget).toList(),
			pagedSlice.stream().map(OguiWidgetDto::fromWidget).toList(),
			model.footerWidgets().stream().map(OguiWidgetDto::fromWidget).toList(),
			model.heroCard() == null ? null : OguiWidgetDto.fromWidget(model.heroCard()),
			model.infoBadges().stream().map(OguiWidgetDto::fromWidget).toList(),
			model.formControls().stream().map(OguiWidgetDto::fromWidget).toList(),
			model.actionButtons().stream().map(OguiWidgetDto::fromWidget).toList(),
			model.fixedRows(),
			slottedDtos,
			model.emptyState() == null ? null : OguiWidgetDto.fromWidget(model.emptyState()),
			activeConfirmKey()
		);
	}
}
