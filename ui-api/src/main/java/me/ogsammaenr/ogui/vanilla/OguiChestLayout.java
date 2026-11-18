package me.ogsammaenr.ogui.vanilla;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.runtime.OguiSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure layout planner converting any {@link OguiSession} into vanilla {@link OguiChestMenu} slot bindings.
 */
public final class OguiChestLayout {
	public static final int SLOT_CLOSE_OR_BACK = 45;
	public static final int SLOT_PREV_PAGE = 48;
	public static final int SLOT_PAGE_INDICATOR = 49;
	public static final int SLOT_NEXT_PAGE = 50;
	public static final int SLOT_REFRESH = 53;
	private static final int[] FOOTER_WIDGET_SLOTS = {47, 46, 51, 52};

	public record SlotBinding(
		ItemStack icon,
		@Nullable String actionId,
		@Nullable OguiClickAction forcedAction,
		int forcedNumericValue
	) {
		public SlotBinding {
			Objects.requireNonNull(icon, "icon");
		}

		public static SlotBinding decorative(ItemStack icon) {
			return new SlotBinding(icon, null, null, 0);
		}

		public static SlotBinding action(ItemStack icon, String actionId) {
			return new SlotBinding(icon, actionId, null, 0);
		}

		public static SlotBinding numeric(ItemStack icon, String actionId, int value) {
			return new SlotBinding(icon, actionId, OguiClickAction.CLIENT_INPUT, value);
		}
	}

	public record PlannedChestView(int rows, Map<Integer, SlotBinding> slots) {
	}

	private OguiChestLayout() {
	}

	public static PlannedChestView plan(OguiSession session, @Nullable MinecraftServer server) {
		Objects.requireNonNull(session, "session");
		OguiScreenModel model = session.model();
		String confirmKey = session.activeConfirmKey();
		return switch (model.archetype()) {
			case TABBED_PANEL -> planTabbedPanel(session, model, confirmKey, server);
			case PAGED_GRID -> planPagedGrid(session, model, confirmKey, server);
			case DETAIL_FORM -> planDetailForm(session, model, confirmKey, server);
			case FIXED_GRID -> planFixedGrid(model, confirmKey, server);
		};
	}

	private static PlannedChestView planTabbedPanel(
		OguiSession session,
		OguiScreenModel model,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		Map<Integer, SlotBinding> slots = new LinkedHashMap<>();

		List<OguiTab> tabs = model.tabs();
		int maxTabs = Math.min(tabs.size(), 7);
		for (int i = 0; i < maxTabs; i++) {
			OguiTab tab = tabs.get(i);
			boolean active = tab.id().equals(model.activeTabId());
			ChatFormatting titleColor = active
				? ChatFormatting.AQUA
				: (tab.enabled() ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY);
			List<Component> lore = new ArrayList<>(tab.lore());
			if (active) {
				lore.add(OguiText.nonItalic("Currently Viewing", ChatFormatting.GREEN));
			} else if (tab.enabled()) {
				lore.add(OguiText.nonItalic("Click to open tab", ChatFormatting.GRAY));
			}
			ItemStack stack = OguiReadOnlyChestMenu.buildItem(
				tab.icon(),
				OguiText.nonItalic(tab.title().getString(), titleColor),
				lore,
				active,
				server
			);
			slots.put(i, tab.enabled()
				? SlotBinding.action(stack, OguiSession.NAV_TAB_PREFIX + tab.id())
				: SlotBinding.decorative(stack));
		}

		List<OguiWidget> barWidgets = new ArrayList<>(model.headerWidgets().size() + model.footerWidgets().size());
		barWidgets.addAll(model.headerWidgets());
		barWidgets.addAll(model.footerWidgets());
		List<OguiWidget> unpinnedBar = placeHintedWidgets(slots, barWidgets, model.vanillaSlotHints(), confirmKey, server);

		placeSummaryCards(slots, model.theme().summaryCards(), server);
		placePagedContent(slots, session, model, confirmKey, server);
		placePagedFooter(slots, session, model.backButtonLabel(), unpinnedBar, confirmKey, server);

		return new PlannedChestView(6, Map.copyOf(slots));
	}

