package me.ogsammaenr.ogui.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.vanilla.OguiChestLayout;
import me.ogsammaenr.ogui.vanilla.OguiChestMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies TABBED_PANEL, PAGED_GRID, DETAIL_FORM, and FIXED_GRID end-to-end interactions
 * including tabs, toggle/cycle, quantity selector stepping, 2-click confirm, and back navigation.
 */
public final class OguiArchetypeGameTests {
	@GameTest
	public void allFourArchetypesAndWidgetsWorkEndToEnd(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Identifier tabbedId = Identifier.fromNamespaceAndPath("ogui_test", "tabbed_" + suffix);
		Identifier detailId = Identifier.fromNamespaceAndPath("ogui_test", "detail_" + suffix);

		AtomicInteger purchasedQty = new AtomicInteger();

		Ogui.service().registerScreen(detailId, ctx -> {
			int qty = ctx.stateInt("qty", 1);
			boolean gift = Boolean.parseBoolean(ctx.stateOrDefault("gift", "false"));
			return OguiScreenModel.detailForm("Purchase Diamond")
				.heroCard(OguiWidget.button("hero", OguiIcon.item(Items.DIAMOND), OguiText.nonItalic("Diamond", ChatFormatting.AQUA)).build())
				.addControl(OguiWidget.quantity("qty_ctrl", OguiIcon.item(Items.CHEST), OguiText.nonItalic("Quantity"))
					.range(1, 100)
					.value(qty)
					.steps(1, 16, 64)
					.summaryFormatter(v -> OguiText.nonItalic("Total: " + (v * 50) + "g", ChatFormatting.GOLD))
					.onChange((click, nextVal) -> click.refreshWithState("qty", Integer.toString(nextVal)))
					.build())
				.addControl(OguiWidget.toggle("gift_ctrl", OguiIcon.item(Items.LEVER), OguiText.nonItalic("Gift Wrap"))
					.value(gift)
					.onToggle((click, next) -> click.refreshWithState("gift", Boolean.toString(next)))
					.build())
				.addAction(OguiWidget.button("buy_now", OguiIcon.item(Items.EMERALD), OguiText.nonItalic("Buy Now", ChatFormatting.GREEN))
					.confirm("confirm_purchase", "Click again to confirm purchase")
					.onClick(click -> {
						purchasedQty.set(click.stateInt("qty", 1));
						click.goBack();
					})
					.build())
				.build();
		});

		Ogui.service().registerScreen(tabbedId, ctx -> {
			String tab = ctx.stateOrDefault("tab", "ores");
			String sort = ctx.stateOrDefault("sort", "asc");
			OguiScreenModel.TabbedPanelBuilder b = OguiScreenModel.tabbedPanel("Catalog (" + tab + ")")
				.addTab(OguiTab.builder("ores", OguiIcon.item(Items.DIAMOND), OguiText.nonItalic("Ores")).build())
				.addTab(OguiTab.builder("food", OguiIcon.item(Items.APPLE), OguiText.nonItalic("Food")).build())
				.activeTabId(tab)
				.addFooterWidget(OguiWidget.cycle("sort_cycle", OguiIcon.item(Items.HOPPER), OguiText.nonItalic("Sort"))
					.addOption("asc", OguiText.nonItalic("Ascending"))
					.addOption("desc", OguiText.nonItalic("Descending"))
					.selectedId(sort)
					.onCycle((click, opt) -> click.refreshWithState("sort", opt.id()))
					.build());

			if ("ores".equals(tab)) {
				for (int i = 0; i < 30; i++) {
					int idx = "desc".equals(sort) ? (29 - i) : i;
					b.addEntry(OguiWidget.button("ore_" + idx, "minecraft:diamond", "Ore " + idx, ChatFormatting.AQUA)
						.onClick(click -> click.openScreen(OguiOpenRequest.of(detailId).withState("qty", "5")))
						.build());
				}
			} else {
				b.addEntry(OguiWidget.button("apple_item", "minecraft:apple", "Crisp Apple", ChatFormatting.RED).build());
			}
			return b.build();
		});

		AtomicReference<EmbeddedChannel> chRef = new AtomicReference<>();
		ServerPlayer player = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "OguiArchetypes", GameType.SURVIVAL, chRef);

