package me.ogsammaenr.ogui.client.test;

import me.ogsammaenr.ogui.api.OguiClickAction;
import me.ogsammaenr.ogui.api.OguiClickContext;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiTab;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.client.OguiClientMod;
import me.ogsammaenr.ogui.client.OguiClientPreferences;
import me.ogsammaenr.ogui.client.OguiClientSessionState;
import me.ogsammaenr.ogui.client.OguiQuickLauncherScreen;
import me.ogsammaenr.ogui.client.OguiScreen;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.runtime.OguiSession;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Isolated client-side preview session runner so {@code /oguitest open} and {@code /oguitest launcher}
 * render interactive {@link OguiScreen}s locally without sending network packets to remote servers.
 */
public final class OguiClientPreviewHarness {
	private final OguiClientPreferences preferences;

	public OguiClientPreviewHarness(OguiClientPreferences preferences) {
		this.preferences = preferences;
	}

	public OguiScreen createPreviewScreen(Minecraft mc, OguiDemoScreens.DemoArchetype initialArchetype) {
		UUID viewerUuid = mc.getUser().getProfileId();
		String viewerName = mc.getUser().getName();
		OguiOpenRequest initialReq = OguiOpenRequest.of(initialArchetype.screenId());
		OguiScreenModel initialModel = OguiDemoScreens.buildModel(initialArchetype, viewerUuid, viewerName, initialReq);

		OguiClientSessionState previewState = new OguiClientSessionState();
		OguiSession session = new OguiSession(UUID.randomUUID(), viewerUuid, true, initialReq, initialModel);
		previewState.onOpenScreen(new S2COpenScreenPayload(session.sessionId(), session.revision(), session.toDto()));

		OguiScreen[] screenRef = new OguiScreen[1];
		OguiScreen.InteractionSink localSink = new OguiScreen.InteractionSink() {
			@Override
			public void sendInteraction(
				UUID sessionId,
				int revision,
				String widgetId,
				OguiClickAction action,
				String inputText,
				int numericValue
			) {
				if (!session.sessionId().equals(sessionId) || session.revision() != revision) {
					return;
				}
				dispatchLocally(mc, previewState, session, screenRef[0], viewerUuid, viewerName, widgetId, action, inputText, numericValue);
			}

			@Override
			public void onScreenClosed(UUID sessionId) {
				previewState.clearActiveScreenIfMatching(sessionId);
			}
		};

		OguiScreen screen = new OguiScreen(
			previewState,
			preferences,
			session.sessionId(),
			session.revision(),
			session.toDto(),
			localSink
		);
		screenRef[0] = screen;
		return screen;
	}

	public OguiQuickLauncherScreen createPreviewLauncher(Minecraft mc) {
		List<S2CServerHelloPayload.ShortcutInfo> shortcuts = Arrays.stream(OguiDemoScreens.DemoArchetype.values())
			.map(arch -> new S2CServerHelloPayload.ShortcutInfo(
				arch.shortcutId(),
				OguiText.nonItalic(arch.displayName(), ChatFormatting.WHITE),
				OguiText.nonItalic("Client önizlemesi olarak açar", ChatFormatting.GRAY)
			))
			.toList();

		return new OguiQuickLauncherScreen(shortcuts, preferences, selectedId -> {
			for (OguiDemoScreens.DemoArchetype arch : OguiDemoScreens.DemoArchetype.values()) {
				if (arch.shortcutId().equals(selectedId)) {
					mc.gui.setScreen(createPreviewScreen(mc, arch));
					return;
				}
			}
		});
	}