	private static PlannedChestView planPagedGrid(
		OguiSession session,
		OguiScreenModel model,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		Map<Integer, SlotBinding> slots = new LinkedHashMap<>();

		List<OguiWidget> unpinnedHeaders = placeHintedWidgets(slots, model.headerWidgets(), model.vanillaSlotHints(), confirmKey, server);
		List<OguiWidget> unpinnedFooters = placeHintedWidgets(slots, model.footerWidgets(), model.vanillaSlotHints(), confirmKey, server);

		List<OguiSummaryCard> summaries = model.theme().summaryCards();
		placeSummaryCards(slots, summaries, server);
		int maxHeaderSlot = summaries.isEmpty() ? 9 : Math.max(0, 9 - Math.min(summaries.size(), 2));
		int headerCursor = 0;
		for (OguiWidget widget : unpinnedHeaders) {
			while (headerCursor < maxHeaderSlot && slots.containsKey(headerCursor)) {
				headerCursor++;
			}
			if (headerCursor >= maxHeaderSlot) {
				break;
			}
			slots.put(headerCursor, SlotBinding.action(renderWidgetItem(widget, confirmKey, server), widget.widgetId()));
			headerCursor++;
		}

		placePagedContent(slots, session, model, confirmKey, server);
		placePagedFooter(slots, session, model.backButtonLabel(), unpinnedFooters, confirmKey, server);

		return new PlannedChestView(6, Map.copyOf(slots));
	}

	private static List<OguiWidget> placeHintedWidgets(
		Map<Integer, SlotBinding> slots,
		List<OguiWidget> widgets,
		Map<String, Integer> hints,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		if (widgets.isEmpty() || hints.isEmpty()) {
			return widgets;
		}
		List<OguiWidget> remaining = new ArrayList<>();
		for (OguiWidget widget : widgets) {
			Integer hintedSlot = hints.get(widget.widgetId());
			if (hintedSlot != null && !slots.containsKey(hintedSlot)) {
				slots.put(hintedSlot, SlotBinding.action(renderWidgetItem(widget, confirmKey, server), widget.widgetId()));
			} else {
				remaining.add(widget);
			}
		}
		return remaining;
	}

