package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.OguiTabDto;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiLayoutEngineTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void tabbedPanelProducesNonOverlappingBoundsAndScrollableOverflow() {
		List<OguiWidgetDto> entries = new ArrayList<>();
		for (int i = 0; i < 28; i++) {
			entries.add(new OguiWidgetDto.ButtonDto(
				"item_" + i, OguiIcon.item("minecraft:diamond"), OguiText.nonItalic("Item " + i, ChatFormatting.AQUA),
				List.of(), null, true, null
			));
		}
		ScreenSnapshotDto snap = new ScreenSnapshotDto(
			Ogui.id("shop"),
			null,
			OguiText.nonItalic("Shop"),
			LayoutArchetype.TABBED_PANEL,
			OguiTheme.builder()
				.subtitle("Buy & Sell")
				.addSummaryCard(OguiSummaryCard.of(OguiIcon.item("minecraft:emerald"), OguiText.nonItalic("Balance"), List.of(OguiText.nonItalic("500g"))))
				.build(),
			List.of(
				new OguiTabDto("blocks", OguiIcon.item("minecraft:stone"), OguiText.nonItalic("Blocks"), List.of(), true),
				new OguiTabDto("ores", OguiIcon.item("minecraft:diamond"), OguiText.nonItalic("Ores"), List.of(), true)
			),
			"blocks",
			0, 2, 40, true,
			List.of(new OguiWidgetDto.TextInputDto(
				"search", "search", OguiIcon.item("minecraft:compass"), OguiText.nonItalic("Search"),
				OguiText.nonItalic("..."), "", 48, OguiWidget.InputMode.SEARCH, true, List.of(), true
			)),
			entries,
			List.of(),
			null, List.of(), List.of(), List.of(), 6, Map.of(), null, null
		);

		for (int[] res : new int[][]{{480, 270}, {854, 480}, {1920, 1080}}) {
			for (OguiClientPreferences.UiScaleMode scale : OguiClientPreferences.UiScaleMode.values()) {
				OguiLayoutEngine.ComputedLayout layout = OguiLayoutEngine.compute(snap, res[0], res[1], scale);
				assertTrue(layout.panelRect().width() <= res[0]);
				assertTrue(layout.panelRect().height() <= res[1]);
				assertEquals(2, layout.tabs().size());
				assertTrue(layout.maxScrollOffset() > 0, "Expected 28 cards to scroll smoothly");

				// Every non-scrollable widget and nav button stays inside panelRect
				for (OguiLayoutEngine.PositionedNav nav : layout.navButtons()) {
					assertInside(layout.panelRect(), nav.bounds());
				}
				for (OguiLayoutEngine.PositionedTab tab : layout.tabs()) {
					assertInside(layout.panelRect(), tab.bounds());
				}
			}
		}
	}

	@Test
	void detailFormAndFixedGridKeepExpectedWidgetStructures() {
		OguiWidgetDto.QuantityDto qty = new OguiWidgetDto.QuantityDto(
			"qty", OguiIcon.item("minecraft:chest"), OguiText.nonItalic("Amount"),
			10, 1, 100, List.of(1, 16, 64), OguiText.nonItalic("Total: 100g"), List.of(), true
		);
		ScreenSnapshotDto detail = new ScreenSnapshotDto(
			Ogui.id("detail"), null, OguiText.nonItalic("Item Detail"),
			LayoutArchetype.DETAIL_FORM, OguiTheme.DEFAULT, List.of(), null, 0, 1, 0, true,
			List.of(), List.of(), List.of(),
			new OguiWidgetDto.ButtonDto("hero", OguiIcon.item("minecraft:diamond"), OguiText.nonItalic("Diamond"), List.of(), null, true, null),
			List.of(new OguiWidgetDto.ButtonDto("stock", OguiIcon.item("minecraft:chest"), OguiText.nonItalic("Stock: 50"), List.of(), null, true, null)),
			List.of(qty),
			List.of(new OguiWidgetDto.ButtonDto("buy", OguiIcon.item("minecraft:lime_dye"), OguiText.nonItalic("Buy"), List.of(), null, true, null)),
			6, Map.of(), null, null
		);
		OguiLayoutEngine.ComputedLayout detailLayout = OguiLayoutEngine.compute(detail, 854, 480, OguiClientPreferences.UiScaleMode.NORMAL);
		OguiLayoutEngine.PositionedWidget qtyWidget = detailLayout.widgets().stream()
			.filter(w -> w.widget().widgetId().equals("qty"))
			.findFirst()
			.orElseThrow();
		assertEquals(6, qtyWidget.quantitySteps().size());
		assertEquals("-64", qtyWidget.quantitySteps().get(0).label());
		assertEquals(1, qtyWidget.quantitySteps().get(0).targetValue());
		assertEquals("+64", qtyWidget.quantitySteps().get(5).label());
		assertEquals(74, qtyWidget.quantitySteps().get(5).targetValue());

		Map<Integer, OguiWidgetDto> slots = new LinkedHashMap<>();
		slots.put(0, new OguiWidgetDto.ButtonDto("a", OguiIcon.item("minecraft:stone"), OguiText.nonItalic("A"), List.of(), null, true, null));
		slots.put(26, new OguiWidgetDto.ButtonDto("b", OguiIcon.item("minecraft:dirt"), OguiText.nonItalic("B"), List.of(), null, true, null));
		ScreenSnapshotDto fixed = new ScreenSnapshotDto(
			Ogui.id("fixed"), null, OguiText.nonItalic("Fixed"),
			LayoutArchetype.FIXED_GRID, OguiTheme.DEFAULT, List.of(), null, 0, 1, 0, false,
			List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
			3, slots, null, null
		);
		OguiLayoutEngine.ComputedLayout fixedLayout = OguiLayoutEngine.compute(fixed, 854, 480, OguiClientPreferences.UiScaleMode.NORMAL);
		long slotCount = fixedLayout.widgets().stream()
			.filter(w -> w.region() == OguiLayoutEngine.Region.FIXED_SLOT)
			.count();
		assertEquals(2, slotCount);
	}

	@Test
	void galleryModeAnd3dHeroExpandCardAndViewportSizes() {
		OguiAssetRef pngRef = new OguiAssetRef("0123456789abcdef".repeat(4), OguiAssetKind.PNG, 2048);
		OguiAssetRef shellRef = new OguiAssetRef("abcdef0123456789".repeat(4), OguiAssetKind.STRUCTURE_SHELL, 1024);

		List<OguiWidgetDto> galleryEntries = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			galleryEntries.add(new OguiWidgetDto.ButtonDto(
				"island_" + i, OguiIcon.item("minecraft:grass_block"), OguiText.nonItalic("Island " + i),
				List.of(), null, true, null, i == 0 ? pngRef : null
			));
		}
		ScreenSnapshotDto gallerySnap = new ScreenSnapshotDto(
			Ogui.id("island_gallery"), null, OguiText.nonItalic("Islands"),
			LayoutArchetype.PAGED_GRID, OguiTheme.DEFAULT, List.of(), null, 0, 1, 8, false,
			List.of(), galleryEntries, List.of(), null, List.of(), List.of(), List.of(), 6, Map.of(), null, null
		);
		assertTrue(OguiLayoutEngine.isGalleryGrid(gallerySnap));
		OguiLayoutEngine.ComputedLayout galleryLayout = OguiLayoutEngine.compute(gallerySnap, 854, 480, OguiClientPreferences.UiScaleMode.NORMAL);
		OguiLayoutEngine.PositionedWidget firstEntry = galleryLayout.widgets().stream()
			.filter(w -> w.region() == OguiLayoutEngine.Region.ENTRY)
			.findFirst()
			.orElseThrow();
		assertTrue(firstEntry.bounds().height() >= 80, "Gallery cards must reserve room for PNG thumbnails");
		assertTrue(galleryLayout.maxScrollOffset() > 0, "8 tall gallery cards should enable vertical scrolling");

		ScreenSnapshotDto hero3dSnap = new ScreenSnapshotDto(
			Ogui.id("island_3d"), null, OguiText.nonItalic("3D Preview"),
			LayoutArchetype.DETAIL_FORM, OguiTheme.DEFAULT, List.of(), null, 0, 1, 0, true,
			List.of(), List.of(), List.of(),
			new OguiWidgetDto.StructurePreviewDto(
				"island_view", OguiIcon.item("minecraft:grass_block"), OguiText.nonItalic("Classic Skyblock"),
				List.of(), shellRef, 42.0F, 26.0F, false, true
			),
			List.of(),
			List.of(new OguiWidgetDto.CycleDto(
				"dim", OguiIcon.item("minecraft:ender_pearl"), OguiText.nonItalic("Dimension"),
				List.of(new OguiWidgetDto.CycleOptionDto("overworld", OguiText.nonItalic("Overworld"), null)),
				0, List.of(), true
			)),
			List.of(new OguiWidgetDto.ButtonDto("create", OguiIcon.item("minecraft:emerald"), OguiText.nonItalic("Create"), List.of(), null, true, null)),
			6, Map.of(), null, null
		);
		OguiLayoutEngine.ComputedLayout heroLayout = OguiLayoutEngine.compute(hero3dSnap, 854, 480, OguiClientPreferences.UiScaleMode.NORMAL);
		OguiLayoutEngine.PositionedWidget heroWidget = heroLayout.widgets().stream()
			.filter(w -> w.region() == OguiLayoutEngine.Region.HERO)
			.findFirst()
			.orElseThrow();
		assertTrue(heroWidget.bounds().width() > heroLayout.bodyRect().width() / 2, "3D hero viewport should take >50% width");
		assertTrue(heroWidget.bounds().height() >= 120, "3D hero viewport should use tall pane height");
		assertInside(heroLayout.bodyRect(), heroWidget.bounds());
	}

	private static void assertInside(OguiLayoutEngine.Rect outer, OguiLayoutEngine.Rect inner) {
		assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
			&& inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
			"Expected " + inner + " to fit inside " + outer);
	}
}
