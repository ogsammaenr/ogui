package me.ogsammaenr.ogui.runtime;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiBuildContext;
import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiClickContext;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiScreenProvider;
import me.ogsammaenr.ogui.api.OguiService;
import me.ogsammaenr.ogui.api.OguiShortcut;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SCloseNotifyPayload;
import me.ogsammaenr.ogui.network.C2SRequestAssetPayload;
import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.C2SWidgetInteractPayload;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.network.S2CAssetUnavailablePayload;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import me.ogsammaenr.ogui.vanilla.FakeSignInputManager;
import me.ogsammaenr.ogui.vanilla.OguiChestLayout;
import me.ogsammaenr.ogui.vanilla.OguiChestMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Singleton server-authoritative {@link OguiService} coordinating protocol handshake,
 * active RAM sessions, client DTO dispatch, and anti-dupe vanilla {@code ChestMenu} fallback.
 */
public final class OguiSessionManager implements OguiService {
	private static final OguiSessionManager INSTANCE = new OguiSessionManager();

	private final Map<Identifier, OguiScreenProvider> screens = new ConcurrentHashMap<>();
	private final Map<Identifier, OguiShortcut> shortcuts = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<UUID, OguiSession> activeSessions = new ConcurrentHashMap<>();
	private final OguiHandshakeRegistry handshakes = new OguiHandshakeRegistry();
	private final OguiAssetRegistry assetRegistry = new OguiAssetRegistry();

	private OguiSessionManager() {
	}

	public static OguiSessionManager instance() {
		return INSTANCE;
	}

	public OguiHandshakeRegistry handshakes() {
		return handshakes;
	}

	public OguiAssetRegistry assetRegistry() {
		return assetRegistry;
	}

	public @Nullable OguiSession activeSession(UUID playerUuid) {
		return activeSessions.get(playerUuid);
	}

	@Override
	public void registerScreen(Identifier screenId, OguiScreenProvider provider) {
		Objects.requireNonNull(screenId, "screenId");
		Objects.requireNonNull(provider, "provider");
		screens.put(screenId, provider);
	}

	@Override
	public Optional<OguiScreenProvider> findScreen(Identifier screenId) {
		Objects.requireNonNull(screenId, "screenId");
		return Optional.ofNullable(screens.get(screenId));
	}

	@Override
	public void registerShortcut(OguiShortcut shortcut) {
		Objects.requireNonNull(shortcut, "shortcut");
		synchronized (shortcuts) {
			shortcuts.put(shortcut.id(), shortcut);
		}
	}

	@Override
	public Collection<OguiShortcut> shortcuts() {
		synchronized (shortcuts) {
			return List.copyOf(shortcuts.values());
		}
	}

	@Override
	public OguiAssetRef registerPngAsset(byte[] pngBytes) {
		return assetRegistry.registerPng(pngBytes);
	}

	@Override
	public OguiAssetRef registerStructureAsset(OguiStructureShell shell) {
		return assetRegistry.registerStructure(shell);
	}

	@Override
	public Optional<byte[]> findAssetBytes(OguiAssetRef ref) {
		Objects.requireNonNull(ref, "ref");
		return assetRegistry.find(ref.sha256())
			.filter(stored -> stored.ref().kind() == ref.kind())
			.map(stored -> stored.bytes().clone());
	}

	@Override
	public boolean isClientRendererAvailable(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		return player.connection != null
			&& !player.hasDisconnected()
			&& ServerPlayNetworking.canSend(player, S2COpenScreenPayload.TYPE)
			&& handshakes.isCompatible(player.getUUID());
	}

	@Override
	public boolean openScreen(ServerPlayer player, OguiOpenRequest request) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(request, "request");

		Optional<OguiScreenProvider> provider = findScreen(request.screenId());
		if (provider.isEmpty()) {
			return false;
		}

		FakeSignInputManager.instance().cancelAndRestore(player);

		boolean useClientUi = isClientRendererAvailable(player);
		OguiScreenModel model = buildModel(player, provider.get(), request);
		OguiSession session = new OguiSession(UUID.randomUUID(), player.getUUID(), useClientUi, request, model);
		activeSessions.put(player.getUUID(), session);