	private static PlannedChestView planDetailForm(
		OguiSession session,
		OguiScreenModel model,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		List<OguiWidget.QuantitySelectorWidget> quantityControls = new ArrayList<>();
		List<OguiWidget> otherControls = new ArrayList<>();
		for (OguiWidget control : model.formControls()) {
			if (control instanceof OguiWidget.QuantitySelectorWidget q) {
				quantityControls.add(q);
			} else {
				otherControls.add(control);
			}
		}

		int otherRows = otherControls.isEmpty() ? 0 : (otherControls.size() + 6) / 7;
		int requiredRows = 2 + quantityControls.size() + otherRows;
		int rows = Math.clamp(Math.max(4, requiredRows), 4, 6);
		Map<Integer, SlotBinding> slots = new LinkedHashMap<>();

		// Row 0: Hero card (slot 4) + flanking info badges + optional summary card
		if (model.heroCard() != null) {
			OguiWidget hero = model.heroCard();
			slots.put(4, SlotBinding.action(renderWidgetItem(hero, confirmKey, server), hero.widgetId()));
		}
		int[] badgeSlots = {1, 2, 3, 5, 6, 7, 0};
		List<OguiWidget> badges = model.infoBadges();
		for (int i = 0; i < badges.size() && i < badgeSlots.length; i++) {
			OguiWidget badge = badges.get(i);
			slots.put(badgeSlots[i], SlotBinding.action(renderWidgetItem(badge, confirmKey, server), badge.widgetId()));
		}
		if (!model.theme().summaryCards().isEmpty() && !slots.containsKey(8)) {
			OguiSummaryCard card = model.theme().summaryCards().getFirst();
			slots.put(8, SlotBinding.decorative(renderSummaryItem(card, server)));
		}

		// Middle rows: QuantitySelector rows + packed non-quantity controls
		int cursorRow = 1;
		int maxMiddleRow = rows - 2;
		for (OguiWidget.QuantitySelectorWidget q : quantityControls) {
			if (cursorRow > maxMiddleRow) {
				break;
			}
			placeQuantityRow(slots, cursorRow, q, server);
			cursorRow++;
		}
		int otherIdx = 0;
		while (otherIdx < otherControls.size() && cursorRow <= maxMiddleRow) {
			int countInRow = Math.min(7, otherControls.size() - otherIdx);
			int startCol = 1 + (7 - countInRow) / 2;
			for (int c = 0; c < countInRow; c++) {
				OguiWidget w = otherControls.get(otherIdx++);
				int slot = cursorRow * 9 + startCol + c;
				slots.put(slot, SlotBinding.action(renderWidgetItem(w, confirmKey, server), w.widgetId()));
			}
			cursorRow++;
		}

		// Bottom row: Back/Close + Action buttons + Refresh
		int bottomStart = (rows - 1) * 9;
		boolean hasBack = session.request().backRequest() != null;
		Component backLabel = hasBack
			? (model.backButtonLabel() != null ? OguiText.nonItalic(model.backButtonLabel()) : OguiText.nonItalic("Back", ChatFormatting.YELLOW))
			: OguiText.nonItalic("Close", ChatFormatting.RED);
		slots.put(bottomStart, SlotBinding.action(
			buildNavItem(hasBack ? Items.OAK_DOOR : Items.BARRIER, backLabel,
				hasBack ? "Click to return" : "Click to close", server),
			hasBack ? OguiSession.NAV_BACK : OguiSession.NAV_CLOSE
		));

		List<OguiWidget> actions = model.actionButtons();
		int actionCount = Math.min(actions.size(), 7);
		int actionStartCol = 1 + (7 - actionCount) / 2;
		for (int i = 0; i < actionCount; i++) {
			OguiWidget action = actions.get(i);
			slots.put(bottomStart + actionStartCol + i,
				SlotBinding.action(renderWidgetItem(action, confirmKey, server), action.widgetId()));
		}

		slots.put(bottomStart + 8, SlotBinding.action(
			buildNavItem(Items.CLOCK, OguiText.nonItalic("Refresh", ChatFormatting.GREEN), "Reload screen data", server),
			OguiSession.NAV_REFRESH
		));

		return new PlannedChestView(rows, Map.copyOf(slots));
	}

	private static PlannedChestView planFixedGrid(
		OguiScreenModel model,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		Map<Integer, SlotBinding> slots = new LinkedHashMap<>();
		for (Map.Entry<Integer, OguiWidget> entry : model.slottedWidgets().entrySet()) {
			OguiWidget widget = entry.getValue();
			slots.put(entry.getKey(), SlotBinding.action(renderWidgetItem(widget, confirmKey, server), widget.widgetId()));
		}
		return new PlannedChestView(model.fixedRows(), Map.copyOf(slots));
	}

	private static void placeSummaryCards(
		Map<Integer, SlotBinding> slots,
		List<OguiSummaryCard> cards,
		@Nullable MinecraftServer server
	) {
		if (cards.isEmpty()) {
			return;
		}
		if (!slots.containsKey(8)) {
			slots.put(8, SlotBinding.decorative(renderSummaryItem(cards.get(0), server)));
		}
		if (cards.size() > 1 && !slots.containsKey(7)) {
			slots.put(7, SlotBinding.decorative(renderSummaryItem(cards.get(1), server)));
		}
	}

