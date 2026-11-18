package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable wire snapshot of an active server-side screen sent to {@code ogui-client}.
 */
public record ScreenSnapshotDto(
	Identifier screenId,
	@Nullable String contextKey,
	Component title,
	LayoutArchetype archetype,
	OguiTheme theme,
	List<OguiTabDto> tabs,
	@Nullable String activeTabId,
	int page,
	int totalPages,
	int totalEntries,
	boolean hasBack,
	List<OguiWidgetDto> headerWidgets,
	List<OguiWidgetDto> entries,
	List<OguiWidgetDto> footerWidgets,
	@Nullable OguiWidgetDto heroCard,
	List<OguiWidgetDto> infoBadges,
	List<OguiWidgetDto> formControls,
	List<OguiWidgetDto> actionButtons,
	int fixedRows,
	Map<Integer, OguiWidgetDto> slottedWidgets,
	@Nullable OguiWidgetDto emptyState,
	@Nullable String pendingConfirmKey
) {
	public ScreenSnapshotDto {
		Objects.requireNonNull(screenId, "screenId");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(archetype, "archetype");
		theme = theme == null ? OguiTheme.DEFAULT : theme;
		tabs = tabs == null ? List.of() : List.copyOf(tabs);
		totalPages = Math.max(1, totalPages);
		page = Math.clamp(page, 0, totalPages - 1);
		totalEntries = Math.max(0, totalEntries);
		headerWidgets = headerWidgets == null ? List.of() : List.copyOf(headerWidgets);
		entries = entries == null ? List.of() : List.copyOf(entries);
		footerWidgets = footerWidgets == null ? List.of() : List.copyOf(footerWidgets);
		infoBadges = infoBadges == null ? List.of() : List.copyOf(infoBadges);
		formControls = formControls == null ? List.of() : List.copyOf(formControls);
		actionButtons = actionButtons == null ? List.of() : List.copyOf(actionButtons);
		fixedRows = Math.clamp(fixedRows, 1, 6);
		slottedWidgets = slottedWidgets == null ? Map.of() : Map.copyOf(slottedWidgets);
	}

	public boolean hasPreviousPage() {
		return page > 0;
	}

	public boolean hasNextPage() {
		return page + 1 < totalPages;
	}
}
