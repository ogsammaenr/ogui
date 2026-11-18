package me.ogsammaenr.ogui.client.test;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiBuildContext;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiService;
import me.ogsammaenr.ogui.api.OguiShortcut;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.api.OguiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared showcase definitions for all four Ogui layout archetypes, consumed by both
 * standalone client previews ({@code /oguitest open}) and live singleplayer server sessions ({@code /oguitest live}).
 */
public final class OguiDemoScreens {
	public enum DemoArchetype {
		TABBED("tabbed", "Sekmeli Panel (TABBED_PANEL)"),
		PAGED("paged", "Sayfalı Izgara (PAGED_GRID)"),
		DETAIL("detail", "Detay / Miktar Formu (DETAIL_FORM)"),
		FIXED("fixed", "Sabit Slot Düzeni (FIXED_GRID)"),
		GALLERY("gallery", "PNG Ada Galerisi (PAGED_GRID)"),
		PREVIEW_3D("preview3d", "3D Yapı Önizleme (DETAIL_FORM)");

		private final String commandName;
		private final String displayName;
		private final Identifier screenId;
		private final Identifier shortcutId;

		DemoArchetype(String commandName, String displayName) {
			this.commandName = commandName;
			this.displayName = displayName;
			this.screenId = Ogui.id("demo_" + commandName);
			this.shortcutId = Ogui.id("demo_shortcut_" + commandName);
		}

		public String commandName() {
			return commandName;
		}

		public String displayName() {
			return displayName;
		}

		public Identifier screenId() {
			return screenId;
		}

		public Identifier shortcutId() {
			return shortcutId;
		}

		public static Optional<DemoArchetype> fromName(String raw) {
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			String normalized = raw.trim().toLowerCase(Locale.ROOT);
			for (DemoArchetype value : values()) {
				if (value.commandName.equals(normalized)) {
					return Optional.of(value);
				}
			}
			return Optional.empty();
		}
	}

	private record CatalogEntry(String id, String name, String itemId, int price, int stock) {
	}

	private record IslandDemo(
		String id,
		String name,
		String iconItem,
		String badge,
		int topRgb,
		int bottomRgb,
		int landRgb,
		int accentRgb
	) {
	}

	private record DemoMediaBundle(
		Map<String, OguiAssetRef> pngs,
		OguiAssetRef overworldShell,
		OguiAssetRef netherShell
	) {
	}

	private static final List<CatalogEntry> SAMPLE_CATALOG = createSampleCatalog();
	private static final List<IslandDemo> ISLAND_DEMOS = List.of(
		new IslandDemo("classic", "Klasik Skyblock", "minecraft:grass_block", "Başlangıç", 0x38BDF8, 0x0EA5E9, 0x795548, 0x5DBE46),
		new IslandDemo("cherry", "Kiraz Çiçeği", "minecraft:cherry_sapling", "Egzotik", 0xF472B6, 0x93C5FD, 0x6D4C41, 0xF9A8D4),
		new IslandDemo("bamboo", "Bambu Vadisi", "minecraft:bamboo", "Doğa", 0x2DD4BF, 0x38BDF8, 0x5D4037, 0x4ADE80),
		new IslandDemo("nether", "Kızıl Nether", "minecraft:crimson_fungus", "Zorlu", 0x7F1D1D, 0xDC2626, 0x450A0A, 0xF97316)
	);
	private static volatile boolean registeredOnService;
	private static volatile @org.jspecify.annotations.Nullable DemoMediaBundle serverMediaBundle;
	private static volatile @org.jspecify.annotations.Nullable DemoMediaBundle localMediaBundle;

	private OguiDemoScreens() {
	}

