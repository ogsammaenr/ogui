package me.ogsammaenr.ogui.api;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual theme hints and header summary cards attached to an {@link OguiScreenModel}.
 */
public record OguiTheme(
	int accentColor,
	@Nullable Component subtitle,
	List<OguiSummaryCard> summaryCards,
	@Nullable String styleTag
) {
	public static final int DEFAULT_ACCENT_COLOR = 0xFF38BDF8;
	public static final OguiTheme DEFAULT = new OguiTheme(DEFAULT_ACCENT_COLOR, null, List.of(), null);

	public OguiTheme {
		summaryCards = summaryCards == null ? List.of() : List.copyOf(summaryCards);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int accentColor = DEFAULT_ACCENT_COLOR;
		private @Nullable Component subtitle;
		private final List<OguiSummaryCard> summaryCards = new ArrayList<>();
		private @Nullable String styleTag;

		private Builder() {
		}

		public Builder accentColor(int accentColor) {
			this.accentColor = accentColor;
			return this;
		}

		public Builder subtitle(@Nullable Component subtitle) {
			this.subtitle = subtitle;
			return this;
		}

		public Builder subtitle(String subtitle) {
			this.subtitle = OguiText.nonItalic(subtitle);
			return this;
		}

		public Builder addSummaryCard(OguiSummaryCard card) {
			if (card != null) {
				this.summaryCards.add(card);
			}
			return this;
		}

		public Builder styleTag(@Nullable String styleTag) {
			this.styleTag = styleTag;
			return this;
		}

		public OguiTheme build() {
			return new OguiTheme(accentColor, subtitle, summaryCards, styleTag);
		}
	}
}