	private void dispatchLocally(
		Minecraft mc,
		OguiClientSessionState previewState,
		OguiSession session,
		@Nullable OguiScreen screen,
		UUID viewerUuid,
		String viewerName,
		String widgetId,
		OguiClickAction action,
		String inputText,
		int numericValue
	) {
		if (handleLocalNav(mc, previewState, session, screen, viewerUuid, viewerName, widgetId, action)) {
			return;
		}
		OguiWidget widget = session.findWidget(widgetId);
		if (widget == null || !widget.enabled()) {
			return;
		}

		OguiClickContext ctx = new PreviewClickContext(mc, previewState, session, screen, viewerUuid, viewerName, action);
		switch (widget) {
			case OguiWidget.ButtonWidget b -> {
				if (!session.checkOrArmConfirmation(b.confirmSpec())) {
					OguiClientMod.playFeedbackSound(mc, preferences, S2CFeedbackPayload.FeedbackSound.CLICK);
					pushLocalRefresh(previewState, session, screen);
					return;
				}
				if (b.onClick() != null) {
					b.onClick().accept(ctx);
				}
			}
			case OguiWidget.ToggleWidget t -> {
				if (!session.checkOrArmConfirmation(t.confirmSpec())) {
					OguiClientMod.playFeedbackSound(mc, preferences, S2CFeedbackPayload.FeedbackSound.CLICK);
					pushLocalRefresh(previewState, session, screen);
					return;
				}
				if (t.onToggle() != null) {
					t.onToggle().accept(ctx, !t.value());
				}
			}
			case OguiWidget.CycleWidget c -> {
				session.clearConfirmation();
				int nextIndex = action.isShift()
					? 0
					: action.isRight()
					? Math.floorMod(c.selectedIndex() - 1, c.options().size())
					: Math.floorMod(c.selectedIndex() + 1, c.options().size());
				if (c.onCycle() != null) {
					c.onCycle().accept(ctx, c.options().get(nextIndex));
				}
			}
			case OguiWidget.QuantitySelectorWidget q -> {
				session.clearConfirmation();
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
				String cleaned = inputText == null ? "" : inputText;
				if (cleaned.length() > ti.maxLength()) {
					cleaned = cleaned.substring(0, ti.maxLength());
				}
				if (ti.onSubmit() != null) {
					ti.onSubmit().accept(ctx, cleaned);
				}
			}
			case OguiWidget.StructurePreviewWidget sp -> {
				session.clearConfirmation();
				if (sp.onClick() != null) {
					sp.onClick().accept(ctx);
				}
			}
		}
	}