	public static void ensureRegisteredOnService() {
		if (registeredOnService) {
			return;
		}
		OguiService service = Ogui.service();
		for (DemoArchetype archetype : DemoArchetype.values()) {
			service.registerScreen(archetype.screenId(), ctx -> buildFromServerContext(archetype, ctx));
		}
		service.registerShortcut(new OguiShortcut(
			DemoArchetype.TABBED.shortcutId(),
			OguiText.nonItalic("Demo: Sekmeli Panel"),
			OguiText.nonItalic("TABBED_PANEL örneğini sunucu oturumuyla açar", ChatFormatting.GRAY),
			player -> service.openScreen(player, DemoArchetype.TABBED.screenId())
		));
		service.registerShortcut(new OguiShortcut(
			DemoArchetype.PAGED.shortcutId(),
			OguiText.nonItalic("Demo: Sayfalı Katalog"),
			OguiText.nonItalic("PAGED_GRID örneğini sunucu oturumuyla açar", ChatFormatting.GRAY),
			player -> service.openScreen(player, DemoArchetype.PAGED.screenId())
		));
		service.registerShortcut(new OguiShortcut(
			DemoArchetype.DETAIL.shortcutId(),
			OguiText.nonItalic("Demo: Detay & Miktar Formu"),
			OguiText.nonItalic("DETAIL_FORM örneğini sunucu oturumuyla açar", ChatFormatting.GRAY),
			player -> service.openScreen(player, DemoArchetype.DETAIL.screenId())
		));
		service.registerShortcut(new OguiShortcut(
			DemoArchetype.FIXED.shortcutId(),
			OguiText.nonItalic("Demo: Sabit Grid"),
			OguiText.nonItalic("FIXED_GRID örneğini sunucu oturumuyla açar", ChatFormatting.GRAY),
			player -> service.openScreen(player, DemoArchetype.FIXED.screenId())
		));
		service.registerShortcut(new OguiShortcut(
			DemoArchetype.GALLERY.shortcutId(),
			OguiText.nonItalic("Demo: PNG Galeri -> 3D"),
			OguiText.nonItalic("PNG ada galerisi + 3D önizleme vitrini", ChatFormatting.GRAY),
			player -> service.openScreen(player, DemoArchetype.GALLERY.screenId())
		));
		registeredOnService = true;
	}

	private static OguiScreenModel buildFromServerContext(DemoArchetype archetype, OguiBuildContext ctx) {
		ServerPlayer viewer = ctx.viewer();
		OguiOpenRequest req = new OguiOpenRequest(
			archetype.screenId(),
			ctx.contextKey(),
			ctx.requestedPage(),
			ctx.state(),
			null
		);
		return buildModel(archetype, viewer.getUUID(), viewer.getScoreboardName(), req, true);
	}

	public static OguiScreenModel buildModel(
		DemoArchetype archetype,
		UUID viewerUuid,
		String viewerName,
		OguiOpenRequest request
	) {
		return buildModel(archetype, viewerUuid, viewerName, request, false);
	}

	public static OguiScreenModel buildModel(
		DemoArchetype archetype,
		UUID viewerUuid,
		String viewerName,
		OguiOpenRequest request,
		boolean serverMode
	) {
		return switch (archetype) {
			case TABBED -> buildTabbedPanel(request);
			case PAGED -> buildPagedGrid(request);
			case DETAIL -> buildDetailForm(request);
			case FIXED -> buildFixedGrid(viewerUuid, viewerName, request);
			case GALLERY -> buildIslandGallery(request, serverMode);
			case PREVIEW_3D -> buildStructurePreview(request, serverMode);
		};
	}

	private static DemoMediaBundle ensureMediaBundle(boolean serverMode) {
		if (serverMode) {
			DemoMediaBundle cached = serverMediaBundle;
			if (cached != null) {
				return cached;
			}
			OguiService service = Ogui.service();
			DemoMediaBundle created = createMediaBundle(service);
			serverMediaBundle = created;
			return created;
		}
		DemoMediaBundle cached = localMediaBundle;
		if (cached != null) {
			return cached;
		}
		DemoMediaBundle created = createMediaBundle(null);
		localMediaBundle = created;
		return created;
	}

	private static DemoMediaBundle createMediaBundle(@org.jspecify.annotations.Nullable OguiService service) {
		Map<String, OguiAssetRef> pngs = new LinkedHashMap<>();
		for (IslandDemo demo : ISLAND_DEMOS) {
			byte[] pngBytes = OguiDemoAssets.createIslandThumbnailPng(demo.topRgb(), demo.bottomRgb(), demo.landRgb(), demo.accentRgb());
			pngs.put(demo.id(), OguiDemoAssets.registerDemoPng(service, pngBytes));
		}
		OguiStructureShell overworld = OguiDemoAssets.createClassicSkyblockShell(false);
		OguiStructureShell nether = OguiDemoAssets.createClassicSkyblockShell(true);
		return new DemoMediaBundle(
			Map.copyOf(pngs),
			OguiDemoAssets.registerDemoShell(service, overworld),
			OguiDemoAssets.registerDemoShell(service, nether)
		);
	}

