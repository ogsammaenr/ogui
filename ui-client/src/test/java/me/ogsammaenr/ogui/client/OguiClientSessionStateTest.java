package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiClientSessionStateTest {
	private static ScreenSnapshotDto dummySnap(String title) {
		return new ScreenSnapshotDto(
			Ogui.id("test"), null, OguiText.nonItalic(title),
			LayoutArchetype.FIXED_GRID, OguiTheme.DEFAULT, List.of(), null, 0, 1, 0, false,
			List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(),
			3, Map.of(), null, null
		);
	}

	@Test
	void tracksHandshakeOpenUpdateFeedbackAndDisconnect() {
		OguiClientSessionState state = new OguiClientSessionState();
		S2CServerHelloPayload.ShortcutInfo shortcut = new S2CServerHelloPayload.ShortcutInfo(
			Ogui.id("shop"), Component.literal("Shop"), Component.literal("Open shop")
		);

		// Mismatched server protocol rejects shortcuts
		state.onServerHello(new S2CServerHelloPayload(999, "2.0.0", List.of(shortcut)));
		assertFalse(state.handshakeAccepted());
		assertTrue(state.shortcuts().isEmpty());

		// Matching protocol accepts shortcuts
		state.onServerHello(new S2CServerHelloPayload(Ogui.PROTOCOL_VERSION, "1.0.0", List.of(shortcut)));
		assertTrue(state.handshakeAccepted());
		assertEquals(1, state.shortcuts().size());

		UUID s1 = UUID.randomUUID();
		state.onOpenScreen(new S2COpenScreenPayload(s1, 1, dummySnap("V1")));
		assertEquals(s1, state.activeSessionId());
		assertEquals(1, state.activeRevision());

		// Mismatched sessionId update is ignored
		assertFalse(state.onUpdateScreen(new S2CUpdateScreenPayload(UUID.randomUUID(), 2, dummySnap("Bad"))));
		assertTrue(state.onUpdateScreen(new S2CUpdateScreenPayload(s1, 2, dummySnap("V2"))));
		assertEquals(2, state.activeRevision());
		assertEquals("V2", state.activeSnapshot().title().getString());

		// Feedback expires after 4.5s
		assertTrue(state.onFeedback(new S2CFeedbackPayload(s1, true, Component.literal("OK"), S2CFeedbackPayload.FeedbackSound.SUCCESS), 1000L));
		assertNotNull(state.activeFeedback(2000L));
		assertNull(state.activeFeedback(6000L));

		// Close & disconnect reset
		assertTrue(state.onCloseScreen(new S2CCloseScreenPayload(s1)));
		assertNull(state.activeSessionId());

		state.resetOnDisconnect();
		assertFalse(state.handshakeAccepted());
		assertTrue(state.shortcuts().isEmpty());
	}
}