	private static void placePagedContent(
		Map<Integer, SlotBinding> slots,
		OguiSession session,
		OguiScreenModel model,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		List<OguiWidget> slice = session.pagedSlice();
		if (slice.isEmpty()) {
			if (model.emptyState() != null) {
				OguiWidget empty = model.emptyState();
				slots.put(22, SlotBinding.action(renderWidgetItem(empty, confirmKey, server), empty.widgetId()));
			}
			return;
		}
		for (int i = 0; i < slice.size() && i < OguiReadOnlyChestMenu.CONTENT_SLOTS.length; i++) {
			OguiWidget widget = slice.get(i);
			slots.put(
				OguiReadOnlyChestMenu.CONTENT_SLOTS[i],
				SlotBinding.action(renderWidgetItem(widget, confirmKey, server), widget.widgetId())
			);
		}
	}

	private static void placePagedFooter(
		Map<Integer, SlotBinding> slots,
		OguiSession session,
		@Nullable Component backButtonLabel,
		List<OguiWidget> extraFooterWidgets,
		@Nullable String confirmKey,
		@Nullable MinecraftServer server
	) {
		boolean hasBack = session.request().backRequest() != null;
		Component backLabel = hasBack
			? (backButtonLabel != null ? OguiText.nonItalic(backButtonLabel) : OguiText.nonItalic("Back", ChatFormatting.YELLOW))
			: OguiText.nonItalic("Close", ChatFormatting.RED);
		slots.put(SLOT_CLOSE_OR_BACK, SlotBinding.action(
			buildNavItem(
				hasBack ? Items.OAK_DOOR : Items.BARRIER,
				backLabel,
				hasBack ? "Click to return" : "Click to close menu",
				server
			),
			hasBack ? OguiSession.NAV_BACK : OguiSession.NAV_CLOSE
		));

		int footerSlotIdx = 0;
		for (OguiWidget widget : extraFooterWidgets) {
			while (footerSlotIdx < FOOTER_WIDGET_SLOTS.length && slots.containsKey(FOOTER_WIDGET_SLOTS[footerSlotIdx])) {
				footerSlotIdx++;
			}
			if (footerSlotIdx >= FOOTER_WIDGET_SLOTS.length) {
				break;
			}
			slots.put(
				FOOTER_WIDGET_SLOTS[footerSlotIdx],
				SlotBinding.action(renderWidgetItem(widget, confirmKey, server), widget.widgetId())
			);
			footerSlotIdx++;
		}

		if (session.hasPreviousPage()) {
			slots.put(SLOT_PREV_PAGE, SlotBinding.action(
				buildNavItem(Items.ARROW, OguiText.nonItalic("Previous Page", ChatFormatting.YELLOW), "Go to page " + session.page(), server),
				OguiSession.NAV_PREV
			));
		}

		slots.put(SLOT_PAGE_INDICATOR, SlotBinding.action(
			OguiReadOnlyChestMenu.buildItem(
				OguiIcon.item("minecraft:paper"),
				OguiText.nonItalic("Page " + (session.page() + 1) + " / " + session.totalPages(), ChatFormatting.WHITE),
				List.of(
					OguiText.nonItalic("Entries: " + session.model().entries().size(), ChatFormatting.GRAY),
					OguiText.nonItalic("Click to refresh", ChatFormatting.YELLOW)
				),
				false,
				server
			),
			OguiSession.NAV_REFRESH
		));

		if (session.hasNextPage()) {
			slots.put(SLOT_NEXT_PAGE, SlotBinding.action(
				buildNavItem(Items.ARROW, OguiText.nonItalic("Next Page", ChatFormatting.YELLOW), "Go to page " + (session.page() + 2), server),
				OguiSession.NAV_NEXT
			));
		}

		slots.put(SLOT_REFRESH, SlotBinding.action(
			buildNavItem(Items.CLOCK, OguiText.nonItalic("Refresh", ChatFormatting.GREEN), "Reload latest screen data", server),
			OguiSession.NAV_REFRESH
		));
	}