	private static OguiScreenModel buildIslandGallery(OguiOpenRequest request, boolean serverMode) {
		DemoMediaBundle media = ensureMediaBundle(serverMode);
		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFF38BDF8)
			.subtitle("Client Test • PNG Ada Galerisi (/is create akışı)")
			.build();
		OguiScreenModel.PagedGridBuilder builder = OguiScreenModel.pagedGrid(OguiText.nonItalic("Ada Seçimi (PNG Galeri)", ChatFormatting.AQUA))
			.theme(theme);
		for (IslandDemo demo : ISLAND_DEMOS) {
			OguiAssetRef thumb = media.pngs().get(demo.id());
			builder.addEntry(OguiWidget.button("island_" + demo.id(), OguiIcon.item(demo.iconItem()), OguiText.nonItalic(demo.name(), ChatFormatting.WHITE))
				.previewImage(thumb)
				.badge(demo.badge(), ChatFormatting.GOLD)
				.loreLine("3D önizleme için tıkla", ChatFormatting.GRAY)
				.onClick(ctx -> ctx.openScreen(DemoArchetype.PREVIEW_3D.screenId(), demo.id()))
				.build());
		}
		return builder.build();
	}

	private static OguiScreenModel buildStructurePreview(OguiOpenRequest request, boolean serverMode) {
		DemoMediaBundle media = ensureMediaBundle(serverMode);
		String islandId = request.contextKey() != null ? request.contextKey() : "classic";
		IslandDemo info = ISLAND_DEMOS.stream()
			.filter(d -> d.id().equals(islandId))
			.findFirst()
			.orElse(ISLAND_DEMOS.getFirst());

		String dim = request.state().getOrDefault("dim", "nether".equals(islandId) ? "nether" : "overworld");
		OguiAssetRef activeShell = "nether".equals(dim) ? media.netherShell() : media.overworldShell();

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFF22C55E)
			.subtitle("3D Etkileşimli Önizleme • Fare ile Döndür & Yakınlaştır")
			.build();

		return OguiScreenModel.detailForm(OguiText.nonItalic("Ada Önizlemesi: " + info.name(), ChatFormatting.AQUA))
			.theme(theme)
			.heroCard(OguiWidget.structurePreview("island_3d_view", OguiIcon.item(info.iconItem()), OguiText.nonItalic(info.name(), ChatFormatting.GREEN))
				.structureAsset(activeShell)
				.initialRotation(42.0F, 26.0F)
				.autoRotate(false)
				.loreLine("Boyut: " + ("nether".equals(dim) ? "Nether" : "Overworld"), ChatFormatting.GRAY)
				.build())
			.addControl(OguiWidget.cycle("preview_dim", OguiIcon.item("minecraft:ender_pearl"), OguiText.nonItalic("Dünya Önizlemesi"))
				.addOption("overworld", OguiText.nonItalic("Overworld (Başlangıç)", ChatFormatting.GREEN))
				.addOption("nether", OguiText.nonItalic("Nether (Kızıl)", ChatFormatting.RED))
				.selectedId(dim)
				.onCycle((ctx, opt) -> ctx.refreshWithState("dim", opt.id()))
				.build())
			.addAction(OguiWidget.button("create_island_confirm", OguiIcon.item("minecraft:emerald_block"), OguiText.nonItalic("Bu Adayı Oluştur", ChatFormatting.GREEN))
				.confirm("confirm_create_" + info.id(), "Oluşturmayı onaylamak için tekrar tıkla!")
				.onClick(ctx -> ctx.sendFeedback(true, "Demo: '" + info.name() + "' adası (" + dim + ") oluşturulurdu!"))
				.build())
			.build();
	}

	private static OguiScreenModel buildTabbedPanel(OguiOpenRequest request) {
		Map<String, String> state = request.state();
		String activeTab = state.getOrDefault("tab", "market");
		String query = state.getOrDefault("search", "").trim().toLowerCase(Locale.ROOT);
		String sort = state.getOrDefault("sort", "name");
		boolean inStockOnly = Boolean.parseBoolean(state.getOrDefault("in_stock", "false"));

		List<CatalogEntry> filtered = new ArrayList<>();
		for (CatalogEntry entry : SAMPLE_CATALOG) {
			if (!tabMatches(activeTab, entry)) {
				continue;
			}
			if (inStockOnly && entry.stock() <= 0) {
				continue;
			}
			if (!query.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			filtered.add(entry);
		}
		if ("price_desc".equals(sort)) {
			filtered.sort(Comparator.comparingInt(CatalogEntry::price).reversed());
		} else if ("stock_desc".equals(sort)) {
			filtered.sort(Comparator.comparingInt(CatalogEntry::stock).reversed());
		} else {
			filtered.sort(Comparator.comparing(CatalogEntry::name));
		}

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFF38BDF8)
			.subtitle("Client Test • Sekmeli Panel (TABBED_PANEL)")
			.addSummaryCard(OguiSummaryCard.of(
				OguiIcon.item("minecraft:sunflower"),
				OguiText.nonItalic("Önizleme Durumu", ChatFormatting.AQUA),
				List.of(
					OguiText.nonItalic("Sekme: " + activeTab, ChatFormatting.GRAY),
					OguiText.nonItalic("Sonuç: " + filtered.size() + " öğe", ChatFormatting.GREEN)
				)
			))
			.addSummaryCard(OguiSummaryCard.of(
				OguiIcon.item("minecraft:compass"),
				OguiText.nonItalic("Filtreler", ChatFormatting.GOLD),
				List.of(
					OguiText.nonItalic("Sıralama: " + sort, ChatFormatting.GRAY),
					OguiText.nonItalic("Stokta: " + (inStockOnly ? "Evet" : "Tümü"), ChatFormatting.YELLOW)
				)
			))
			.build();

		OguiScreenModel.TabbedPanelBuilder builder = OguiScreenModel.tabbedPanel(OguiText.nonItalic("Ogui Demo Paneli", ChatFormatting.AQUA))
			.theme(theme)
			.activeTabId(activeTab)
			.addTab(OguiTab.builder("market", OguiIcon.item("minecraft:emerald"), OguiText.nonItalic("Market", ChatFormatting.GREEN))
				.lore(OguiText.nonItalic("Değerli maden ve ticaret öğeleri", ChatFormatting.GRAY))
				.onSelect(ctx -> ctx.refreshWithState("tab", "market"))
				.build())
			.addTab(OguiTab.builder("gear", OguiIcon.item("minecraft:diamond_sword"), OguiText.nonItalic("Ekipman", ChatFormatting.AQUA))
				.lore(OguiText.nonItalic("Araçlar, zırhlar ve savaş ekipmanları", ChatFormatting.GRAY))
				.onSelect(ctx -> ctx.refreshWithState("tab", "gear"))
				.build())
			.addTab(OguiTab.builder("blocks", OguiIcon.item("minecraft:bricks"), OguiText.nonItalic("Yapı", ChatFormatting.GOLD))
				.lore(OguiText.nonItalic("Dekoratif ve yapı blokları", ChatFormatting.GRAY))
				.onSelect(ctx -> ctx.refreshWithState("tab", "blocks"))
				.build())
			.addHeaderWidget(OguiWidget.textInput("search", OguiIcon.item("minecraft:name_tag"), OguiText.nonItalic("Ara"))
				.placeholder("Öğe adı yaz...")
				.value(state.getOrDefault("search", ""))
				.liveDebounce(true)
				.onSubmit((ctx, text) -> ctx.refreshWithState("search", text))
				.build())
			.addHeaderWidget(OguiWidget.cycle("sort_mode", OguiIcon.item("minecraft:hopper"), OguiText.nonItalic("Sırala", ChatFormatting.YELLOW))
				.addOption("name", OguiText.nonItalic("Ada Göre (A-Z)", ChatFormatting.WHITE))
				.addOption("price_desc", OguiText.nonItalic("Fiyat (Yüksek → Düşük)", ChatFormatting.GOLD))
				.addOption("stock_desc", OguiText.nonItalic("Stok (Yüksek → Düşük)", ChatFormatting.GREEN))
				.selectedId(sort)
				.onCycle((ctx, opt) -> ctx.refreshWithState("sort", opt.id()))
				.build())
			.addHeaderWidget(OguiWidget.toggle("stock_filter", OguiIcon.item("minecraft:redstone_torch"), OguiText.nonItalic("Sadece Stoktakiler"))
				.activeIcon(OguiIcon.item("minecraft:emerald_block").withGlowing(true))
				.value(inStockOnly)
				.labels(OguiText.nonItalic("Açık", ChatFormatting.GREEN), OguiText.nonItalic("Kapalı", ChatFormatting.GRAY))
				.onToggle((ctx, next) -> ctx.refreshWithState("in_stock", Boolean.toString(next)))
				.build())
			.addFooterWidget(OguiWidget.button("reset_filters", OguiIcon.item("minecraft:barrier"), OguiText.nonItalic("Filtreleri Sıfırla", ChatFormatting.RED))
				.confirm("confirm_reset_filters", "Sıfırlamak için tekrar tıkla!")
				.onClick(ctx -> {
					ctx.sendFeedback(true, "Filtreler sıfırlandı.");
					ctx.openScreen(OguiOpenRequest.of(DemoArchetype.TABBED.screenId()));
				})
				.build())
			.emptyState(OguiWidget.button("empty_hint", OguiIcon.item("minecraft:paper"), OguiText.nonItalic("Eşleşen Öğe Yok", ChatFormatting.YELLOW))
				.loreLine("Aramayı temizlemek için tıkla", ChatFormatting.GRAY)
				.onClick(ctx -> ctx.refreshWithoutState("search"))
				.build());

		for (CatalogEntry entry : filtered) {
			builder.addEntry(OguiWidget.button(
				"item_" + entry.id(),
				OguiIcon.item(entry.itemId()),
				OguiText.nonItalic(entry.name(), ChatFormatting.WHITE)
			)
				.badge(entry.price() + " Coin", entry.stock() > 0 ? ChatFormatting.GOLD : ChatFormatting.RED)
				.loreLine("Stok: " + (entry.stock() > 0 ? entry.stock() : "Tükendi"), ChatFormatting.GRAY)
				.loreLine("Detay formunu açmak için tıkla", ChatFormatting.AQUA)
				.onClick(ctx -> ctx.openScreen(
					OguiOpenRequest.of(DemoArchetype.DETAIL.screenId(), entry.id())
						.withState("item_name", entry.name())
						.withState("item_id", entry.itemId())
						.withState("unit_price", Integer.toString(entry.price()))
				))
				.build());
		}
		return builder.build();
	}

	private static OguiScreenModel buildPagedGrid(OguiOpenRequest request) {
		Map<String, String> state = request.state();
		boolean emptySimulation = Boolean.parseBoolean(state.getOrDefault("simulate_empty", "false"));
		String filter = state.getOrDefault("category", "all");

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFF22C55E)
			.subtitle("Client Test • Sayfalı Izgara (PAGED_GRID)")
			.addSummaryCard(OguiSummaryCard.of(
				OguiIcon.item("minecraft:chest"),
				OguiText.nonItalic("Katalog Özet", ChatFormatting.GREEN),
				List.of(OguiText.nonItalic("Toplam: " + (emptySimulation ? 0 : SAMPLE_CATALOG.size()) + " kayıt", ChatFormatting.GRAY))
			))
			.build();

		OguiScreenModel.PagedGridBuilder builder = OguiScreenModel.pagedGrid(OguiText.nonItalic("Sayfalı Katalog Örneği", ChatFormatting.GREEN))
			.theme(theme)
			.addHeaderWidget(OguiWidget.cycle("cat_cycle", OguiIcon.item("minecraft:book"), OguiText.nonItalic("Kategori"))
				.addOption("all", OguiText.nonItalic("Tümü", ChatFormatting.WHITE))
				.addOption("market", OguiText.nonItalic("Market", ChatFormatting.GREEN))
				.addOption("gear", OguiText.nonItalic("Ekipman", ChatFormatting.AQUA))
				.addOption("blocks", OguiText.nonItalic("Yapı", ChatFormatting.GOLD))
				.selectedId(filter)
				.onCycle((ctx, opt) -> ctx.refreshWithState("category", opt.id()))
				.build())
			.addHeaderWidget(OguiWidget.toggle("toggle_empty", OguiIcon.item("minecraft:structure_void"), OguiText.nonItalic("Boş Durum Simülasyonu"))
				.value(emptySimulation)
				.onToggle((ctx, next) -> ctx.refreshWithState("simulate_empty", Boolean.toString(next)))
				.build())
			.addFooterWidget(OguiWidget.button("open_fixed_btn", OguiIcon.item("minecraft:crafting_table"), OguiText.nonItalic("Sabit Grid'e Geç", ChatFormatting.AQUA))
				.onClick(ctx -> ctx.openScreen(DemoArchetype.FIXED.screenId(), "from_paged"))
				.build())
			.emptyState(OguiWidget.button("paged_empty", OguiIcon.item("minecraft:barrier"), OguiText.nonItalic("Gösterilecek Kayıt Yok", ChatFormatting.RED))
				.loreLine("Boş durum simülasyonunu kapatmak için tıkla", ChatFormatting.GRAY)
				.onClick(ctx -> ctx.refreshWithState("simulate_empty", "false"))
				.build());

		if (!emptySimulation) {
			for (CatalogEntry entry : SAMPLE_CATALOG) {
				if (!"all".equals(filter) && !tabMatches(filter, entry)) {
					continue;
				}
				builder.addEntry(OguiWidget.button(
					"grid_" + entry.id(),
					OguiIcon.item(entry.itemId()),
					OguiText.nonItalic(entry.name(), ChatFormatting.WHITE)
				)
					.badge(entry.price() + "c", ChatFormatting.GOLD)
					.loreLine("Tıklayınca DETAIL_FORM açılır", ChatFormatting.GRAY)
					.onClick(ctx -> ctx.openScreen(
						OguiOpenRequest.of(DemoArchetype.DETAIL.screenId(), entry.id())
							.withState("item_name", entry.name())
							.withState("item_id", entry.itemId())
							.withState("unit_price", Integer.toString(entry.price()))
					))
					.build());
			}
		}
		return builder.build();
	}

	private static OguiScreenModel buildDetailForm(OguiOpenRequest request) {
		Map<String, String> state = request.state();
		String itemName = state.getOrDefault("item_name", "Nether Yıldızı");
		String itemId = state.getOrDefault("item_id", "minecraft:nether_star");
		int unitPrice = parseInt(state.get("unit_price"), 250);
		int qty = parseInt(state.get("qty"), 16);
		boolean express = Boolean.parseBoolean(state.getOrDefault("express", "false"));
		String payment = state.getOrDefault("payment", "coin");
		String note = state.getOrDefault("note", "");

		int totalCost = (qty * unitPrice) + (express ? 50 : 0);

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFFF59E0B)
			.subtitle("Client Test • Detay & Form (DETAIL_FORM)")
			.build();

		return OguiScreenModel.detailForm(OguiText.nonItalic("İşlem Detayı: " + itemName, ChatFormatting.GOLD))
			.theme(theme)
			.heroCard(OguiWidget.button("detail_hero", OguiIcon.item(itemId).withGlowing(true), OguiText.nonItalic(itemName, ChatFormatting.AQUA))
				.badge("Birim: " + unitPrice + " Coin", ChatFormatting.GOLD)
				.loreLine("Seçili miktar: x" + qty, ChatFormatting.GRAY)
				.loreLine("Ödeme: " + payment + (express ? " (+Hızlı Teslimat)" : ""), ChatFormatting.YELLOW)
				.build())
			.addInfoBadge(OguiWidget.button("badge_cost", OguiIcon.item("minecraft:gold_ingot"), OguiText.nonItalic("Toplam Tutar", ChatFormatting.GOLD))
				.badge(totalCost + " Coin", ChatFormatting.GREEN)
				.build())
			.addInfoBadge(OguiWidget.button("badge_unit", OguiIcon.item("minecraft:emerald"), OguiText.nonItalic("Birim Fiyat", ChatFormatting.GREEN))
				.badge(unitPrice + " Coin", ChatFormatting.WHITE)
				.build())
			.addControl(OguiWidget.quantity("qty_selector", OguiIcon.item(itemId), OguiText.nonItalic("Miktar Seçimi"))
				.value(qty)
				.range(1, 640)
				.steps(1, 16, 64)
				.summaryFormatter(val -> OguiText.nonItalic("Ara toplam: " + (val * unitPrice) + " Coin", ChatFormatting.GOLD))
				.onChange((ctx, nextQty) -> ctx.refreshWithState("qty", Integer.toString(nextQty)))
				.build())
			.addControl(OguiWidget.textInput("order_note", OguiIcon.item("minecraft:writable_book"), OguiText.nonItalic("Sipariş Notu"))
				.placeholder("İsteğe bağlı not yaz...")
				.value(note)
				.liveDebounce(true)
				.onSubmit((ctx, text) -> {
					ctx.sendFeedback(true, "Not kaydedildi: " + (text.isBlank() ? "(boş)" : text));
					ctx.refreshWithState("note", text);
				})
				.build())
			.addControl(OguiWidget.toggle("express_toggle", OguiIcon.item("minecraft:sugar"), OguiText.nonItalic("Hızlı Teslimat (+50c)"))
				.value(express)
				.labels(OguiText.nonItalic("Açık (+50c)", ChatFormatting.GREEN), OguiText.nonItalic("Standart", ChatFormatting.GRAY))
				.onToggle((ctx, next) -> ctx.refreshWithState("express", Boolean.toString(next)))
				.build())
			.addControl(OguiWidget.cycle("payment_cycle", OguiIcon.item("minecraft:ender_chest"), OguiText.nonItalic("Ödeme Yöntemi"))
				.addOption("coin", OguiText.nonItalic("Cüzdan (Coin)", ChatFormatting.GOLD))
				.addOption("bank", OguiText.nonItalic("Banka Hesabı", ChatFormatting.AQUA))
				.addOption("token", OguiText.nonItalic("Pazar Jetonu", ChatFormatting.LIGHT_PURPLE))
				.selectedId(payment)
				.onCycle((ctx, opt) -> ctx.refreshWithState("payment", opt.id()))
				.build())
			.addAction(OguiWidget.button("buy_confirm_btn", OguiIcon.item("minecraft:lime_dye"), OguiText.nonItalic("Siparişi Onayla", ChatFormatting.GREEN))
				.badge(totalCost + "c", ChatFormatting.GOLD)
				.confirm("confirm_demo_buy", "Onaylamak için tekrar tıkla!")
				.onClick(ctx -> ctx.sendFeedback(
					true,
					"Simülasyon: x" + qty + " " + itemName + " (" + totalCost + " Coin) onaylandı!"
				))
				.build())
			.addAction(OguiWidget.button("reset_form_btn", OguiIcon.item("minecraft:bucket"), OguiText.nonItalic("Formu Sıfırla", ChatFormatting.YELLOW))
				.onClick(ctx -> {
					ctx.sendFeedback(true, "Form varsayılanlara döndü.");
					ctx.refreshWithStates(Map.of("qty", "1", "express", "false", "payment", "coin", "note", ""));
				})
				.build())
			.build();
	}

	private static OguiScreenModel buildFixedGrid(UUID viewerUuid, String viewerName, OguiOpenRequest request) {
		Map<String, String> state = request.state();
		boolean alertsOn = Boolean.parseBoolean(state.getOrDefault("alerts", "true"));
		int clicks = parseInt(state.get("clicks"), 0);

		OguiTheme theme = OguiTheme.builder()
			.accentColor(0xFFA855F7)
			.subtitle("Client Test • Sabit 9x4 Slot Düzeni (FIXED_GRID)")
			.build();

		return OguiScreenModel.fixedGrid(OguiText.nonItalic("Sabit Slot Izgarası (4 Satır)", ChatFormatting.LIGHT_PURPLE), 4)
			.theme(theme)
			.slot(4, OguiWidget.button("player_profile", OguiIcon.playerHead(viewerUuid), OguiText.nonItalic(viewerName, ChatFormatting.AQUA))
				.badge("Oyuncu Kafası", ChatFormatting.LIGHT_PURPLE)
				.loreLine("Tık sayısı: " + clicks, ChatFormatting.GRAY)
				.onClick(ctx -> {
					int next = clicks + 1;
					ctx.sendFeedback(true, "Profil kartına tıklandı (#" + next + ")");
					ctx.refreshWithState("clicks", Integer.toString(next));
				})
				.build())
			.slot(11, OguiWidget.button("sample_success", OguiIcon.item("minecraft:emerald").withGlowing(true), OguiText.nonItalic("Başarı Banner Testi", ChatFormatting.GREEN))
				.loreLine("Üst/alt banner ve başarı sesi çalar", ChatFormatting.GRAY)
				.onClick(ctx -> ctx.sendFeedback(true, "Başarılı etkileşim testi!"))
				.build())
			.slot(13, OguiWidget.toggle("sample_alerts", OguiIcon.item("minecraft:bell"), OguiText.nonItalic("Bildirimler"))
				.value(alertsOn)
				.onToggle((ctx, next) -> {
					ctx.sendFeedback(next, "Bildirimler " + (next ? "açıldı" : "kapatıldı"));
					ctx.refreshWithState("alerts", Boolean.toString(next));
				})
				.build())
			.slot(15, OguiWidget.button("sample_error", OguiIcon.item("minecraft:redstone"), OguiText.nonItalic("Hata Banner Testi", ChatFormatting.RED))
				.loreLine("Kırmızı hata banner'ı ve uyarı sesi gösterir", ChatFormatting.GRAY)
				.onClick(ctx -> ctx.sendFeedback(false, "Örnek hata bildirimi!"))
				.build())
			.slot(22, OguiWidget.button("danger_reset", OguiIcon.item("minecraft:tnt"), OguiText.nonItalic("Sayacı Sıfırla (2-Tık)", ChatFormatting.RED))
				.confirm("confirm_fixed_reset", "Sıfırlamak için tekrar tıkla!")
				.onClick(ctx -> {
					ctx.sendFeedback(true, "Sayaç sıfırlandı.");
					ctx.refreshWithState("clicks", "0");
				})
				.build())
			.build();
	}

	private static boolean tabMatches(String tab, CatalogEntry entry) {
		int idx = Integer.parseInt(entry.id());
		return switch (tab) {
			case "gear" -> idx % 3 == 1;
			case "blocks" -> idx % 3 == 2;
			default -> idx % 3 == 0;
		};
	}

	private static int parseInt(String raw, int fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static List<CatalogEntry> createSampleCatalog() {
		String[] items = {
			"minecraft:diamond", "minecraft:diamond_pickaxe", "minecraft:stone_bricks",
			"minecraft:emerald", "minecraft:diamond_sword", "minecraft:oak_planks",
			"minecraft:gold_ingot", "minecraft:iron_chestplate", "minecraft:glass",
			"minecraft:netherite_scrap", "minecraft:bow", "minecraft:terracotta",
			"minecraft:amethyst_shard", "minecraft:shield", "minecraft:quartz_block"
		};
		String[] names = {
			"Elmas", "Elmas Kazma", "Taş Tuğla",
			"Zümrüt", "Elmas Kılıç", "Meşe Kalas",
			"Altın Külçe", "Demir Göğüslük", "Cam",
			"Netherite Hurdası", "Yay", "Terakota",
			"Ametist Parçası", "Kalkan", "Kuvars Bloğu"
		};
		List<CatalogEntry> list = new ArrayList<>(90);
		for (int i = 0; i < 90; i++) {
			int base = i % items.length;
			int tier = i / items.length + 1;
			int price = (base + 1) * 15 * tier;
			int stock = (i % 7 == 0) ? 0 : (16 * tier + base);
			String suffix = tier > 1 ? " #" + tier : "";
			list.add(new CatalogEntry(Integer.toString(i), names[base] + suffix, items[base], price, stock));
		}
		return List.copyOf(list);
	}
}