	private boolean handleLocalNav(
		Minecraft mc,
		OguiClientSessionState previewState,
		OguiSession session,
		@Nullable OguiScreen screen,
		UUID viewerUuid,
		String viewerName,
		String widgetId,
		OguiClickAction action
	) {
		switch (widgetId) {
			case OguiSession.NAV_CLOSE -> {
				previewState.clearActiveScreen();
				if (screen != null) {
					screen.suppressNextCloseNotify();
				}
				mc.gui.setScreen(null);
				return true;
			}
			case OguiSession.NAV_BACK -> {
				OguiOpenRequest back = session.request().backRequest();
				if (back == null) {
					previewState.clearActiveScreen();
					if (screen != null) {
						screen.suppressNextCloseNotify();
					}
					mc.gui.setScreen(null);
				} else {
					rebuildSession(previewState, session, screen, viewerUuid, viewerName, back);
				}
				return true;
			}
			case OguiSession.NAV_PREV -> {
				if (session.hasPreviousPage()) {
					rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request().withPage(session.page() - 1));
				}
				return true;
			}
			case OguiSession.NAV_NEXT -> {
				if (session.hasNextPage()) {
					rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request().withPage(session.page() + 1));
				}
				return true;
			}
			case OguiSession.NAV_REFRESH -> {
				rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request());
				return true;
			}
			default -> {
				if (widgetId.startsWith(OguiSession.NAV_TAB_PREFIX)) {
					String tabId = widgetId.substring(OguiSession.NAV_TAB_PREFIX.length());
					OguiTab tab = session.findTab(tabId);
					if (tab != null && tab.enabled() && tab.onSelect() != null) {
						session.clearConfirmation();
						tab.onSelect().accept(new PreviewClickContext(mc, previewState, session, screen, viewerUuid, viewerName, action));
					}
					return true;
				}
				return false;
			}
		}
	}

	private static void rebuildSession(
		OguiClientSessionState previewState,
		OguiSession session,
		@Nullable OguiScreen screen,
		UUID viewerUuid,
		String viewerName,
		OguiOpenRequest nextRequest
	) {
		OguiDemoScreens.DemoArchetype archetype = resolveArchetype(nextRequest.screenId());
		OguiScreenModel nextModel = OguiDemoScreens.buildModel(archetype, viewerUuid, viewerName, nextRequest);
		session.clearConfirmation();
		session.updateView(nextRequest, nextModel, true);
		pushLocalRefresh(previewState, session, screen);
	}

	private static void pushLocalRefresh(
		OguiClientSessionState previewState,
		OguiSession session,
		@Nullable OguiScreen screen
	) {
		var dto = session.toDto();
		previewState.onUpdateScreen(new me.ogsammaenr.ogui.network.S2CUpdateScreenPayload(
			session.sessionId(), session.revision(), dto
		));
		if (screen != null) {
			screen.applyLiveUpdate(session.sessionId(), session.revision(), dto);
		}
	}

	private static OguiDemoScreens.DemoArchetype resolveArchetype(Identifier screenId) {
		for (OguiDemoScreens.DemoArchetype archetype : OguiDemoScreens.DemoArchetype.values()) {
			if (archetype.screenId().equals(screenId)) {
				return archetype;
			}
		}
		return OguiDemoScreens.DemoArchetype.TABBED;
	}

	private final class PreviewClickContext implements OguiClickContext {
		private final Minecraft mc;
		private final OguiClientSessionState previewState;
		private final OguiSession session;
		private final @Nullable OguiScreen screen;
		private final UUID viewerUuid;
		private final String viewerName;
		private final OguiClickAction action;

		private PreviewClickContext(
			Minecraft mc,
			OguiClientSessionState previewState,
			OguiSession session,
			@Nullable OguiScreen screen,
			UUID viewerUuid,
			String viewerName,
			OguiClickAction action
		) {
			this.mc = mc;
			this.previewState = previewState;
			this.session = session;
			this.screen = screen;
			this.viewerUuid = viewerUuid;
			this.viewerName = viewerName;
			this.action = action;
		}

		@Override
		public ServerPlayer actor() {
			return null;
		}

		@Override
		public @Nullable String contextKey() {
			return session.request().contextKey();
		}

		@Override
		public OguiClickAction clickAction() {
			return action;
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
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request());
		}

		@Override
		public void refreshWithState(String key, String value) {
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request().withState(key, value));
		}

		@Override
		public void refreshWithState(String key, String value, boolean preservePage) {
			OguiOpenRequest next = session.request().withState(key, value);
			if (preservePage) {
				next = next.withPage(session.page());
			}
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, next);
		}

		@Override
		public void refreshWithStates(Map<String, String> updates) {
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request().withStates(updates));
		}

		@Override
		public void refreshWithoutState(String key) {
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, session.request().withRemovedState(key));
		}

		@Override
		public void openScreen(Identifier screenId, @Nullable String targetContextKey) {
			openScreen(OguiOpenRequest.of(screenId, targetContextKey));
		}

		@Override
		public void openScreen(OguiOpenRequest nextRequest) {
			OguiOpenRequest withBack = nextRequest.backRequest() != null
				? nextRequest
				: nextRequest.withBackRequest(session.request());
			rebuildSession(previewState, session, screen, viewerUuid, viewerName, withBack);
		}

		@Override
		public void goBack() {
			OguiOpenRequest back = session.request().backRequest();
			if (back == null) {
				close();
			} else {
				rebuildSession(previewState, session, screen, viewerUuid, viewerName, back);
			}
		}

		@Override
		public void close() {
			previewState.clearActiveScreen();
			if (screen != null) {
				screen.suppressNextCloseNotify();
			}
			mc.gui.setScreen(null);
		}

		@Override
		public void sendFeedback(boolean success, String message) {
			sendFeedback(success, OguiText.nonItalic(message, success ? ChatFormatting.GREEN : ChatFormatting.RED));
		}

		@Override
		public void sendFeedback(boolean success, Component message) {
			S2CFeedbackPayload.FeedbackSound sound = success
				? S2CFeedbackPayload.FeedbackSound.SUCCESS
				: S2CFeedbackPayload.FeedbackSound.ERROR;
			previewState.onFeedback(new S2CFeedbackPayload(session.sessionId(), success, message, sound), System.currentTimeMillis());
			OguiClientMod.playFeedbackSound(mc, preferences, sound);
		}

		@Override
		public boolean isClientModActive() {
			return true;
		}
	}
}