	private static void placeQuantityRow(
		Map<Integer, SlotBinding> slots,
		int row,
		OguiWidget.QuantitySelectorWidget q,
		@Nullable MinecraftServer server
	) {
		int centerSlot = row * 9 + 4;
		slots.put(centerSlot, SlotBinding.action(renderWidgetItem(q, null, server), q.widgetId()));

		List<Integer> steps = q.stepPresets();
		Item[] negItems = {Items.STAINED_GLASS_PANE.yellow(), Items.STAINED_GLASS_PANE.orange(), Items.STAINED_GLASS_PANE.red()};
		Item[] posItems = {Items.STAINED_GLASS_PANE.lime(), Items.STAINED_GLASS_PANE.green(), Items.CONCRETE.green()};

		int limit = Math.min(3, steps.size());
		for (int i = 0; i < limit; i++) {
			int step = steps.get(i);
			int negTarget = Math.clamp((long) q.value() - step, q.min(), q.max());
			int posTarget = Math.clamp((long) q.value() + step, q.min(), q.max());

			ItemStack negStack = OguiReadOnlyChestMenu.buildItem(
				OguiIcon.item(negItems[i]),
				OguiText.nonItalic("-" + step, ChatFormatting.RED),
				List.of(
					OguiText.nonItalic("Set quantity to " + negTarget, ChatFormatting.GRAY),
					OguiText.nonItalic("Range: " + q.min() + " .. " + q.max(), ChatFormatting.DARK_GRAY)
				),
				false,
				server
			);
			slots.put(centerSlot - (i + 1), SlotBinding.numeric(negStack, q.widgetId(), negTarget));

			ItemStack posStack = OguiReadOnlyChestMenu.buildItem(
				OguiIcon.item(posItems[i]),
				OguiText.nonItalic("+" + step, ChatFormatting.GREEN),
				List.of(
					OguiText.nonItalic("Set quantity to " + posTarget, ChatFormatting.GRAY),
					OguiText.nonItalic("Range: " + q.min() + " .. " + q.max(), ChatFormatting.DARK_GRAY)
				),
				false,
				server
			);
			slots.put(centerSlot + (i + 1), SlotBinding.numeric(posStack, q.widgetId(), posTarget));
		}
	}

	public static ItemStack renderSummaryItem(OguiSummaryCard card, @Nullable MinecraftServer server) {
		return OguiReadOnlyChestMenu.buildItem(card.icon(), OguiText.nonItalic(card.title()), card.lines(), false, server);
	}

