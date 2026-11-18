package me.ogsammaenr.ogui.api;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable navigation and state request identifying a registered {@link OguiScreenProvider} screen.
 */
public record OguiOpenRequest(
	Identifier screenId,
	@Nullable String contextKey,
	int page,
	Map<String, String> state,
	@Nullable OguiOpenRequest backRequest
) {
	public static final int MAX_BACK_DEPTH = 16;

	public OguiOpenRequest {
		Objects.requireNonNull(screenId, "screenId");
		page = Math.max(0, page);
		state = state == null ? Map.of() : Map.copyOf(state);
		if (depth(backRequest) > MAX_BACK_DEPTH) {
			backRequest = truncate(backRequest, MAX_BACK_DEPTH);
		}
	}

	public static OguiOpenRequest of(Identifier screenId) {
		return new OguiOpenRequest(screenId, null, 0, Map.of(), null);
	}

	public static OguiOpenRequest of(Identifier screenId, @Nullable String contextKey) {
		return new OguiOpenRequest(screenId, contextKey, 0, Map.of(), null);
	}

	public static OguiOpenRequest of(Identifier screenId, @Nullable String contextKey, int page) {
		return new OguiOpenRequest(screenId, contextKey, page, Map.of(), null);
	}

	public OguiOpenRequest withPage(int nextPage) {
		return new OguiOpenRequest(screenId, contextKey, nextPage, state, backRequest);
	}

	public OguiOpenRequest withState(String key, String value) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(value, "value");
		Map<String, String> copy = new LinkedHashMap<>(state);
		copy.put(key, value);
		return new OguiOpenRequest(screenId, contextKey, 0, copy, backRequest);
	}

	public OguiOpenRequest withStates(Map<String, String> updates) {
		Objects.requireNonNull(updates, "updates");
		Map<String, String> copy = new LinkedHashMap<>(state);
		updates.forEach((k, v) -> {
			if (k != null && v != null) {
				copy.put(k, v);
			}
		});
		return new OguiOpenRequest(screenId, contextKey, 0, copy, backRequest);
	}

	public OguiOpenRequest withRemovedState(String key) {
		Objects.requireNonNull(key, "key");
		if (!state.containsKey(key)) {
			return this;
		}
		Map<String, String> copy = new LinkedHashMap<>(state);
		copy.remove(key);
		return new OguiOpenRequest(screenId, contextKey, 0, copy, backRequest);
	}

	public OguiOpenRequest withBackRequest(@Nullable OguiOpenRequest parent) {
		return new OguiOpenRequest(screenId, contextKey, page, state, parent);
	}

	public @Nullable Identifier backScreenId() {
		return backRequest != null ? backRequest.screenId() : null;
	}

	public @Nullable String backContextKey() {
		return backRequest != null ? backRequest.contextKey() : null;
	}

	private static int depth(@Nullable OguiOpenRequest request) {
		int count = 0;
		OguiOpenRequest cursor = request;
		while (cursor != null && count <= MAX_BACK_DEPTH + 1) {
			count++;
			cursor = cursor.backRequest();
		}
		return count;
	}

	private static @Nullable OguiOpenRequest truncate(@Nullable OguiOpenRequest request, int remaining) {
		if (request == null || remaining <= 0) {
			return null;
		}
		return new OguiOpenRequest(
			request.screenId(),
			request.contextKey(),
			request.page(),
			request.state(),
			truncate(request.backRequest(), remaining - 1)
		);
	}
}