		if (useClientUi) {
			if (player.containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}
			ScreenSnapshotDto dto = session.toDto();
			assetRegistry.allowAssetsFromSnapshot(player.getUUID(), dto);
			ServerPlayNetworking.send(player, new S2COpenScreenPayload(session.sessionId(), session.revision(), dto));
		} else {
			openChestForSession(player, session);
		}
		return true;
	}

	@Override
	public Optional<OguiOpenRequest> activeRequest(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		return Optional.ofNullable(activeSessions.get(player.getUUID())).map(OguiSession::request);
	}

	@Override
	public void refreshPlayerScreen(ServerPlayer player, Identifier screenId) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(screenId, "screenId");
		OguiSession session = activeSessions.get(player.getUUID());
		if (session == null || !screenId.equals(session.request().screenId())) {
			return;
		}
		refreshSession(player, session, session.request());
	}

	@Override
	public int refreshMatchingScreens(Predicate<OguiOpenRequest> filter) {
		Objects.requireNonNull(filter, "filter");
		if (activeSessions.isEmpty()) {
			return 0;
		}
		List<OguiSession> targets = new ArrayList<>();
		for (OguiSession session : activeSessions.values()) {
			if (filter.test(session.request())) {
				targets.add(session);
			}
		}
		if (targets.isEmpty()) {
			return 0;
		}
		int refreshed = 0;
		for (OguiSession session : targets) {
			MinecraftServer server = findServer(session);
			if (server != null) {
				server.execute(() -> {
					ServerPlayer viewer = server.getPlayerList().getPlayer(session.playerUuid());
					if (viewer != null && activeSessions.get(viewer.getUUID()) == session) {
						refreshSession(viewer, session, session.request());
					}
				});
				refreshed++;
			}
		}
		return refreshed;
	}

	@Override
	public int closeMatchingScreens(
		BiPredicate<UUID, OguiOpenRequest> filter,
		boolean successFeedback,
		@Nullable Component reason
	) {
		Objects.requireNonNull(filter, "filter");
		if (activeSessions.isEmpty()) {
			return 0;
		}
		List<OguiSession> targets = new ArrayList<>();
		for (OguiSession session : activeSessions.values()) {
			if (filter.test(session.playerUuid(), session.request())) {
				targets.add(session);
			}
		}
		if (targets.isEmpty()) {
			return 0;
		}
		int closed = 0;
		for (OguiSession session : targets) {
			MinecraftServer server = findServer(session);
			if (server != null) {
				server.execute(() -> {
					ServerPlayer viewer = server.getPlayerList().getPlayer(session.playerUuid());
					if (viewer != null && activeSessions.get(viewer.getUUID()) == session) {
						if (reason != null) {
							sendFeedback(viewer, successFeedback, reason);
						}
						closePlayerScreen(viewer);
					}
				});
				closed++;
			}
		}
		return closed;
	}

	@Override
	public void closePlayerScreen(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		FakeSignInputManager.instance().cancelAndRestore(player);
		OguiSession removed = activeSessions.remove(player.getUUID());
		if (removed == null) {
			return;
		}
		if (removed.clientModMode()) {
			if (player.connection != null && !player.hasDisconnected()
				&& ServerPlayNetworking.canSend(player, S2CCloseScreenPayload.TYPE)) {
				ServerPlayNetworking.send(player, new S2CCloseScreenPayload(removed.sessionId()));
			}
		} else if (player.containerMenu instanceof OguiChestMenu chest && chest.sessionId().equals(removed.sessionId())) {
			player.closeContainer();
		}
	}

	@Override
	public void sendFeedback(ServerPlayer player, boolean success, Component message) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(message, "message");
		OguiSession session = activeSessions.get(player.getUUID());
		if (session != null && session.clientModMode() && player.connection != null && !player.hasDisconnected()
			&& ServerPlayNetworking.canSend(player, S2CFeedbackPayload.TYPE)) {
			ServerPlayNetworking.send(player, new S2CFeedbackPayload(
				session.sessionId(),
				success,
				message,
				success ? S2CFeedbackPayload.FeedbackSound.SUCCESS : S2CFeedbackPayload.FeedbackSound.ERROR
			));
			return;
		}
		OguiFeedbackSupport.sendChatFeedback(player, success, message);
	}

	public void handleClientHello(ServerPlayer player, C2SClientHelloPayload payload) {
		var cap = handshakes.recordClientHello(player.getUUID(), payload.protocolVersion(), payload.clientModVersion());
		if (ServerPlayNetworking.canSend(player, S2CServerHelloPayload.TYPE)) {
			List<S2CServerHelloPayload.ShortcutInfo> shortcutInfos = cap.compatible()
				? shortcuts().stream().map(s -> new S2CServerHelloPayload.ShortcutInfo(s.id(), s.label(), s.description())).toList()
				: List.of();
			ServerPlayNetworking.send(player, new S2CServerHelloPayload(
				Ogui.PROTOCOL_VERSION,
				modVersion(),
				shortcutInfos
			));
		}
	}

	public void handleClientInteract(ServerPlayer player, C2SWidgetInteractPayload payload) {
		OguiSession session = activeSessions.get(player.getUUID());
		if (session == null || !session.clientModMode()) {
			return;
		}
		if (!session.sessionId().equals(payload.sessionId()) || session.revision() != payload.revision()) {
			return;
		}
		dispatchInteraction(
			player,
			session,
			payload.widgetId(),
			payload.clickAction(),
			payload.inputText(),
			payload.numericValue()
		);
	}

	public void handleClientCloseNotify(ServerPlayer player, C2SCloseNotifyPayload payload) {
		OguiSession current = activeSessions.get(player.getUUID());
		if (current != null && current.clientModMode() && current.sessionId().equals(payload.sessionId())) {
			activeSessions.remove(player.getUUID(), current);
		}
	}

	public void handleShortcutTrigger(ServerPlayer player, C2SShortcutTriggerPayload payload) {
		if (!isClientRendererAvailable(player)) {
			return;
		}
		OguiShortcut shortcut;
		synchronized (shortcuts) {
			shortcut = shortcuts.get(payload.shortcutId());
		}
		if (shortcut != null) {
			shortcut.onTrigger().accept(player);
		}
	}

	public void handleAssetRequest(ServerPlayer player, C2SRequestAssetPayload payload) {
		if (!isClientRendererAvailable(player) || !ServerPlayNetworking.canSend(player, S2CAssetChunkPayload.TYPE)) {
			return;
		}
		String sha256 = payload.sha256();
		if (!assetRegistry.isAllowedForPlayer(player.getUUID(), sha256)) {
			if (ServerPlayNetworking.canSend(player, S2CAssetUnavailablePayload.TYPE)) {
				ServerPlayNetworking.send(player, new S2CAssetUnavailablePayload(sha256));
			}
			return;
		}
		Optional<OguiAssetRegistry.StoredAsset> assetOpt = assetRegistry.find(sha256);
		if (assetOpt.isEmpty()) {
			if (ServerPlayNetworking.canSend(player, S2CAssetUnavailablePayload.TYPE)) {
				ServerPlayNetworking.send(player, new S2CAssetUnavailablePayload(sha256));
			}
			return;
		}
		OguiAssetRegistry.StoredAsset asset = assetOpt.get();
		byte[] bytes = asset.bytes();
		int chunkSize = OguiAssetRegistry.CHUNK_SIZE_BYTES;
		int totalChunks = Math.max(1, (bytes.length + chunkSize - 1) / chunkSize);
		for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
			int from = chunkIndex * chunkSize;
			int to = Math.min(bytes.length, from + chunkSize);
			byte[] slice = java.util.Arrays.copyOfRange(bytes, from, to);
			ServerPlayNetworking.send(player, new S2CAssetChunkPayload(
				sha256,
				asset.ref().kind(),
				bytes.length,
				chunkIndex,
				totalChunks,
				slice
			));
		}
	}

	public void handleChestInteraction(
		ServerPlayer player,
		OguiChestMenu menu,
		OguiChestLayout.SlotBinding binding,
		int buttonNum,
		ContainerInput input
	) {
		OguiSession session = activeSessions.get(player.getUUID());
		if (session == null || session.clientModMode() || !session.sessionId().equals(menu.sessionId())) {
			return;
		}
		if (binding.actionId() == null) {
			return;
		}
		OguiClickAction action = binding.forcedAction() != null
			? binding.forcedAction()
			: mapChestClickAction(buttonNum, input);
		dispatchInteraction(player, session, binding.actionId(), action, "", binding.forcedNumericValue());
	}

	public void onChestMenuClosed(UUID playerUuid, UUID closedSessionId) {
		OguiSession current = activeSessions.get(playerUuid);
		if (current != null && !current.clientModMode() && current.sessionId().equals(closedSessionId)) {
			activeSessions.remove(playerUuid, current);
		}
	}

	public void completeFakeSignInput(
		ServerPlayer player,
		UUID sessionId,
		int expectedRevision,
		String widgetId,
		String extractedText
	) {
		OguiSession session = activeSessions.get(player.getUUID());
		if (session == null || session.clientModMode()) {
			return;
		}
		if (!session.sessionId().equals(sessionId) || session.revision() != expectedRevision) {
			return;
		}
		OguiWidget widget = session.findWidget(widgetId);
		if (!(widget instanceof OguiWidget.TextInputWidget ti) || !ti.enabled()) {
			if (activeSessions.get(player.getUUID()) == session) {
				openChestForSession(player, session);
			}
			return;
		}
		handleValidatedTextInput(player, session, ti, extractedText);
		// Ensure player returns to the live ChestMenu if the handler did not close or open another screen.
		OguiSession after = activeSessions.get(player.getUUID());
		if (after != null && !after.clientModMode() && !(player.containerMenu instanceof OguiChestMenu)) {
			openChestForSession(player, after);
		}
	}

	public void onPlayerDisconnect(UUID playerUuid) {
		activeSessions.remove(playerUuid);
		handshakes.evict(playerUuid);
		assetRegistry.evictPlayer(playerUuid);
		FakeSignInputManager.instance().onPlayerDisconnect(playerUuid);
	}

	public void clearRuntimeState() {
		activeSessions.clear();
		handshakes.clear();
		assetRegistry.clearRuntime();
		FakeSignInputManager.instance().clearAll();
	}

	public void clearAllRegistrationsForTest() {
		clearRuntimeState();
		screens.clear();
		assetRegistry.clearAll();
		synchronized (shortcuts) {
			shortcuts.clear();
		}
	}

	private void dispatchInteraction(
		ServerPlayer player,
		OguiSession session,
		String widgetId,
		OguiClickAction action,
		String inputText,
		int numericValue
	) {
		if (handleSyntheticNav(player, session, widgetId, action)) {
			return;
		}
		OguiWidget widget = session.findWidget(widgetId);
		if (widget == null || !widget.enabled()) {
			return;
		}

		OguiClickContext ctx = new OguiClickContextImpl(player, session, action);
		switch (widget) {
			case OguiWidget.ButtonWidget b -> {
				if (!session.checkOrArmConfirmation(b.confirmSpec())) {
					playClickCue(player, session);
					sendStateSnapshotOnly(player, session);
					return;
				}
				playClickCue(player, session);
				if (b.onClick() != null) {
					b.onClick().accept(ctx);
				}
			}
			case OguiWidget.ToggleWidget t -> {
				if (!session.checkOrArmConfirmation(t.confirmSpec())) {
					playClickCue(player, session);
					sendStateSnapshotOnly(player, session);
					return;
				}
				playClickCue(player, session);
				if (t.onToggle() != null) {
					t.onToggle().accept(ctx, !t.value());
				}
			}
			case OguiWidget.CycleWidget c -> {
				session.clearConfirmation();
				playClickCue(player, session);
				int nextIndex;
				if (action.isShift()) {
					nextIndex = 0;
				} else if (action.isRight()) {
					nextIndex = Math.floorMod(c.selectedIndex() - 1, c.options().size());
				} else {
					nextIndex = Math.floorMod(c.selectedIndex() + 1, c.options().size());
				}
				if (c.onCycle() != null) {
					c.onCycle().accept(ctx, c.options().get(nextIndex));
				}
			}
			case OguiWidget.QuantitySelectorWidget q -> {
				session.clearConfirmation();
				playClickCue(player, session);
				int nextValue;
				if (action == OguiClickAction.CLIENT_INPUT) {
					nextValue = Math.clamp(numericValue, q.min(), q.max());
				} else {
					int step = (action.isShift() && q.stepPresets().size() > 1)
						? q.stepPresets().get(1)
						: q.stepPresets().getFirst();
					int delta = action.isRight() ? -step : step;
					nextValue = Math.clamp((long) q.value() + delta, q.min(), q.max());
				}
				if (q.onChange() != null) {
					q.onChange().accept(ctx, nextValue);
				}
			}
			case OguiWidget.TextInputWidget ti -> {
				session.clearConfirmation();
				if (!session.clientModMode() && action != OguiClickAction.CLIENT_INPUT) {
					playClickCue(player, session);
					if (action.isRight()) {
						handleValidatedTextInput(player, session, ti, "");
					} else {
						FakeSignInputManager.instance().startInput(player, session, ti);
					}
				} else {
					handleValidatedTextInput(player, session, ti, inputText);
				}
			}
			case OguiWidget.StructurePreviewWidget sp -> {
				session.clearConfirmation();
				playClickCue(player, session);
				if (sp.onClick() != null) {
					sp.onClick().accept(ctx);
				}
			}
		}
	}

	private boolean handleSyntheticNav(
		ServerPlayer player,
		OguiSession session,
		String widgetId,
		OguiClickAction action
	) {
		if (OguiSession.NAV_CLOSE.equals(widgetId)) {
			playClickCue(player, session);
			closePlayerScreen(player);
			return true;
		}
		if (OguiSession.NAV_BACK.equals(widgetId)) {
			playClickCue(player, session);
			session.clearConfirmation();
			OguiOpenRequest back = session.request().backRequest();
			if (back != null) {
				openScreen(player, back);
			} else {
				closePlayerScreen(player);
			}
			return true;
		}
		if (OguiSession.NAV_PREV.equals(widgetId)) {
			if (session.hasPreviousPage()) {
				session.clearConfirmation();
				playClickCue(player, session);
				refreshSession(player, session, session.request().withPage(session.page() - 1));
			}
			return true;
		}
		if (OguiSession.NAV_NEXT.equals(widgetId)) {
			if (session.hasNextPage()) {
				session.clearConfirmation();
				playClickCue(player, session);
				refreshSession(player, session, session.request().withPage(session.page() + 1));
			}
			return true;
		}
		if (OguiSession.NAV_REFRESH.equals(widgetId)) {
			session.clearConfirmation();
			playClickCue(player, session);
			refreshSession(player, session, session.request());
			return true;
		}
		if (widgetId.startsWith(OguiSession.NAV_TAB_PREFIX)) {
			String tabId = widgetId.substring(OguiSession.NAV_TAB_PREFIX.length());
			OguiTab tab = session.findTab(tabId);
			if (tab != null && tab.enabled()) {
				session.clearConfirmation();
				playClickCue(player, session);
				OguiClickContext ctx = new OguiClickContextImpl(player, session, action);
				if (tab.onSelect() != null) {
					tab.onSelect().accept(ctx);
				} else {
					ctx.refreshWithState("tab", tab.id());
				}
			}
			return true;
		}
		return false;
	}

	private void handleValidatedTextInput(
		ServerPlayer player,
		OguiSession session,
		OguiWidget.TextInputWidget ti,
		String rawInput
	) {
		String sanitized = sanitizeTextInput(rawInput, ti.maxLength());
		if (!sanitized.isEmpty()) {
			if (ti.mode() == OguiWidget.InputMode.INTEGER) {
				try {
					Integer.parseInt(sanitized);
				} catch (NumberFormatException ignored) {
					sendFeedback(player, false, OguiText.nonItalic("Invalid integer: " + sanitized, ChatFormatting.RED));
					return;
				}
			} else if (ti.mode() == OguiWidget.InputMode.DECIMAL) {
				try {
					double parsed = Double.parseDouble(sanitized);
					if (!Double.isFinite(parsed)) {
						sendFeedback(player, false, OguiText.nonItalic("Invalid number: " + sanitized, ChatFormatting.RED));
						return;
					}
				} catch (NumberFormatException ignored) {
					sendFeedback(player, false, OguiText.nonItalic("Invalid decimal number: " + sanitized, ChatFormatting.RED));
					return;
				}
			}
		}

		OguiClickContext ctx = new OguiClickContextImpl(player, session, OguiClickAction.CLIENT_INPUT);
		if (ti.onSubmit() != null) {
			ti.onSubmit().accept(ctx, sanitized);
		} else {
			ctx.refreshWithState(ti.stateKey(), sanitized);
		}
	}

	private void refreshSession(ServerPlayer player, OguiSession session, OguiOpenRequest targetRequest) {
		Optional<OguiScreenProvider> provider = findScreen(targetRequest.screenId());
		if (provider.isEmpty()) {
			closePlayerScreen(player);
			return;
		}
		OguiScreenModel nextModel = buildModel(player, provider.get(), targetRequest);
		session.updateView(targetRequest, nextModel, true);
		pushSessionView(player, session, true);
	}

	private void sendStateSnapshotOnly(ServerPlayer player, OguiSession session) {
		pushSessionView(player, session, false);
	}

	private void pushSessionView(ServerPlayer player, OguiSession session, boolean reopenChestIfMissing) {
		if (session.clientModMode()) {
			if (player.connection != null && !player.hasDisconnected()
				&& ServerPlayNetworking.canSend(player, S2CUpdateScreenPayload.TYPE)) {
				ScreenSnapshotDto dto = session.toDto();
				assetRegistry.allowAssetsFromSnapshot(player.getUUID(), dto);
				ServerPlayNetworking.send(player, new S2CUpdateScreenPayload(
					session.sessionId(),
					session.revision(),
					dto
				));
			}
			return;
		}
		if (player.containerMenu instanceof OguiChestMenu chestMenu && chestMenu.sessionId().equals(session.sessionId())) {
			chestMenu.renderSession(session, player.level().getServer());
		} else if (reopenChestIfMissing) {
			openChestForSession(player, session);
		}
	}

	private void openChestForSession(ServerPlayer player, OguiSession session) {
		OguiChestLayout.PlannedChestView initialPlan = OguiChestLayout.plan(session, player.level().getServer());
		player.openMenu(new SimpleMenuProvider(
			(containerId, inv, p) -> new OguiChestMenu(containerId, inv, this, session, initialPlan),
			session.model().title()
		));
	}

	private static OguiScreenModel buildModel(
		ServerPlayer viewer,
		OguiScreenProvider provider,
		OguiOpenRequest request
	) {
		OguiBuildContext context = new OguiBuildContext(
			viewer,
			request.contextKey(),
			request.page(),
			request.state(),
			request.backScreenId(),
			request.backContextKey()
		);
		return Objects.requireNonNull(
			provider.build(context),
			() -> "OguiScreenProvider returned null model for " + request.screenId()
		);
	}

	private static void playClickCue(ServerPlayer player, OguiSession session) {
		if (!session.clientModMode()) {
			OguiFeedbackSupport.playClickSound(player);
		}
	}

	private static OguiClickAction mapChestClickAction(int buttonNum, ContainerInput input) {
		if (input == ContainerInput.QUICK_MOVE) {
			return buttonNum == 1 ? OguiClickAction.SHIFT_RIGHT : OguiClickAction.SHIFT_LEFT;
		}
		return buttonNum == 1 ? OguiClickAction.RIGHT : OguiClickAction.LEFT;
	}

	private static String sanitizeTextInput(@Nullable String raw, int maxLength) {
		if (raw == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(raw);
		if (stripped == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(Math.min(stripped.length(), maxLength));
		for (int i = 0; i < stripped.length() && sb.length() < maxLength; i++) {
			char c = stripped.charAt(i);
			if (!Character.isISOControl(c)) {
				sb.append(c);
			}
		}
		return sb.toString().trim();
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(Ogui.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("1.0.0");
	}

	private static @Nullable MinecraftServer findServer(OguiSession session) {
		return currentServer;
	}

	private static volatile @Nullable MinecraftServer currentServer;

	public static void setCurrentServer(@Nullable MinecraftServer server) {
		currentServer = server;
	}

	private final class OguiClickContextImpl implements OguiClickContext {
		private final ServerPlayer actor;
		private final OguiSession session;
		private final OguiClickAction clickAction;

		private OguiClickContextImpl(ServerPlayer actor, OguiSession session, OguiClickAction clickAction) {
			this.actor = actor;
			this.session = session;
			this.clickAction = clickAction;
		}

		@Override
		public ServerPlayer actor() {
			return actor;
		}

		@Override
		public @Nullable String contextKey() {
			return session.request().contextKey();
		}

		@Override
		public OguiClickAction clickAction() {
			return clickAction;
		}

		@Override
		public int currentPage() {
			return session.page();
		}

		@Override
		public Map<String, String> state() {
			return session.request().state();
		}

		@Override
		public void refresh() {
			if (activeSessions.get(actor.getUUID()) == session) {
				refreshSession(actor, session, session.request());
			}
		}

		@Override
		public void refreshWithState(String key, String value) {
			refreshWithState(key, value, false);
		}

		@Override
		public void refreshWithState(String key, String value, boolean preservePage) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
			session.clearConfirmation();
			if (activeSessions.get(actor.getUUID()) == session) {
				OguiOpenRequest nextReq = session.request().withState(key, value);
				if (preservePage) {
					nextReq = nextReq.withPage(session.page());
				}
				refreshSession(actor, session, nextReq);
			}
		}

		@Override
		public void refreshWithStates(Map<String, String> updates) {
			Objects.requireNonNull(updates, "updates");
			session.clearConfirmation();
			if (activeSessions.get(actor.getUUID()) == session) {
				refreshSession(actor, session, session.request().withStates(updates));
			}
		}

		@Override
		public void refreshWithoutState(String key) {
			Objects.requireNonNull(key, "key");
			session.clearConfirmation();
			if (activeSessions.get(actor.getUUID()) == session) {
				refreshSession(actor, session, session.request().withRemovedState(key));
			}
		}

		@Override
		public void openScreen(Identifier screenId, @Nullable String targetContextKey) {
			Objects.requireNonNull(screenId, "screenId");
			openScreen(OguiOpenRequest.of(screenId, targetContextKey));
		}

		@Override
		public void openScreen(OguiOpenRequest nextRequest) {
			Objects.requireNonNull(nextRequest, "nextRequest");
			OguiOpenRequest resolved = nextRequest.backRequest() == null
				? nextRequest.withBackRequest(session.request())
				: nextRequest;
			OguiSessionManager.this.openScreen(actor, resolved);
		}

		@Override
		public void goBack() {
			OguiOpenRequest back = session.request().backRequest();
			if (back != null) {
				OguiSessionManager.this.openScreen(actor, back);
			} else {
				close();
			}
		}

		@Override
		public void close() {
			closePlayerScreen(actor);
		}

		@Override
		public void sendFeedback(boolean success, String message) {
			OguiSessionManager.this.sendFeedback(actor, success, message);
		}

		@Override
		public void sendFeedback(boolean success, Component message) {
			OguiSessionManager.this.sendFeedback(actor, success, message);
		}

		@Override
		public boolean isClientModActive() {
			return session.clientModMode();
		}
	}
}