	public static ItemStack renderWidgetItem(
		OguiWidget widget,
		@Nullable String pendingConfirmKey,
		@Nullable MinecraftServer server
	) {
		return switch (widget) {
			case OguiWidget.ButtonWidget b -> {
				OguiConfirmSpec confirm = b.confirmSpec();
				boolean awaiting = confirm != null && confirm.targetKey().equals(pendingConfirmKey);
				List<Component> lore = new ArrayList<>();
				if (b.badge() != null) {
					lore.add(OguiText.nonItalic(b.badge()));
				}
				lore.addAll(b.lore());
				if (awaiting) {
					lore.add(confirm.promptLine());
				}
				if (!b.enabled()) {
					lore.add(OguiText.nonItalic("Unavailable", ChatFormatting.DARK_GRAY));
				}
				yield OguiReadOnlyChestMenu.buildItem(b.icon(), OguiText.nonItalic(b.label()), lore, awaiting, server);
			}
			case OguiWidget.ToggleWidget t -> {
				OguiConfirmSpec confirm = t.confirmSpec();
				boolean awaiting = confirm != null && confirm.targetKey().equals(pendingConfirmKey);
				List<Component> lore = new ArrayList<>();
				lore.add(Component.literal("State: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
					.append(t.value() ? t.onLabel() : t.offLabel()));
				lore.addAll(t.lore());
				if (awaiting) {
					lore.add(confirm.promptLine());
				} else if (t.enabled()) {
					lore.add(OguiText.nonItalic("Click to toggle", ChatFormatting.YELLOW));
				}
				yield OguiReadOnlyChestMenu.buildItem(
					t.effectiveIcon(),
					OguiText.nonItalic(t.label()),
					lore,
					t.value() || awaiting,
					server
				);
			}
			case OguiWidget.CycleWidget c -> {
				OguiWidget.CycleOption selected = c.selectedOption();
				OguiIcon icon = selected.icon() != null ? selected.icon() : c.icon();
				List<Component> lore = new ArrayList<>();
				lore.add(Component.literal("Selected: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
					.append(selected.label()));
				lore.addAll(c.lore());
				if (c.enabled()) {
					lore.add(OguiText.nonItalic("Left-click: Next | Right-click: Previous", ChatFormatting.YELLOW));
					lore.add(OguiText.nonItalic("Shift-click: Reset", ChatFormatting.GOLD));
				}
				yield OguiReadOnlyChestMenu.buildItem(icon, OguiText.nonItalic(c.label()), lore, false, server);
			}
			case OguiWidget.QuantitySelectorWidget q -> {
				List<Component> lore = new ArrayList<>();
				lore.add(OguiText.nonItalic("Quantity: " + q.value() + " (" + q.min() + ".." + q.max() + ")", ChatFormatting.AQUA));
				Component summary = q.formattedSummary();
				if (summary != null) {
					lore.add(OguiText.nonItalic(summary));
				}
				lore.addAll(q.lore());
				if (q.enabled()) {
					int s0 = q.stepPresets().getFirst();
					lore.add(OguiText.nonItalic("Left: +" + s0 + " | Right: -" + s0, ChatFormatting.YELLOW));
				}
				yield OguiReadOnlyChestMenu.buildItem(
					q.icon().withCount(Math.clamp(q.value(), 1, 99)),
					OguiText.nonItalic(q.label()),
					lore,
					false,
					server
				);
			}
			case OguiWidget.TextInputWidget ti -> {
				List<Component> lore = new ArrayList<>();
				if (ti.value().isEmpty()) {
					lore.add(Component.literal("Value: ").withStyle(ChatFormatting.GRAY).withStyle(s -> s.withItalic(false))
						.append(ti.placeholder().getString().isEmpty()
							? OguiText.nonItalic("(empty)", ChatFormatting.DARK_GRAY)
							: ti.placeholder()));
				} else {
					lore.add(OguiText.nonItalic("Value: " + ti.value(), ChatFormatting.GREEN));
				}
				lore.addAll(ti.lore());
				if (ti.enabled()) {
					lore.add(OguiText.nonItalic("Left-click: Edit via Sign", ChatFormatting.YELLOW));
					lore.add(OguiText.nonItalic("Right-click: Clear", ChatFormatting.GRAY));
				}
				yield OguiReadOnlyChestMenu.buildItem(ti.icon(), OguiText.nonItalic(ti.label()), lore, !ti.value().isEmpty(), server);
			}
			case OguiWidget.StructurePreviewWidget sp -> {
				List<Component> lore = new ArrayList<>(sp.lore());
				lore.add(OguiText.nonItalic("3D Preview requires ogui-client mod", ChatFormatting.DARK_GRAY));
				yield OguiReadOnlyChestMenu.buildItem(sp.icon(), OguiText.nonItalic(sp.label()), lore, false, server);
			}
		};
	}

	private static ItemStack buildNavItem(
		Item item,
		Component label,
		String hint,
		@Nullable MinecraftServer server
	) {
		return OguiReadOnlyChestMenu.buildItem(
			OguiIcon.item(item),
			label,
			List.of(OguiText.nonItalic(hint, ChatFormatting.GRAY)),
			false,
			server
		);
	}
}
