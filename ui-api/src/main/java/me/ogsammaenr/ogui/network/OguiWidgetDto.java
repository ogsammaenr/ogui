package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Client-safe, lambda-free wire representation of {@link OguiWidget}.
 */
public sealed interface OguiWidgetDto permits
	OguiWidgetDto.ButtonDto,
	OguiWidgetDto.ToggleDto,
	OguiWidgetDto.CycleDto,
	OguiWidgetDto.QuantityDto,
	OguiWidgetDto.TextInputDto,
	OguiWidgetDto.StructurePreviewDto {

	String widgetId();

	OguiIcon icon();

	Component label();

	List<Component> lore();

	boolean enabled();

	static OguiWidgetDto fromWidget(OguiWidget widget) {
		return switch (widget) {
			case OguiWidget.ButtonWidget b -> new ButtonDto(
				b.widgetId(), b.icon(), b.label(), b.lore(), b.badge(), b.enabled(), b.confirmSpec(), b.previewImage()
			);
			case OguiWidget.ToggleWidget t -> new ToggleDto(
				t.widgetId(), t.icon(), t.activeIcon(), t.label(), t.value(),
				t.onLabel(), t.offLabel(), t.lore(), t.enabled(), t.confirmSpec()
			);
			case OguiWidget.CycleWidget c -> new CycleDto(
				c.widgetId(), c.icon(), c.label(),
				c.options().stream().map(o -> new CycleOptionDto(o.id(), o.label(), o.icon())).toList(),
				c.selectedIndex(), c.lore(), c.enabled()
			);
			case OguiWidget.QuantitySelectorWidget q -> new QuantityDto(
				q.widgetId(), q.icon(), q.label(), q.value(), q.min(), q.max(),
				q.stepPresets(), q.formattedSummary(), q.lore(), q.enabled()
			);
			case OguiWidget.TextInputWidget ti -> new TextInputDto(
				ti.widgetId(), ti.stateKey(), ti.icon(), ti.label(), ti.placeholder(),
				ti.value(), ti.maxLength(), ti.mode(), ti.liveDebounce(), ti.lore(), ti.enabled()
			);
			case OguiWidget.StructurePreviewWidget sp -> new StructurePreviewDto(
				sp.widgetId(), sp.icon(), sp.label(), sp.lore(), sp.structureAsset(),
				sp.initialYaw(), sp.initialPitch(), sp.autoRotate(), sp.enabled()
			);
		};
	}

	record ButtonDto(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<Component> lore,
		@Nullable Component badge,
		boolean enabled,
		@Nullable OguiConfirmSpec confirmSpec,
		@Nullable OguiAssetRef previewImage
	) implements OguiWidgetDto {
		public ButtonDto(
			String widgetId,
			OguiIcon icon,
			Component label,
			List<Component> lore,
			@Nullable Component badge,
			boolean enabled,
			@Nullable OguiConfirmSpec confirmSpec
		) {
			this(widgetId, icon, label, lore, badge, enabled, confirmSpec, null);
		}

		public ButtonDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			lore = lore == null ? List.of() : List.copyOf(lore);
			if (previewImage != null && previewImage.kind() != OguiAssetKind.PNG) {
				throw new IllegalArgumentException("ButtonDto previewImage must have kind PNG");
			}
		}
	}

	record ToggleDto(
		String widgetId,
		OguiIcon icon,
		@Nullable OguiIcon activeIcon,
		Component label,
		boolean value,
		Component onLabel,
		Component offLabel,
		List<Component> lore,
		boolean enabled,
		@Nullable OguiConfirmSpec confirmSpec
	) implements OguiWidgetDto {
		public ToggleDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			Objects.requireNonNull(onLabel, "onLabel");
			Objects.requireNonNull(offLabel, "offLabel");
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public OguiIcon effectiveIcon() {
			return value && activeIcon != null ? activeIcon : icon;
		}
	}

	record CycleOptionDto(String id, Component label, @Nullable OguiIcon icon) {
		public CycleOptionDto {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(label, "label");
		}
	}

	record CycleDto(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<CycleOptionDto> options,
		int selectedIndex,
		List<Component> lore,
		boolean enabled
	) implements OguiWidgetDto {
		public CycleDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			options = options == null ? List.of() : List.copyOf(options);
			if (options.isEmpty()) {
				throw new IllegalArgumentException("CycleDto options must not be empty.");
			}
			selectedIndex = Math.floorMod(selectedIndex, options.size());
			lore = lore == null ? List.of() : List.copyOf(lore);
		}

		public CycleOptionDto selectedOption() {
			return options.get(selectedIndex);
		}
	}

	record QuantityDto(
		String widgetId,
		OguiIcon icon,
		Component label,
		int value,
		int min,
		int max,
		List<Integer> stepPresets,
		@Nullable Component formattedSummary,
		List<Component> lore,
		boolean enabled
	) implements OguiWidgetDto {
		public QuantityDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			max = Math.max(min, max);
			value = Math.clamp(value, min, max);
			stepPresets = stepPresets == null ? List.of(1, 16, 64) : List.copyOf(stepPresets);
			lore = lore == null ? List.of() : List.copyOf(lore);
		}
	}

	record TextInputDto(
		String widgetId,
		String stateKey,
		OguiIcon icon,
		Component label,
		Component placeholder,
		String value,
		int maxLength,
		OguiWidget.InputMode mode,
		boolean liveDebounce,
		List<Component> lore,
		boolean enabled
	) implements OguiWidgetDto {
		public TextInputDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(stateKey, "stateKey");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			placeholder = placeholder == null ? Component.empty() : placeholder;
			maxLength = Math.clamp(maxLength, 1, 512);
			value = value == null ? "" : (value.length() > maxLength ? value.substring(0, maxLength) : value);
			mode = mode == null ? OguiWidget.InputMode.TEXT : mode;
			lore = lore == null ? List.of() : List.copyOf(lore);
		}
	}

	record StructurePreviewDto(
		String widgetId,
		OguiIcon icon,
		Component label,
		List<Component> lore,
		@Nullable OguiAssetRef structureAsset,
		float initialYaw,
		float initialPitch,
		boolean autoRotate,
		boolean enabled
	) implements OguiWidgetDto {
		public StructurePreviewDto {
			Objects.requireNonNull(widgetId, "widgetId");
			Objects.requireNonNull(icon, "icon");
			Objects.requireNonNull(label, "label");
			lore = lore == null ? List.of() : List.copyOf(lore);
			if (structureAsset != null && structureAsset.kind() != OguiAssetKind.STRUCTURE_SHELL) {
				throw new IllegalArgumentException("StructurePreviewDto structureAsset must have kind STRUCTURE_SHELL");
			}
			initialYaw = Float.isFinite(initialYaw) ? initialYaw : 42.0F;
			initialPitch = Float.isFinite(initialPitch) ? Math.clamp(initialPitch, -85.0F, 85.0F) : 26.0F;
		}
	}
}