		try {
			helper.assertTrue(Ogui.service().openScreen(player, tabbedId), "Expected tabbed screen to open");
			OguiChestMenu tabbedMenu = (OguiChestMenu) player.containerMenu;

			// Cycle sort from asc -> desc
			int cycleSlot = OguiGameTestSupport.findSlotByName(tabbedMenu, "Sort");
			helper.assertTrue(cycleSlot >= 0, "Expected Sort cycle button in footer");
			tabbedMenu.clicked(cycleSlot, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(OguiGameTestSupport.findSlotByName(tabbedMenu, "Ore 29") >= 0,
				"Expected Ore 29 first after cycling sort to desc");

			// Switch tab to Food (slot 1), then back to Ores (slot 0)
			tabbedMenu.clicked(1, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(OguiGameTestSupport.findSlotByName(tabbedMenu, "Crisp Apple") >= 0,
				"Expected Food tab entry Crisp Apple");
			tabbedMenu.clicked(0, 0, ContainerInput.PICKUP, player);

			// Pagination Next -> Page 2 -> Back to Page 1
			tabbedMenu.clicked(OguiChestLayout.SLOT_NEXT_PAGE, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(OguiGameTestSupport.findSlotByName(tabbedMenu, "Ore 1") >= 0
				|| OguiGameTestSupport.findSlotByName(tabbedMenu, "Ore 0") >= 0,
				"Expected tail entries on page 2");
			tabbedMenu.clicked(OguiChestLayout.SLOT_PREV_PAGE, 0, ContainerInput.PICKUP, player);

			// Open DetailForm
			int ore29Slot = OguiGameTestSupport.findSlotByName(tabbedMenu, "Ore 29");
			tabbedMenu.clicked(ore29Slot, 0, ContainerInput.PICKUP, player);
			OguiChestMenu detailMenu = (OguiChestMenu) player.containerMenu;
			helper.assertTrue(detailMenu != tabbedMenu, "Expected DetailForm ChestMenu opened");

			// Click +16 quantity button (5 -> 21) and +64 (21 -> 85)
			int plus16 = OguiGameTestSupport.findSlotByName(detailMenu, "+16");
			int plus64 = OguiGameTestSupport.findSlotByName(detailMenu, "+64");
			helper.assertTrue(plus16 >= 0 && plus64 >= 0, "Expected +16 and +64 quantity step buttons");
			detailMenu.clicked(plus16, 0, ContainerInput.PICKUP, player);
			detailMenu.clicked(plus64, 0, ContainerInput.PICKUP, player);

			// Toggle Gift Wrap
			int giftSlot = OguiGameTestSupport.findSlotByName(detailMenu, "Gift Wrap");
			detailMenu.clicked(giftSlot, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(Boolean.TRUE.equals(detailMenu.getSlot(giftSlot).getItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"Expected Gift Wrap toggled on with glint");

			// Two-click confirmation on Buy Now -> goes back to TabbedPanel
			int buySlot = OguiGameTestSupport.findSlotByName(detailMenu, "Buy Now");
			detailMenu.clicked(buySlot, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(purchasedQty.get() == 0, "First click must arm confirmation");
			helper.assertTrue(Boolean.TRUE.equals(detailMenu.getSlot(buySlot).getItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
				"Pending confirm button must highlight with glint");

			detailMenu.clicked(buySlot, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(purchasedQty.get() == 85, "Second click must commit quantity 85, got: " + purchasedQty.get());
			helper.assertTrue(player.containerMenu instanceof OguiChestMenu && player.containerMenu != detailMenu,
				"Expected goBack() to reopen parent TabbedPanel");

			// Verify PAGED_GRID and FIXED_GRID archetypes as well
			Identifier pagedId = Identifier.fromNamespaceAndPath("ogui_test", "paged_" + suffix);
			Identifier fixedId = Identifier.fromNamespaceAndPath("ogui_test", "fixed_" + suffix);
			AtomicInteger fixedClicks = new AtomicInteger();

			Ogui.service().registerScreen(pagedId, ctx -> OguiScreenModel.pagedGrid("Paged Screen")
				.addEntry(OguiWidget.button("open_fixed", "minecraft:beacon", "Open Fixed", ChatFormatting.AQUA)
					.onClick(click -> click.openScreen(fixedId, null))
					.build())
				.build());
			Ogui.service().registerScreen(fixedId, ctx -> OguiScreenModel.fixedGrid("Fixed 3-Row", 3)
				.slot(13, OguiWidget.button("fixed_center", "minecraft:emerald", "Center Action", ChatFormatting.GREEN)
					.onClick(click -> fixedClicks.incrementAndGet())
					.build())
				.build());

			helper.assertTrue(Ogui.service().openScreen(player, pagedId), "Expected PAGED_GRID screen open");
			OguiChestMenu pagedMenu = (OguiChestMenu) player.containerMenu;
			int openFixedSlot = OguiGameTestSupport.findSlotByName(pagedMenu, "Open Fixed");
			helper.assertTrue(openFixedSlot >= 0, "Expected Open Fixed button in PAGED_GRID");
			pagedMenu.clicked(openFixedSlot, 0, ContainerInput.PICKUP, player);

			OguiChestMenu fixedMenu = (OguiChestMenu) player.containerMenu;
			helper.assertTrue(fixedMenu.getRowCount() == 3, "Expected 3-row FIXED_GRID menu");
			fixedMenu.clicked(13, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(fixedClicks.get() == 1, "Expected FIXED_GRID center slot callback to fire");

			helper.succeed();
		} finally {
			OguiGameTestSupport.cleanupPlayers(server, player);
		}
	}
}
