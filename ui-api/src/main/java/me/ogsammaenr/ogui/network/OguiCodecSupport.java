package me.ogsammaenr.ogui.network;

import io.netty.handler.codec.DecoderException;
import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiConfirmSpec;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiSummaryCard;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.api.OguiWidget;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounds-checked {@link StreamCodec} definitions for Ogui DTOs and network payloads.
 */
public final class OguiCodecSupport {
	public static final int MAX_LIST_SIZE = 512;
	public static final int MAX_MAP_SIZE = 64;
	public static final int MAX_SHORT_TEXT = 256;
	public static final int MAX_INPUT_TEXT = 512;

	private OguiCodecSupport() {
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiIcon> ICON_CODEC = new StreamCodec<>() {
		@Override
		public OguiIcon decode(RegistryFriendlyByteBuf buf) {
			String itemId = buf.readUtf(MAX_SHORT_TEXT);
			UUID headUuid = FriendlyByteBuf.readNullable(buf, UUIDUtil.STREAM_CODEC);
			ResolvableProfile profile = FriendlyByteBuf.readNullable(buf, ResolvableProfile.STREAM_CODEC);
			ItemStack stackOverride = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			int count = buf.readVarInt();
			boolean glowing = buf.readBoolean();
			return new OguiIcon(itemId, headUuid, profile, stackOverride.isEmpty() ? null : stackOverride, count, glowing);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiIcon icon) {
			buf.writeUtf(icon.itemId(), MAX_SHORT_TEXT);
			FriendlyByteBuf.writeNullable(buf, icon.playerHeadUuid(), UUIDUtil.STREAM_CODEC);
			FriendlyByteBuf.writeNullable(buf, icon.profile(), ResolvableProfile.STREAM_CODEC);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon.itemStackOverride() == null ? ItemStack.EMPTY : icon.itemStackOverride());
			buf.writeVarInt(icon.count());
			buf.writeBoolean(icon.glowing());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiConfirmSpec> CONFIRM_CODEC = new StreamCodec<>() {
		@Override
		public OguiConfirmSpec decode(RegistryFriendlyByteBuf buf) {
			String key = buf.readUtf(MAX_SHORT_TEXT);
			Component prompt = ComponentSerialization.STREAM_CODEC.decode(buf);
			long ttlMillis = Math.max(1000L, buf.readVarLong());
			return new OguiConfirmSpec(key, prompt, Duration.ofMillis(ttlMillis));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiConfirmSpec spec) {
			buf.writeUtf(spec.targetKey(), MAX_SHORT_TEXT);
			ComponentSerialization.STREAM_CODEC.encode(buf, spec.promptLine());
			buf.writeVarLong(spec.ttl().toMillis());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiSummaryCard> SUMMARY_CARD_CODEC = new StreamCodec<>() {
		@Override
		public OguiSummaryCard decode(RegistryFriendlyByteBuf buf) {
			OguiIcon icon = ICON_CODEC.decode(buf);
			Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
			List<Component> lines = readList(buf, ComponentSerialization.STREAM_CODEC, 32);
			int accent = buf.readInt();
			return new OguiSummaryCard(icon, title, lines, accent);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiSummaryCard card) {
			ICON_CODEC.encode(buf, card.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, card.title());
			writeList(buf, card.lines(), ComponentSerialization.STREAM_CODEC);
			buf.writeInt(card.accentColor());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiTheme> THEME_CODEC = new StreamCodec<>() {
		@Override
		public OguiTheme decode(RegistryFriendlyByteBuf buf) {
			int accent = buf.readInt();
			Component subtitle = FriendlyByteBuf.readNullable(buf, ComponentSerialization.STREAM_CODEC);
			List<OguiSummaryCard> cards = readList(buf, SUMMARY_CARD_CODEC, 16);
			String styleTag = FriendlyByteBuf.readNullable(buf, b -> b.readUtf(MAX_SHORT_TEXT));
			return new OguiTheme(accent, subtitle, cards, styleTag);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiTheme theme) {
			buf.writeInt(theme.accentColor());
			FriendlyByteBuf.writeNullable(buf, theme.subtitle(), ComponentSerialization.STREAM_CODEC);
			writeList(buf, theme.summaryCards(), SUMMARY_CARD_CODEC);
			FriendlyByteBuf.writeNullable(buf, theme.styleTag(), (b, s) -> b.writeUtf(s, MAX_SHORT_TEXT));
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiTabDto> TAB_CODEC = new StreamCodec<>() {
		@Override
		public OguiTabDto decode(RegistryFriendlyByteBuf buf) {
			String id = buf.readUtf(MAX_SHORT_TEXT);
			OguiIcon icon = ICON_CODEC.decode(buf);
			Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
			List<Component> lore = readList(buf, ComponentSerialization.STREAM_CODEC, 32);
			boolean enabled = buf.readBoolean();
			return new OguiTabDto(id, icon, title, lore, enabled);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiTabDto tab) {
			buf.writeUtf(tab.id(), MAX_SHORT_TEXT);
			ICON_CODEC.encode(buf, tab.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, tab.title());
			writeList(buf, tab.lore(), ComponentSerialization.STREAM_CODEC);
			buf.writeBoolean(tab.enabled());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiWidgetDto.CycleOptionDto> CYCLE_OPTION_CODEC = new StreamCodec<>() {
		@Override
		public OguiWidgetDto.CycleOptionDto decode(RegistryFriendlyByteBuf buf) {
			String id = buf.readUtf(MAX_SHORT_TEXT);
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			OguiIcon icon = FriendlyByteBuf.readNullable(buf, ICON_CODEC);
			return new OguiWidgetDto.CycleOptionDto(id, label, icon);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiWidgetDto.CycleOptionDto option) {
			buf.writeUtf(option.id(), MAX_SHORT_TEXT);
			ComponentSerialization.STREAM_CODEC.encode(buf, option.label());
			FriendlyByteBuf.writeNullable(buf, option.icon(), ICON_CODEC);
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiAssetRef> ASSET_REF_CODEC = new StreamCodec<>() {
		@Override
		public OguiAssetRef decode(RegistryFriendlyByteBuf buf) {
			String sha256 = buf.readUtf(OguiAssetRef.SHA256_HEX_LENGTH);
			OguiAssetKind kind = readEnum(buf, OguiAssetKind.class);
			int byteSize = buf.readVarInt();
			try {
				return new OguiAssetRef(sha256, kind, byteSize);
			} catch (IllegalArgumentException e) {
				throw new DecoderException("Invalid OguiAssetRef: " + e.getMessage());
			}
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiAssetRef ref) {
			buf.writeUtf(ref.sha256(), OguiAssetRef.SHA256_HEX_LENGTH);
			buf.writeEnum(ref.kind());
			buf.writeVarInt(ref.byteSize());
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, OguiWidgetDto> WIDGET_CODEC = new StreamCodec<>() {
		@Override
		public OguiWidgetDto decode(RegistryFriendlyByteBuf buf) {
			int kind = buf.readVarInt();
			String widgetId = buf.readUtf(MAX_SHORT_TEXT);
			OguiIcon icon = ICON_CODEC.decode(buf);
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			List<Component> lore = readList(buf, ComponentSerialization.STREAM_CODEC, 64);
			boolean enabled = buf.readBoolean();
			return switch (kind) {
				case 0 -> {
					Component badge = FriendlyByteBuf.readNullable(buf, ComponentSerialization.STREAM_CODEC);
					OguiConfirmSpec confirm = FriendlyByteBuf.readNullable(buf, CONFIRM_CODEC);
					OguiAssetRef previewImage = FriendlyByteBuf.readNullable(buf, ASSET_REF_CODEC);
					yield new OguiWidgetDto.ButtonDto(widgetId, icon, label, lore, badge, enabled, confirm, previewImage);
				}
				case 1 -> {
					OguiIcon activeIcon = FriendlyByteBuf.readNullable(buf, ICON_CODEC);
					boolean value = buf.readBoolean();
					Component onLabel = ComponentSerialization.STREAM_CODEC.decode(buf);
					Component offLabel = ComponentSerialization.STREAM_CODEC.decode(buf);
					OguiConfirmSpec confirm = FriendlyByteBuf.readNullable(buf, CONFIRM_CODEC);
					yield new OguiWidgetDto.ToggleDto(widgetId, icon, activeIcon, label, value, onLabel, offLabel, lore, enabled, confirm);
				}
				case 2 -> {
					List<OguiWidgetDto.CycleOptionDto> options = readList(buf, CYCLE_OPTION_CODEC, 64);
					int selectedIndex = buf.readVarInt();
					yield new OguiWidgetDto.CycleDto(widgetId, icon, label, options, selectedIndex, lore, enabled);
				}
				case 3 -> {
					int value = buf.readVarInt();
					int min = buf.readVarInt();
					int max = buf.readVarInt();
					int stepCount = Math.min(buf.readVarInt(), 16);
					List<Integer> steps = new ArrayList<>(stepCount);
					for (int i = 0; i < stepCount; i++) {
						steps.add(buf.readVarInt());
					}
					Component formattedSummary = FriendlyByteBuf.readNullable(buf, ComponentSerialization.STREAM_CODEC);
					yield new OguiWidgetDto.QuantityDto(widgetId, icon, label, value, min, max, steps, formattedSummary, lore, enabled);
				}
				case 4 -> {
					String stateKey = buf.readUtf(MAX_SHORT_TEXT);
					Component placeholder = ComponentSerialization.STREAM_CODEC.decode(buf);
					String value = buf.readUtf(MAX_INPUT_TEXT);
					int maxLength = buf.readVarInt();
					OguiWidget.InputMode mode = readEnum(buf, OguiWidget.InputMode.class);
					boolean liveDebounce = buf.readBoolean();
					yield new OguiWidgetDto.TextInputDto(widgetId, stateKey, icon, label, placeholder, value, maxLength, mode, liveDebounce, lore, enabled);
				}
				case 5 -> {
					OguiAssetRef structureAsset = FriendlyByteBuf.readNullable(buf, ASSET_REF_CODEC);
					float initialYaw = buf.readFloat();
					float initialPitch = buf.readFloat();
					boolean autoRotate = buf.readBoolean();
					yield new OguiWidgetDto.StructurePreviewDto(widgetId, icon, label, lore, structureAsset, initialYaw, initialPitch, autoRotate, enabled);
				}
				default -> throw new DecoderException("Unknown OguiWidgetDto kind: " + kind);
			};
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, OguiWidgetDto widget) {
			int kind = switch (widget) {
				case OguiWidgetDto.ButtonDto ignored -> 0;
				case OguiWidgetDto.ToggleDto ignored -> 1;
				case OguiWidgetDto.CycleDto ignored -> 2;
				case OguiWidgetDto.QuantityDto ignored -> 3;
				case OguiWidgetDto.TextInputDto ignored -> 4;
				case OguiWidgetDto.StructurePreviewDto ignored -> 5;
			};
			buf.writeVarInt(kind);
			buf.writeUtf(widget.widgetId(), MAX_SHORT_TEXT);
			ICON_CODEC.encode(buf, widget.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, widget.label());
			writeList(buf, widget.lore(), ComponentSerialization.STREAM_CODEC);
			buf.writeBoolean(widget.enabled());
			switch (widget) {
				case OguiWidgetDto.ButtonDto b -> {
					FriendlyByteBuf.writeNullable(buf, b.badge(), ComponentSerialization.STREAM_CODEC);
					FriendlyByteBuf.writeNullable(buf, b.confirmSpec(), CONFIRM_CODEC);
					FriendlyByteBuf.writeNullable(buf, b.previewImage(), ASSET_REF_CODEC);
				}
				case OguiWidgetDto.ToggleDto t -> {
					FriendlyByteBuf.writeNullable(buf, t.activeIcon(), ICON_CODEC);
					buf.writeBoolean(t.value());
					ComponentSerialization.STREAM_CODEC.encode(buf, t.onLabel());
					ComponentSerialization.STREAM_CODEC.encode(buf, t.offLabel());
					FriendlyByteBuf.writeNullable(buf, t.confirmSpec(), CONFIRM_CODEC);
				}
				case OguiWidgetDto.CycleDto c -> {
					writeList(buf, c.options(), CYCLE_OPTION_CODEC);
					buf.writeVarInt(c.selectedIndex());
				}
				case OguiWidgetDto.QuantityDto q -> {
					buf.writeVarInt(q.value());
					buf.writeVarInt(q.min());
					buf.writeVarInt(q.max());
					buf.writeVarInt(q.stepPresets().size());
					for (int step : q.stepPresets()) {
						buf.writeVarInt(step);
					}
					FriendlyByteBuf.writeNullable(buf, q.formattedSummary(), ComponentSerialization.STREAM_CODEC);
				}
				case OguiWidgetDto.TextInputDto ti -> {
					buf.writeUtf(ti.stateKey(), MAX_SHORT_TEXT);
					ComponentSerialization.STREAM_CODEC.encode(buf, ti.placeholder());
					buf.writeUtf(ti.value(), MAX_INPUT_TEXT);
					buf.writeVarInt(ti.maxLength());
					buf.writeEnum(ti.mode());
					buf.writeBoolean(ti.liveDebounce());
				}
				case OguiWidgetDto.StructurePreviewDto sp -> {
					FriendlyByteBuf.writeNullable(buf, sp.structureAsset(), ASSET_REF_CODEC);
					buf.writeFloat(sp.initialYaw());
					buf.writeFloat(sp.initialPitch());
					buf.writeBoolean(sp.autoRotate());
				}
			}
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, ScreenSnapshotDto> SNAPSHOT_CODEC = new StreamCodec<>() {
		@Override
		public ScreenSnapshotDto decode(RegistryFriendlyByteBuf buf) {
			Identifier screenId = Identifier.STREAM_CODEC.decode(buf);
			String contextKey = FriendlyByteBuf.readNullable(buf, b -> b.readUtf(MAX_SHORT_TEXT));
			Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
			LayoutArchetype archetype = readEnum(buf, LayoutArchetype.class);
			OguiTheme theme = THEME_CODEC.decode(buf);
			List<OguiTabDto> tabs = readList(buf, TAB_CODEC, 32);
			String activeTabId = FriendlyByteBuf.readNullable(buf, b -> b.readUtf(MAX_SHORT_TEXT));
			int page = buf.readVarInt();
			int totalPages = buf.readVarInt();
			int totalEntries = buf.readVarInt();
			boolean hasBack = buf.readBoolean();
			List<OguiWidgetDto> headerWidgets = readList(buf, WIDGET_CODEC, 64);
			List<OguiWidgetDto> entries = readList(buf, WIDGET_CODEC, MAX_LIST_SIZE);
			List<OguiWidgetDto> footerWidgets = readList(buf, WIDGET_CODEC, 64);
			OguiWidgetDto heroCard = FriendlyByteBuf.readNullable(buf, WIDGET_CODEC);
			List<OguiWidgetDto> infoBadges = readList(buf, WIDGET_CODEC, 32);
			List<OguiWidgetDto> formControls = readList(buf, WIDGET_CODEC, 64);
			List<OguiWidgetDto> actionButtons = readList(buf, WIDGET_CODEC, 32);
			int fixedRows = buf.readVarInt();
			int slotCount = Math.min(buf.readVarInt(), MAX_MAP_SIZE);
			Map<Integer, OguiWidgetDto> slotted = new LinkedHashMap<>(slotCount);
			for (int i = 0; i < slotCount; i++) {
				int slot = buf.readVarInt();
				OguiWidgetDto widget = WIDGET_CODEC.decode(buf);
				slotted.put(slot, widget);
			}
			OguiWidgetDto emptyState = FriendlyByteBuf.readNullable(buf, WIDGET_CODEC);
			String pendingConfirmKey = FriendlyByteBuf.readNullable(buf, b -> b.readUtf(MAX_SHORT_TEXT));
			return new ScreenSnapshotDto(
				screenId, contextKey, title, archetype, theme,
				tabs, activeTabId, page, totalPages, totalEntries, hasBack,
				headerWidgets, entries, footerWidgets,
				heroCard, infoBadges, formControls, actionButtons,
				fixedRows, slotted, emptyState, pendingConfirmKey
			);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, ScreenSnapshotDto snap) {
			Identifier.STREAM_CODEC.encode(buf, snap.screenId());
			FriendlyByteBuf.writeNullable(buf, snap.contextKey(), (b, s) -> b.writeUtf(s, MAX_SHORT_TEXT));
			ComponentSerialization.STREAM_CODEC.encode(buf, snap.title());
			buf.writeEnum(snap.archetype());
			THEME_CODEC.encode(buf, snap.theme());
			writeList(buf, snap.tabs(), TAB_CODEC);
			FriendlyByteBuf.writeNullable(buf, snap.activeTabId(), (b, s) -> b.writeUtf(s, MAX_SHORT_TEXT));
			buf.writeVarInt(snap.page());
			buf.writeVarInt(snap.totalPages());
			buf.writeVarInt(snap.totalEntries());
			buf.writeBoolean(snap.hasBack());
			writeList(buf, snap.headerWidgets(), WIDGET_CODEC);
			writeList(buf, snap.entries(), WIDGET_CODEC);
			writeList(buf, snap.footerWidgets(), WIDGET_CODEC);
			FriendlyByteBuf.writeNullable(buf, snap.heroCard(), WIDGET_CODEC);
			writeList(buf, snap.infoBadges(), WIDGET_CODEC);
			writeList(buf, snap.formControls(), WIDGET_CODEC);
			writeList(buf, snap.actionButtons(), WIDGET_CODEC);
			buf.writeVarInt(snap.fixedRows());
			buf.writeVarInt(snap.slottedWidgets().size());
			for (Map.Entry<Integer, OguiWidgetDto> entry : snap.slottedWidgets().entrySet()) {
				buf.writeVarInt(entry.getKey());
				WIDGET_CODEC.encode(buf, entry.getValue());
			}
			FriendlyByteBuf.writeNullable(buf, snap.emptyState(), WIDGET_CODEC);
			FriendlyByteBuf.writeNullable(buf, snap.pendingConfirmKey(), (b, s) -> b.writeUtf(s, MAX_SHORT_TEXT));
		}
	};

	public static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, Class<E> enumClass) {
		int ordinal = buf.readVarInt();
		E[] constants = enumClass.getEnumConstants();
		if (ordinal < 0 || ordinal >= constants.length) {
			throw new DecoderException("Invalid enum ordinal " + ordinal + " for " + enumClass.getSimpleName());
		}
		return constants[ordinal];
	}

	public static <T> List<T> readList(
		RegistryFriendlyByteBuf buf,
		StreamDecoder<? super RegistryFriendlyByteBuf, T> decoder,
		int maxSize
	) {
		int size = buf.readVarInt();
		if (size < 0 || size > maxSize) {
			throw new DecoderException("List size " + size + " out of allowed bounds (0.." + maxSize + ")");
		}
		List<T> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(decoder.decode(buf));
		}
		return list;
	}

	public static <T> void writeList(
		RegistryFriendlyByteBuf buf,
		List<T> items,
		StreamEncoder<? super RegistryFriendlyByteBuf, T> encoder
	) {
		buf.writeVarInt(items.size());
		for (T item : items) {
			encoder.encode(buf, item);
		}
	}
}
