package me.ogsammaenr.ogui;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiOpenRequest;
import me.ogsammaenr.ogui.api.OguiWidget;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiOpenRequestTest {
	@Test
	void requestStateMutationsRemainImmutableAndTruncateDeepBackStack() {
		OguiOpenRequest root = OguiOpenRequest.of(Ogui.id("shop"), "blocks");
		OguiOpenRequest filtered = root.withPage(3).withState("search", "diamond");
		assertEquals(0, filtered.page());
		assertEquals("diamond", filtered.state().get("search"));
		assertTrue(root.state().isEmpty());

		OguiOpenRequest multi = filtered.withStates(Map.of("sort", "price", "onlyStock", "true"));
		assertEquals("price", multi.state().get("sort"));
		assertEquals("true", multi.state().get("onlyStock"));

		OguiOpenRequest removed = multi.withRemovedState("search");
		assertNull(removed.state().get("search"));
		assertEquals("price", removed.state().get("sort"));

		OguiOpenRequest chain = root;
		for (int i = 0; i < 25; i++) {
			chain = OguiOpenRequest.of(Ogui.id("step_" + i)).withBackRequest(chain);
		}
		int depth = 0;
		OguiOpenRequest cursor = chain;
		while (cursor != null) {
			depth++;
			cursor = cursor.backRequest();
		}
		assertTrue(depth <= OguiOpenRequest.MAX_BACK_DEPTH + 1);
	}

	@Test
	void reservedWidgetPrefixIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> OguiWidget.validateWidgetId("__ogui:back"));
		assertThrows(IllegalArgumentException.class, () -> OguiWidget.validateWidgetId("  "));
	}
}
