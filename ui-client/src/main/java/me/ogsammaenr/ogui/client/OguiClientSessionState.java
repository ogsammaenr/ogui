package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Client-side state machine tracking server handshake status, shortcuts, and the currently open Ogui screen session.
 */
public final class OguiClientSessionState {
	public record FeedbackBanner(boolean success, Component message, long expiresAtMillis) {
		public boolean isExpired(long nowMillis) {
			return nowMillis > expiresAtMillis;
		}
	}

	private boolean helloSentThisConnection;
	private boolean handshakeAccepted;
	private int serverProtocolVersion;
	private List<S2CServerHelloPayload.ShortcutInfo> shortcuts = List.of();
	private @Nullable UUID activeSessionId;
	private int activeRevision;
	private @Nullable ScreenSnapshotDto activeSnapshot;
	private @Nullable FeedbackBanner feedbackBanner;

	public boolean helloSentThisConnection() {
		return helloSentThisConnection;
	}

	public void markHelloSent() {
		this.helloSentThisConnection = true;
	}

	public boolean handshakeAccepted() {
		return handshakeAccepted;
	}

	public int serverProtocolVersion() {
		return serverProtocolVersion;
	}

	public List<S2CServerHelloPayload.ShortcutInfo> shortcuts() {
		return shortcuts;
	}

	public @Nullable UUID activeSessionId() {
		return activeSessionId;
	}

	public int activeRevision() {
		return activeRevision;
	}

	public @Nullable ScreenSnapshotDto activeSnapshot() {
		return activeSnapshot;
	}

	public @Nullable FeedbackBanner activeFeedback(long nowMillis) {
		if (feedbackBanner != null && feedbackBanner.isExpired(nowMillis)) {
			feedbackBanner = null;
		}
		return feedbackBanner;
	}

	public void onServerHello(S2CServerHelloPayload payload) {
		this.serverProtocolVersion = payload.protocolVersion();
		this.handshakeAccepted = payload.protocolVersion() == Ogui.PROTOCOL_VERSION;
		this.shortcuts = this.handshakeAccepted ? payload.shortcuts() : List.of();
	}

	public void onOpenScreen(S2COpenScreenPayload payload) {
		this.activeSessionId = payload.sessionId();
		this.activeRevision = payload.revision();
		this.activeSnapshot = payload.snapshot();
		this.feedbackBanner = null;
	}

	public boolean onUpdateScreen(S2CUpdateScreenPayload payload) {
		if (activeSessionId == null || !activeSessionId.equals(payload.sessionId())) {
			return false;
		}
		this.activeRevision = payload.revision();
		this.activeSnapshot = payload.snapshot();
		return true;
	}

	public boolean onCloseScreen(S2CCloseScreenPayload payload) {
		if (activeSessionId == null || !activeSessionId.equals(payload.sessionId())) {
			return false;
		}
		clearActiveScreen();
		return true;
	}

	public boolean onFeedback(S2CFeedbackPayload payload, long nowMillis) {
		if (activeSessionId == null || !activeSessionId.equals(payload.sessionId())) {
			return false;
		}
		this.feedbackBanner = new FeedbackBanner(payload.success(), payload.message(), nowMillis + 4500L);
		return true;
	}

	public void clearActiveScreenIfMatching(UUID sessionId) {
		if (sessionId.equals(activeSessionId)) {
			clearActiveScreen();
		}
	}

	public void clearActiveScreen() {
		this.activeSessionId = null;
		this.activeRevision = 0;
		this.activeSnapshot = null;
		this.feedbackBanner = null;
	}

	public void resetOnDisconnect() {
		this.helloSentThisConnection = false;
		this.handshakeAccepted = false;
		this.serverProtocolVersion = 0;
		this.shortcuts = List.of();
		clearActiveScreen();
	}
}
