package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tab entry displayed in {@link LayoutArchetype#TABBED_PANEL} screens.
 */
public record OguiTab(
	String id,
	OguiIcon icon,
	Component title,
	List<Component> lore,
	boolean enabled,
	@Nullable Consumer<OguiClickContext> onSelect
) {
	public OguiTab {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("OguiTab id must not be blank.");
		}
		Objects.requireNonNull(icon, "icon");
		Objects.requireNonNull(title, "title");
		lore = lore == null ? List.of() : List.copyOf(lore);
	}

	public static Builder builder(String id, OguiIcon icon, Component title) {
		return new Builder(id, icon, title);
	}

	public static final class Builder {
		private final String id;
		private final OguiIcon icon;
		private final Component title;
		private List<Component> lore = List.of();
		private boolean enabled = true;
		private @Nullable Consumer<OguiClickContext> onSelect;

		private Builder(String id, OguiIcon icon, Component title) {
			this.id = id;
			this.icon = icon;
			this.title = title;
		}

		public Builder lore(List<Component> lore) {
			this.lore = lore == null ? List.of() : List.copyOf(lore);
			return this;
		}

		public Builder lore(Component... lines) {
			this.lore = List.of(lines);
			return this;
		}

		public Builder enabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder onSelect(@Nullable Consumer<OguiClickContext> onSelect) {
			this.onSelect = onSelect;
			return this;
		}

		public OguiTab build() {
			return new OguiTab(id, icon, title, lore, enabled, onSelect);
		}
	}
}
