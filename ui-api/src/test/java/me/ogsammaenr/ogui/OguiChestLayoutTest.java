package me.ogsammaenr.ogui;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.runtime.OguiSession;
import me.ogsammaenr.ogui.vanilla.OguiChestLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiChestLayoutTest {
	@BeforeAll
	static void bootstrap() {
		OguiTestBootstrap.ensureBootstrapped();
	}

	@Test
	void tabbedPanelPlansTabsSummaryPagedSliceAndFooter() {
		OguiTheme theme = OguiTheme.builder()
			.addSummaryCard(OguiSummaryCard.of(OguiIcon.item(Items.EMERALD), OguiText.nonItalic("Wallet"), List.of()))
			.build();
		OguiScreenModel.TabbedPanelBuilder builder = OguiScreenModel.tabbedPanel("Market")
			.theme(theme)
			.addTab(OguiTab.builder("buy", OguiIcon.item(Items.GOLD_INGOT), OguiText.nonItalic("Buy")).build())
			.addTab(OguiTab.builder("sell", OguiIcon.item(Items.IRON_INGOT), OguiText.nonItalic("Sell")).build())
			.activeTabId("buy")
			.addHeaderWidget(OguiWidget.textInput("search", OguiIcon.item(Items.COMPASS), OguiText.nonItalic("Search")).build());

		for (int i = 0; i < 30; i++) {
			builder.addEntry(OguiWidget.button("e_" + i, "minecraft:apple", "Apple " + i, ChatFormatting.WHITE).build());
		}
		OguiSession session = new OguiSession(
			UUID.randomUUID(),
			UUID.randomUUID(),
			false,
			OguiOpenRequest.of(Ogui.id("market")).withBackRequest(OguiOpenRequest.of(Ogui.id("home"))),
			builder.build()
		);

		OguiChestLayout.PlannedChestView view = OguiChestLayout.plan(session, null);
		assertEquals(6, view.rows());
		assertEquals(OguiSession.NAV_TAB_PREFIX + "buy", view.slots().get(0).actionId());
		assertTrue(Boolean.TRUE.equals(view.slots().get(0).icon().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
		assertEquals(OguiSession.NAV_TAB_PREFIX + "sell", view.slots().get(1).actionId());
		assertNotNull(view.slots().get(8));
		assertEquals(OguiSession.NAV_BACK, view.slots().get(OguiChestLayout.SLOT_CLOSE_OR_BACK).actionId());
		assertEquals("search", view.slots().get(47).actionId());
		assertEquals(OguiSession.NAV_NEXT, view.slots().get(OguiChestLayout.SLOT_NEXT_PAGE).actionId());
		assertEquals("e_0", view.slots().get(10).actionId());
		assertEquals("e_27", view.slots().get(43).actionId());
	}

	@Test
	void tabbedPanelAndPagedGridRespectVanillaSlotHintsAndCustomBackLabel() {
		OguiTheme theme = OguiTheme.builder()
			.addSummaryCard(OguiSummaryCard.of(OguiIcon.item(Items.PLAYER_HEAD), OguiText.nonItalic("Overview"), List.of()))
			.build();
		OguiScreenModel tabbed = OguiScreenModel.tabbedPanel("Control Panel")
			.theme(theme)
			.addTab(OguiTab.builder("actions", OguiIcon.item(Items.NETHER_STAR), OguiText.nonItalic("Actions")).build())
			.activeTabId("actions")
			.addHeaderWidget(OguiWidget.toggle("quick_lock", OguiIcon.item(Items.OAK_DOOR), OguiText.nonItalic("Lock")).build(), 7)
			.addFooterWidget(OguiWidget.button("mode", "minecraft:lever", "Mode", ChatFormatting.AQUA).build(), 47)
			.addFooterWidget(OguiWidget.textInput("search", OguiIcon.item(Items.COMPASS), OguiText.nonItalic("Search")).build())
			.build();

		OguiSession tabbedSession = new OguiSession(UUID.randomUUID(), UUID.randomUUID(), false, OguiOpenRequest.of(Ogui.id("panel")), tabbed);
		OguiChestLayout.PlannedChestView tabbedView = OguiChestLayout.plan(tabbedSession, null);
		assertEquals("quick_lock", tabbedView.slots().get(7).actionId());
		assertNotNull(tabbedView.slots().get(8));
		assertEquals("mode", tabbedView.slots().get(47).actionId());
		assertEquals("search", tabbedView.slots().get(46).actionId());

		OguiScreenModel paged = OguiScreenModel.pagedGrid("Limits")
			.backLabel("Back to Control Panel")
			.addHeaderWidget(OguiWidget.button("filter", "minecraft:hopper", "Filter", ChatFormatting.AQUA).build(), 2)
			.addHeaderWidget(OguiWidget.button("overview", "minecraft:player_head", "Overview", ChatFormatting.GOLD).build(), 4)
			.addFooterWidget(OguiWidget.button("sort", "minecraft:comparator", "Sort", ChatFormatting.GOLD).build(), 47)
			.build();
		OguiOpenRequest pagedReq = OguiOpenRequest.of(Ogui.id("limits")).withBackRequest(OguiOpenRequest.of(Ogui.id("panel")));
		OguiSession pagedSession = new OguiSession(UUID.randomUUID(), UUID.randomUUID(), false, pagedReq, paged);
		OguiChestLayout.PlannedChestView pagedView = OguiChestLayout.plan(pagedSession, null);
		assertEquals("filter", pagedView.slots().get(2).actionId());
		assertEquals("overview", pagedView.slots().get(4).actionId());
		assertEquals("sort", pagedView.slots().get(47).actionId());
		assertEquals("Back to Control Panel", pagedView.slots().get(45).icon().get(DataComponents.CUSTOM_NAME).getString());
	}

	@Test
	void detailFormExpandsQuantitySelectorIntoSymmetricStepRow() {
		OguiScreenModel model = OguiScreenModel.detailForm("Buy Diamond")
			.heroCard(OguiWidget.button("hero", "minecraft:diamond", "Diamond", ChatFormatting.AQUA).build())
			.addControl(OguiWidget.quantity("qty", OguiIcon.item(Items.CHEST), OguiText.nonItalic("Amount"))
				.range(1, 128)
				.value(20)
				.steps(1, 16, 64)
				.build())
			.addAction(OguiWidget.button("confirm_buy", "minecraft:lime_dye", "Confirm", ChatFormatting.GREEN).build())
			.build();

		OguiSession session = new OguiSession(UUID.randomUUID(), UUID.randomUUID(), false, OguiOpenRequest.of(Ogui.id("detail")), model);
		OguiChestLayout.PlannedChestView view = OguiChestLayout.plan(session, null);
		assertEquals(4, view.rows());

		// Row 1 quantity layout: col 1=-64(clamped 1), col 2=-16(4), col 3=-1(19), col 4=center, col 5=+1(21), col 6=+16(36), col 7=+64(84)
		assertEquals(OguiClickAction.CLIENT_INPUT, view.slots().get(10).forcedAction());
		assertEquals(1, view.slots().get(10).forcedNumericValue());
		assertEquals(4, view.slots().get(11).forcedNumericValue());
		assertEquals(19, view.slots().get(12).forcedNumericValue());
		assertEquals("qty", view.slots().get(13).actionId());
		assertEquals(21, view.slots().get(14).forcedNumericValue());
		assertEquals(36, view.slots().get(15).forcedNumericValue());
		assertEquals(84, view.slots().get(16).forcedNumericValue());
	}
}
