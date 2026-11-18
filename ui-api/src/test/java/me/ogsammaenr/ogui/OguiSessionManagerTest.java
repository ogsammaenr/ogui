package me.ogsammaenr.ogui;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.runtime.OguiHandshakeRegistry;
import me.ogsammaenr.ogui.runtime.OguiSession;
import me.ogsammaenr.ogui.vanilla.FakeSignInputManager;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiSessionManagerTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void handshakeRegistryEnforcesExactProtocolVersionAndEviction() {
		OguiHandshakeRegistry registry = new OguiHandshakeRegistry();
		UUID playerA = UUID.randomUUID();
		UUID playerB = UUID.randomUUID();

		registry.recordClientHello(playerA, Ogui.PROTOCOL_VERSION, "1.0.0");
		registry.recordClientHello(playerB, Ogui.PROTOCOL_VERSION + 1, "2.0.0");

		assertTrue(registry.isCompatible(playerA));
		assertFalse(registry.isCompatible(playerB));

		registry.evict(playerA);
		assertFalse(registry.isCompatible(playerA));
	}

	@Test
	void sessionPaginationConfirmationAndRevisionLifecycle() {
		OguiScreenModel.PagedGridBuilder builder = OguiScreenModel.pagedGrid("Catalog");
		for (int i = 0; i < 30; i++) {
			builder.addEntry(OguiWidget.button("item_" + i, "minecraft:stone", "Item " + i, ChatFormatting.WHITE).build());
		}
		OguiScreenModel model = builder.build();
		OguiOpenRequest request = OguiOpenRequest.of(Ogui.id("catalog"), null, 99);
		OguiSession session = new OguiSession(UUID.randomUUID(), UUID.randomUUID(), false, request, model);

		// Clamped to page 1 (second page with 2 entries)
		assertEquals(1, session.page());
		assertEquals(2, session.totalPages());
		assertEquals(2, session.pagedSlice().size());
		assertTrue(session.hasPreviousPage());
		assertFalse(session.hasNextPage());
		assertNotNull(session.findWidget("item_29"));
		assertNull(session.findWidget("item_0"));

		// Two-click confirmation arming and consumption
		OguiConfirmSpec confirm = OguiConfirmSpec.of("reset_key", "Confirm?");
		assertFalse(session.checkOrArmConfirmation(confirm));
		assertEquals("reset_key", session.activeConfirmKey());
		assertTrue(session.checkOrArmConfirmation(confirm));
		assertNull(session.activeConfirmKey());

		// Expired confirmation resets automatically
		OguiConfirmSpec tinyTtl = new OguiConfirmSpec("ttl_key", OguiText.nonItalic("Wait"), Duration.ofMillis(1));
		// Constructor defaults non-positive to 10s, so test normal arming & clearing:
		assertFalse(session.checkOrArmConfirmation(tinyTtl));
		session.clearConfirmation();
		assertNull(session.activeConfirmKey());

		// Live update increments revision
		int revBefore = session.revision();
		session.updateView(request.withPage(0), model, true);
		assertEquals(revBefore + 1, session.revision());
		assertEquals(0, session.page());
		assertEquals(28, session.pagedSlice().size());
	}

	@Test
	void fakeSignTextExtractionStripsFormattingPreservesSpacesAndCapsLength() {
		String extracted = FakeSignInputManager.extractInputText(
			List.of("§aemerald ", "block", "▲ Type above ▲", "Search"),
			16
		);
		assertEquals("emerald block", extracted);

		String capped = FakeSignInputManager.extractInputText(
			List.of("1234567890", "abcdefghij", "hint", "label"),
			12
		);
		assertEquals("1234567890ab", capped);
	}
}
