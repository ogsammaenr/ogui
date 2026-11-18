package me.ogsammaenr.ogui.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Declarative, server-authoritative UI widget hierarchy rendered cleanly across both {@code ogui-client} and vanilla {@code ChestMenu}.
 */
public sealed interface OguiWidget permits
	OguiWidget.ButtonWidget,
	OguiWidget.ToggleWidget,
	OguiWidget.CycleWidget,
	OguiWidget.QuantitySelectorWidget,
	OguiWidget.TextInputWidget,
	OguiWidget.StructurePreviewWidget {

	String RESERVED_ID_PREFIX = "__ogui:";

	String widgetId();

	OguiIcon icon();

	Component label();

	List<Component> lore();

	boolean enabled();

	static String validateWidgetId(String widgetId) {
		if (widgetId == null || widgetId.isBlank()) {
			throw new IllegalArgumentException("Widget ID must not be blank.");
		}
		if (widgetId.startsWith(RESERVED_ID_PREFIX)) {
			throw new IllegalArgumentException("Widget ID must not start with reserved prefix '" + RESERVED_ID_PREFIX + "'.");
		}
		return widgetId;
	}

	static ButtonWidget.Builder button(String widgetId, OguiIcon icon, Component title) {
		return new ButtonWidget.Builder(widgetId, icon, title);
	}

	static ButtonWidget.Builder button(String widgetId, OguiIcon icon, String title, ChatFormatting color) {
		return new ButtonWidget.Builder(widgetId, icon, OguiText.nonItalic(title, color));
	}

	static ButtonWidget.Builder button(String widgetId, String itemId, String title, ChatFormatting color) {
		return button(widgetId, OguiIcon.item(itemId), title, color);
	}

	static ToggleWidget.Builder toggle(String widgetId, OguiIcon icon, Component label) {
		return new ToggleWidget.Builder(widgetId, icon, label);
	}

	static CycleWidget.Builder cycle(String widgetId, OguiIcon icon, Component label) {
		return new CycleWidget.Builder(widgetId, icon, label);
	}

	static QuantitySelectorWidget.Builder quantity(String widgetId, OguiIcon icon, Component label) {
		return new QuantitySelectorWidget.Builder(widgetId, icon, label);
	}

	static TextInputWidget.Builder textInput(String widgetId, OguiIcon icon, Component label) {
		return new TextInputWidget.Builder(widgetId, icon, label);
	}

	static StructurePreviewWidget.Builder structurePreview(String widgetId, OguiIcon icon, Component label) {
		return new StructurePreviewWidget.Builder(widgetId, icon, label);
	}

	record ButtonWidget(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<Component> lore,
		@Nullable Component badge,
		boolean enabled,
		@Nullable OguiConfirmSpec confirmSpec,
		@Nullable OguiAssetRef previewImage,
		@Nullable Consumer<OguiClickContext> onClick
	) implements OguiWidget {
		public ButtonWidget(
			String widgetId,
			OguiIcon icon,
			Component label,
			List<Component> lore,
			@Nullable Component badge,
			boolean enabled,
			@Nullable OguiConfirmSpec confirmSpec,
			@Nullable Consumer<OguiClickContext> onClick
		) {
			this(widgetId, icon, label, lore, badge, enabled, confirmSpec, null, onClick);
		}

		public ButtonWidget {
			validateWidgetId(widgetId);
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			lore = lore == null ? List.of() : List.copyOf(lore);
			if (previewImage != null && previewImage.kind() != OguiAssetKind.PNG) {
				throw new IllegalArgumentException("Button previewImage asset must have kind PNG, got " + previewImage.kind());
			}
		}

		public static final class Builder {
			private final String widgetId;
			private final OguiIcon icon;
			private final Component label;
			private final List<Component> lore = new ArrayList<>();
			private @Nullable Component badge;
			private boolean enabled = true;
			private @Nullable OguiConfirmSpec confirmSpec;
			private @Nullable OguiAssetRef previewImage;
			private @Nullable Consumer<OguiClickContext> onClick;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder loreLine(String text, ChatFormatting color) {
				this.lore.add(OguiText.nonItalic(text, color));
				return this;
			}

			public Builder badge(@Nullable Component badge) {
				this.badge = badge;
				return this;
			}

			public Builder badge(String text, ChatFormatting color) {
				this.badge = OguiText.nonItalic(text, color);
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder confirm(@Nullable OguiConfirmSpec confirmSpec) {
				this.confirmSpec = confirmSpec;
				return this;
			}

			public Builder confirm(String targetKey, String promptText) {
				this.confirmSpec = OguiConfirmSpec.of(targetKey, promptText);
				return this;
			}

			public Builder previewImage(@Nullable OguiAssetRef previewImage) {
				this.previewImage = previewImage;
				return this;
			}

			public Builder onClick(@Nullable Consumer<OguiClickContext> onClick) {
				this.onClick = onClick;
				return this;
			}

			public ButtonWidget build() {
				return new ButtonWidget(widgetId, icon, label, lore, badge, enabled, confirmSpec, previewImage, onClick);
			}
		}
	}

	record ToggleWidget(
		String widgetId,
		OguiIcon icon,
		@Nullable OguiIcon activeIcon,
		Component label,
		boolean value,
		Component onLabel,
		Component offLabel,
		List<Component> lore,
		boolean enabled,
		@Nullable OguiConfirmSpec confirmSpec,
		@Nullable BiConsumer<OguiClickContext, Boolean> onToggle
	) implements OguiWidget {
		public ToggleWidget {
			validateWidgetId(widgetId);
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			onLabel = onLabel == null ? OguiText.nonItalic("Enabled", ChatFormatting.GREEN) : onLabel;
			offLabel = offLabel == null ? OguiText.nonItalic("Disabled", ChatFormatting.RED) : offLabel;
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public OguiIcon effectiveIcon() {
			return value && activeIcon != null ? activeIcon : icon;
		}

		public static final class Builder {
			private final String widgetId;
			private final OguiIcon icon;
			private @Nullable OguiIcon activeIcon;
			private final Component label;
			private boolean value;
			private Component onLabel = OguiText.nonItalic("Enabled", ChatFormatting.GREEN);
			private Component offLabel = OguiText.nonItalic("Disabled", ChatFormatting.RED);
			private final List<Component> lore = new ArrayList<>();
			private boolean enabled = true;
			private @Nullable OguiConfirmSpec confirmSpec;
			private @Nullable BiConsumer<OguiClickContext, Boolean> onToggle;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder activeIcon(@Nullable OguiIcon activeIcon) {
				this.activeIcon = activeIcon;
				return this;
			}

			public Builder value(boolean value) {
				this.value = value;
				return this;
			}

			public Builder labels(Component onLabel, Component offLabel) {
				this.onLabel = Objects.requireNonNull(onLabel, "onLabel");
				this.offLabel = Objects.requireNonNull(offLabel, "offLabel");
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder confirm(@Nullable OguiConfirmSpec confirmSpec) {
				this.confirmSpec = confirmSpec;
				return this;
			}

			public Builder onToggle(@Nullable BiConsumer<OguiClickContext, Boolean> onToggle) {
				this.onToggle = onToggle;
				return this;
			}

			public ToggleWidget build() {
				return new ToggleWidget(widgetId, icon, activeIcon, label, value, onLabel, offLabel, lore, enabled, confirmSpec, onToggle);
			}
		}
	}

	record CycleOption(String id, Component label, @Nullable OguiIcon icon) {
		public CycleOption {
			if (id == null || id.isBlank()) {
				throw new IllegalArgumentException("CycleOption id must not be blank.");
			}
			Objects.requireNonNull(label, "label");
		}
	}

	record CycleWidget(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<CycleOption> options,
		int selectedIndex,
		List<Component> lore,
		boolean enabled,
		@Nullable BiConsumer<OguiClickContext, CycleOption> onCycle
	) implements OguiWidget {
		public CycleWidget {
			validateWidgetId(widgetId);
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			if (options == null || options.isEmpty()) {
				throw new IllegalArgumentException("CycleWidget options must not be empty.");
			}
			options = List.copyOf(options);
			selectedIndex = Math.floorMod(selectedIndex, options.size());
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public CycleOption selectedOption() {
			return options.get(selectedIndex);
		}

		public static final class Builder {
			private final String widgetId;
			private final OguiIcon icon;
			private final Component label;
			private final List<CycleOption> options = new ArrayList<>();
			private int selectedIndex;
			private final List<Component> lore = new ArrayList<>();
			private boolean enabled = true;
			private @Nullable BiConsumer<OguiClickContext, CycleOption> onCycle;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder addOption(String id, Component label) {
				this.options.add(new CycleOption(id, label, null));
				return this;
			}

			public Builder addOption(String id, Component label, @Nullable OguiIcon optionIcon) {
				this.options.add(new CycleOption(id, label, optionIcon));
				return this;
			}

			public Builder options(List<CycleOption> options) {
				Objects.requireNonNull(options, "options");
				this.options.addAll(options);
				return this;
			}

			public Builder selectedIndex(int selectedIndex) {
				this.selectedIndex = selectedIndex;
				return this;
			}

			public Builder selectedId(String optionId) {
				for (int i = 0; i < options.size(); i++) {
					if (options.get(i).id().equals(optionId)) {
						this.selectedIndex = i;
						break;
					}
				}
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder onCycle(@Nullable BiConsumer<OguiClickContext, CycleOption> onCycle) {
				this.onCycle = onCycle;
				return this;
			}

			public CycleWidget build() {
				return new CycleWidget(widgetId, icon, label, options, selectedIndex, lore, enabled, onCycle);
			}
		}
	}

	record QuantitySelectorWidget(
		String widgetId,
		OguiIcon icon,
		Component label,
		int value,
		int min,
		int max,
		List<Integer> stepPresets,
		@Nullable Function<Integer, Component> summaryFormatter,
		List<Component> lore,
		boolean enabled,
		@Nullable BiConsumer<OguiClickContext, Integer> onChange
	) implements OguiWidget {
		public static final List<Integer> DEFAULT_STEPS = List.of(1, 16, 64);

		public QuantitySelectorWidget {
			validateWidgetId(widgetId);
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			max = Math.max(min, max);
			value = Math.clamp(value, min, max);
			if (stepPresets == null || stepPresets.isEmpty()) {
				stepPresets = DEFAULT_STEPS;
			} else {
				List<Integer> cleaned = new ArrayList<>();
				for (Integer step : stepPresets) {
					if (step != null && step > 0) {
						cleaned.add(step);
					}
				}
				stepPresets = cleaned.isEmpty() ? DEFAULT_STEPS : List.copyOf(cleaned);
			}
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public @Nullable Component formattedSummary() {
			return summaryFormatter != null ? summaryFormatter.apply(value) : null;
		}

		public static final class Builder {
			private final String widgetId;
			private final OguiIcon icon;
			private final Component label;
			private int value = 1;
			private int min = 1;
			private int max = 6400;
			private List<Integer> stepPresets = DEFAULT_STEPS;
			private @Nullable Function<Integer, Component> summaryFormatter;
			private final List<Component> lore = new ArrayList<>();
			private boolean enabled = true;
			private @Nullable BiConsumer<OguiClickContext, Integer> onChange;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder range(int min, int max) {
				this.min = min;
				this.max = max;
				return this;
			}

			public Builder value(int value) {
				this.value = value;
				return this;
			}

			public Builder steps(List<Integer> stepPresets) {
				this.stepPresets = stepPresets == null ? DEFAULT_STEPS : List.copyOf(stepPresets);
				return this;
			}

			public Builder steps(int... steps) {
				List<Integer> list = new ArrayList<>(steps.length);
				for (int step : steps) {
					list.add(step);
				}
				this.stepPresets = list;
				return this;
			}

			public Builder summaryFormatter(@Nullable Function<Integer, Component> summaryFormatter) {
				this.summaryFormatter = summaryFormatter;
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder onChange(@Nullable BiConsumer<OguiClickContext, Integer> onChange) {
				this.onChange = onChange;
				return this;
			}

			public QuantitySelectorWidget build() {
				return new QuantitySelectorWidget(widgetId, icon, label, value, min, max, stepPresets, summaryFormatter, lore, enabled, onChange);
			}
		}
	}

	enum InputMode {
		TEXT,
		INTEGER,
		DECIMAL,
		SEARCH
	}

	record TextInputWidget(
		String widgetId,
		String stateKey,
		OguiIcon icon,
		Component label,
		Component placeholder,
		String value,
		int maxLength,
		InputMode mode,
		boolean liveDebounce,
		List<Component> lore,
		boolean enabled,
		@Nullable BiConsumer<OguiClickContext, String> onSubmit
	) implements OguiWidget {
		public static final int DEFAULT_MAX_LENGTH = 64;

		public TextInputWidget {
			validateWidgetId(widgetId);
			stateKey = (stateKey == null || stateKey.isBlank()) ? widgetId : stateKey;
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			placeholder = placeholder == null ? Component.empty() : placeholder;
			maxLength = Math.clamp(maxLength, 1, 512);
			value = value == null ? "" : (value.length() > maxLength ? value.substring(0, maxLength) : value);
			mode = mode == null ? InputMode.TEXT : mode;
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public static final class Builder {
			private final String widgetId;
			private String stateKey;
			private final OguiIcon icon;
			private final Component label;
			private Component placeholder = Component.empty();
			private String value = "";
			private int maxLength = DEFAULT_MAX_LENGTH;
			private InputMode mode = InputMode.TEXT;
			private boolean liveDebounce;
			private final List<Component> lore = new ArrayList<>();
			private boolean enabled = true;
			private @Nullable BiConsumer<OguiClickContext, String> onSubmit;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.stateKey = this.widgetId;
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder stateKey(String stateKey) {
				if (stateKey != null && !stateKey.isBlank()) {
					this.stateKey = stateKey;
				}
				return this;
			}

			public Builder placeholder(Component placeholder) {
				this.placeholder = placeholder == null ? Component.empty() : placeholder;
				return this;
			}

			public Builder placeholder(String placeholder) {
				this.placeholder = OguiText.nonItalic(placeholder, ChatFormatting.DARK_GRAY);
				return this;
			}

			public Builder value(@Nullable String value) {
				this.value = value == null ? "" : value;
				return this;
			}

			public Builder maxLength(int maxLength) {
				this.maxLength = maxLength;
				return this;
			}

			public Builder mode(InputMode mode) {
				this.mode = Objects.requireNonNull(mode, "mode");
				return this;
			}

			public Builder liveDebounce(boolean liveDebounce) {
				this.liveDebounce = liveDebounce;
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder onSubmit(@Nullable BiConsumer<OguiClickContext, String> onSubmit) {
				this.onSubmit = onSubmit;
				return this;
			}

			public TextInputWidget build() {
				return new TextInputWidget(widgetId, stateKey, icon, label, placeholder, value, maxLength, mode, liveDebounce, lore, enabled, onSubmit);
			}
		}
	}

	/**
	 * Interactive 3D structure viewport rendered natively in {@code ogui-client} and mapped onto an item summary card in vanilla ChestMenu.
	 */
	record StructurePreviewWidget(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<Component> lore,
		@Nullable OguiAssetRef structureAsset,
		float initialYaw,
		float initialPitch,
		boolean autoRotate,
		boolean enabled,
		@Nullable Consumer<OguiClickContext> onClick
	) implements OguiWidget {
		public StructurePreviewWidget {
			validateWidgetId(widgetId);
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			lore = lore == null ? List.of() : List.copyOf(lore);
			if (structureAsset != null && structureAsset.kind() != OguiAssetKind.STRUCTURE_SHELL) {
				throw new IllegalArgumentException(
					"StructurePreviewWidget structureAsset must have kind STRUCTURE_SHELL, got " + structureAsset.kind()
				);
			}
			initialYaw = Float.isFinite(initialYaw) ? initialYaw : 42.0F;
			initialPitch = Float.isFinite(initialPitch) ? Math.clamp(initialPitch, -85.0F, 85.0F) : 26.0F;
		}

		public static final class Builder {
			private final String widgetId;
			private final OguiIcon icon;
			private final Component label;
			private final List<Component> lore = new ArrayList<>();
			private @Nullable OguiAssetRef structureAsset;
			private float initialYaw = 42.0F;
			private float initialPitch = 26.0F;
			private boolean autoRotate;
			private boolean enabled = true;
			private @Nullable Consumer<OguiClickContext> onClick;

			private Builder(String widgetId, OguiIcon icon, Component label) {
				this.widgetId = validateWidgetId(widgetId);
				this.icon = Objects.requireNonNull(icon, "icon");
				this.label = Objects.requireNonNull(label, "label");
			}

			public Builder structureAsset(@Nullable OguiAssetRef structureAsset) {
				this.structureAsset = structureAsset;
				return this;
			}

			public Builder initialRotation(float yawDegrees, float pitchDegrees) {
				this.initialYaw = yawDegrees;
				this.initialPitch = pitchDegrees;
				return this;
			}

			public Builder autoRotate(boolean autoRotate) {
				this.autoRotate = autoRotate;
				return this;
			}

			public Builder lore(List<Component> lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder lore(Component... lines) {
				for (Component line : lines) {
					if (line != null) {
						this.lore.add(line);
					}
				}
				return this;
			}

			public Builder loreLine(String text, ChatFormatting color) {
				this.lore.add(OguiText.nonItalic(text, color));
				return this;
			}

			public Builder enabled(boolean enabled) {
				this.enabled = enabled;
				return this;
			}

			public Builder onClick(@Nullable Consumer<OguiClickContext> onClick) {
				this.onClick = onClick;
				return this;
			}

			public StructurePreviewWidget build() {
				return new StructurePreviewWidget(
					widgetId, icon, label, lore, structureAsset, initialYaw, initialPitch, autoRotate, enabled, onClick
				);
			}
		}
	}
}
