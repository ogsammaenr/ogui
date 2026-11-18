package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.OguiIcon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Resolves {@link OguiIcon} descriptors to client renderable {@link ItemStack} instances.
 */
public final class OguiIconResolver {
	private OguiIconResolver() {
	}

	public static ItemStack toRenderStack(OguiIcon icon) {
		if (icon.itemStackOverride() != null && !icon.itemStackOverride().isEmpty()) {
			ItemStack copy = icon.itemStackOverride().copy();
			copy.setCount(icon.count());
			if (icon.glowing()) {
				copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return copy;
		}
		if ((icon.profile() != null || icon.playerHeadUuid() != null) && icon.itemId().endsWith("player_head")) {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD, icon.count());
			ResolvableProfile profile = icon.profile() != null
				? icon.profile()
				: ResolvableProfile.createUnresolved(icon.playerHeadUuid());
			head.set(DataComponents.PROFILE, profile);
			if (icon.glowing()) {
				head.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return head;
		}
		Identifier parsed = Identifier.tryParse(icon.itemId());
		Item resolved = parsed == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(parsed).orElse(Items.PAPER);
		ItemStack stack = new ItemStack(resolved, icon.count());
		if (icon.glowing()) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}
}
