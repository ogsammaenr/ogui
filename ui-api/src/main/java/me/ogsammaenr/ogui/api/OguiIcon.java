package me.ogsammaenr.ogui.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable icon descriptor supporting item IDs, player heads, custom {@link ItemStack} overrides, stack counts, and glint.
 */
public record OguiIcon(
	String itemId,
	@Nullable UUID playerHeadUuid,
	@Nullable ResolvableProfile profile,
	@Nullable ItemStack itemStackOverride,
	int count,
	boolean glowing
) {
	public OguiIcon {
		if (itemId == null || itemId.isBlank()) {
			throw new IllegalArgumentException("OguiIcon itemId must not be blank.");
		}
		count = Math.clamp(count, 1, 99);
		itemStackOverride = (itemStackOverride == null || itemStackOverride.isEmpty()) ? null : itemStackOverride.copy();
	}

	public static OguiIcon item(String itemId) {
		return new OguiIcon(itemId, null, null, null, 1, false);
	}

	public static OguiIcon item(Item item) {
		Objects.requireNonNull(item, "item");
		return item(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	public static OguiIcon itemStack(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty()) {
			throw new IllegalArgumentException("ItemStack override must not be empty.");
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return new OguiIcon(id, null, null, stack, Math.clamp(stack.getCount(), 1, 99), stack.hasFoil());
	}

	public static OguiIcon playerHead(UUID playerUuid) {
		Objects.requireNonNull(playerUuid, "playerUuid");
		return new OguiIcon("minecraft:player_head", playerUuid, null, null, 1, false);
	}

	public static OguiIcon playerHead(ResolvableProfile profile) {
		Objects.requireNonNull(profile, "profile");
		UUID id = profile.partialProfile().id();
		return new OguiIcon("minecraft:player_head", id, profile, null, 1, false);
	}

	public OguiIcon withCount(int nextCount) {
		return new OguiIcon(itemId, playerHeadUuid, profile, itemStackOverride, nextCount, glowing);
	}

	public OguiIcon withGlowing(boolean nextGlowing) {
		return new OguiIcon(itemId, playerHeadUuid, profile, itemStackOverride, count, nextGlowing);
	}
}
