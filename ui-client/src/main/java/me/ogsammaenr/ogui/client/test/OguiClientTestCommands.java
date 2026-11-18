package me.ogsammaenr.ogui.client.test;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiService;
import me.ogsammaenr.ogui.client.OguiClientPreferences;
import me.ogsammaenr.ogui.client.OguiClientSessionState;
import me.ogsammaenr.ogui.client.OguiPreferencesScreen;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.runtime.OguiSessionManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Registers the client-side {@code /oguitest} command tree for interactive testing of
 * {@code ogui-client} screens, preferences, quick-menu launcher, and singleplayer {@code ogui-api} sessions.
 */
public final class OguiClientTestCommands {
	private static final AtomicReference<@Nullable Supplier<@Nullable Screen>> PENDING_SCREEN = new AtomicReference<>();

	private OguiClientTestCommands() {
	}

	public static void register(
		OguiClientPreferences preferences,
		OguiClientSessionState sessionState,
		KeyMapping quickMenuKey
	) {
		OguiClientPreviewHarness previewHarness = new OguiClientPreviewHarness(preferences);

		// Defer Screen openings until END_CLIENT_TICK because ChatScreen clears gui.screen after command dispatch.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Supplier<@Nullable Screen> supplier = PENDING_SCREEN.getAndSet(null);
			if (supplier != null) {
				Screen next = supplier.get();
				if (next != null) {
					mc.gui.setScreen(next);
				}
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommands.literal("oguitest")
				.executes(ctx -> printHelp(ctx.getSource()))
				.then(ClientCommands.literal("help")
					.executes(ctx -> printHelp(ctx.getSource())))
				.then(ClientCommands.literal("open")
					.then(ClientCommands.argument("archetype", StringArgumentType.word())
						.suggests(OguiClientTestCommands::suggestArchetypes)
						.executes(ctx -> openPreview(ctx, previewHarness))))
				.then(ClientCommands.literal("screen")
					.then(ClientCommands.argument("archetype", StringArgumentType.word())
						.suggests(OguiClientTestCommands::suggestArchetypes)
						.executes(ctx -> openPreview(ctx, previewHarness))))
				.then(ClientCommands.literal("prefs")
					.executes(ctx -> {
						PENDING_SCREEN.set(() -> new OguiPreferencesScreen(null, preferences));
						ctx.getSource().sendFeedback(Component.literal("[ogui] Tercihler ekranı açılıyor...").withStyle(ChatFormatting.AQUA));
						return 1;
					}))
				.then(ClientCommands.literal("launcher")
					.executes(ctx -> {
						Minecraft mc = ctx.getSource().getClient();
						PENDING_SCREEN.set(() -> previewHarness.createPreviewLauncher(mc));
						ctx.getSource().sendFeedback(Component.literal("[ogui] Hızlı Menü önizlemesi açılıyor...").withStyle(ChatFormatting.AQUA));
						return 1;
					}))
				.then(ClientCommands.literal("status")
					.executes(ctx -> printStatus(ctx.getSource(), preferences, sessionState, quickMenuKey)))
				.then(ClientCommands.literal("live")
					.then(ClientCommands.literal("shortcuts")
						.executes(ctx -> registerLiveShortcuts(ctx.getSource(), quickMenuKey)))
					.then(ClientCommands.argument("archetype", StringArgumentType.word())
						.suggests(OguiClientTestCommands::suggestLiveArgs)
						.executes(ctx -> openLiveServerScreen(ctx))));

			dispatcher.register(root);
		});
	}

	private static int printHelp(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("=== Ogui Client Test Komutları ===").withStyle(ChatFormatting.AQUA));
		source.sendFeedback(Component.literal("/oguitest open <tabbed|paged|detail|fixed|gallery|preview3d>").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" - Etkileşimli client UI önizlemesi açar (her yerde çalışır)").withStyle(ChatFormatting.GRAY)));
		source.sendFeedback(Component.literal("/oguitest prefs").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" - Tema / UI Ölçeği / Ses tercihlerini açar").withStyle(ChatFormatting.GRAY)));
		source.sendFeedback(Component.literal("/oguitest launcher").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" - Örnek Hızlı Menü başlatıcısını açar").withStyle(ChatFormatting.GRAY)));
		source.sendFeedback(Component.literal("/oguitest status").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" - El sıkışma, kısayol ve tercih durumunu gösterir").withStyle(ChatFormatting.GRAY)));
		source.sendFeedback(Component.literal("/oguitest live <tabbed|paged|detail|fixed|gallery|preview3d|shortcuts>").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(" - Tek oyunculuda gerçek sunucu paketleriyle açar").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	private static int openPreview(CommandContext<FabricClientCommandSource> ctx, OguiClientPreviewHarness harness) {
		String raw = StringArgumentType.getString(ctx, "archetype");
		var parsed = OguiDemoScreens.DemoArchetype.fromName(raw);
		if (parsed.isEmpty()) {
			ctx.getSource().sendError(Component.literal("[ogui] Geçersiz arketip: '" + raw + "'. Geçerliler: tabbed, paged, detail, fixed, gallery, preview3d"));
			return 0;
		}
		OguiDemoScreens.DemoArchetype archetype = parsed.get();
		Minecraft mc = ctx.getSource().getClient();
		PENDING_SCREEN.set(() -> harness.createPreviewScreen(mc, archetype));
		ctx.getSource().sendFeedback(Component.literal("[ogui] Client önizlemesi açılıyor: " + archetype.displayName()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int printStatus(
		FabricClientCommandSource source,
		OguiClientPreferences preferences,
		OguiClientSessionState sessionState,
		KeyMapping quickMenuKey
	) {
		String keyText = quickMenuKey.getTranslatedKeyMessage().getString();
		String handshakeText = sessionState.handshakeAccepted()
			? "Uyumlu (v" + sessionState.serverProtocolVersion() + ")"
			: (sessionState.helloSentThisConnection() ? "Fallback / Uyumsuz" : "Bekleniyor");

		source.sendFeedback(Component.literal("=== Ogui Durumu ===").withStyle(ChatFormatting.AQUA));
		source.sendFeedback(Component.literal("El Sıkışma: " + handshakeText
			+ " | Sunucu Kısayolları: " + sessionState.shortcuts().size()).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.literal("Hızlı Menü Tuşu: [" + keyText + "]"
			+ " | Tema: " + preferences.themePreset().displayName()
			+ " | Ölçek: " + preferences.uiScale().displayName()
			+ " | Ses: " + (preferences.uiSoundsEnabled() ? "Açık" : "Kapalı")).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int registerLiveShortcuts(FabricClientCommandSource source, KeyMapping quickMenuKey) {
		Minecraft mc = source.getClient();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			source.sendError(Component.literal("[ogui] '/oguitest live' yalnızca Tek Oyunculu (Singleplayer) dünyada kullanılabilir. Sunucuda '/oguitest open ...' kullan."));
			return 0;
		}
		OguiDemoScreens.ensureRegisteredOnService();
		var playerId = source.getPlayer().getUUID();
		String modVersion = FabricLoader.getInstance().getModContainer("ogui-client")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("1.0.0");
		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
			if (sp != null) {
				OguiSessionManager.instance().handleClientHello(sp, new C2SClientHelloPayload(Ogui.PROTOCOL_VERSION, modVersion));
			}
		});
		String keyText = quickMenuKey.getTranslatedKeyMessage().getString();
		source.sendFeedback(Component.literal("[ogui] Demo sunucu kısayolları yüklendi! [" + keyText + "] tuşuna basarak Hızlı Menü'yü test edebilirsin.").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int openLiveServerScreen(CommandContext<FabricClientCommandSource> ctx) {
		String raw = StringArgumentType.getString(ctx, "archetype");
		var parsed = OguiDemoScreens.DemoArchetype.fromName(raw);
		if (parsed.isEmpty()) {
			ctx.getSource().sendError(Component.literal("[ogui] Geçersiz arketip: '" + raw + "'. Geçerliler: tabbed, paged, detail, fixed, gallery, preview3d, shortcuts"));
			return 0;
		}
		Minecraft mc = ctx.getSource().getClient();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			ctx.getSource().sendError(Component.literal("[ogui] '/oguitest live' yalnızca Tek Oyunculu (Singleplayer) dünyada kullanılabilir. Sunucuda '/oguitest open ...' kullan."));
			return 0;
		}

		OguiDemoScreens.DemoArchetype archetype = parsed.get();
		OguiDemoScreens.ensureRegisteredOnService();
		OguiService service = Ogui.service();
		var playerId = ctx.getSource().getPlayer().getUUID();
		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
			if (sp != null) {
				service.openScreen(sp, archetype.screenId());
			}
		});
		ctx.getSource().sendFeedback(Component.literal("[ogui] Canlı sunucu oturumu açılıyor: " + archetype.displayName()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestArchetypes(
		CommandContext<FabricClientCommandSource> ctx,
		SuggestionsBuilder builder
	) {
		for (OguiDemoScreens.DemoArchetype archetype : OguiDemoScreens.DemoArchetype.values()) {
			builder.suggest(archetype.commandName());
		}
		return builder.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestLiveArgs(
		CommandContext<FabricClientCommandSource> ctx,
		SuggestionsBuilder builder
	) {
		for (OguiDemoScreens.DemoArchetype archetype : OguiDemoScreens.DemoArchetype.values()) {
			builder.suggest(archetype.commandName());
		}
		builder.suggest("shortcuts");
		return builder.buildFuture();
	}
}
