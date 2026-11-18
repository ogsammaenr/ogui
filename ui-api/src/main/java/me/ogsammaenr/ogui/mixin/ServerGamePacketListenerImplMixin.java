package me.ogsammaenr.ogui.mixin;

import me.ogsammaenr.ogui.vanilla.FakeSignInputManager;
import me.ogsammaenr.ogui.vanilla.OguiReadOnlyChestMenu;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces anti-dupe protections while any {@link OguiReadOnlyChestMenu} is open and intercepts
 * packet-only Fake Sign submissions before vanilla chunk {@code SignBlockEntity} lookup runs.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Unique
	private AbstractContainerMenu ogui$preClickMenu;

	@Inject(
		method = "handleContainerClick",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER
		)
	)
	private void ogui$capturePreClickMenu(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		this.ogui$preClickMenu = this.player.containerMenu;
	}

	@Inject(method = "handleContainerClick", at = @At("TAIL"))
	private void ogui$enforcePostClickResync(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		AbstractContainerMenu before = this.ogui$preClickMenu;
		this.ogui$preClickMenu = null;
		if (this.player.containerMenu instanceof OguiReadOnlyChestMenu menu) {
			if (menu.consumeForceResync()) {
				menu.syncClientState(this.player, menu.consumePendingHotbarResync());
			}
			return;
		}
		if (before instanceof OguiReadOnlyChestMenu) {
			OguiReadOnlyChestMenu.scrubLeakedOguiItems(this.player.getInventory());
			OguiReadOnlyChestMenu.syncPlayerInventoryAndOffhand(this.player, -1);
		}
	}

	@Inject(
		method = "handlePlayerAction",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void ogui$blockOffhandAndDropWhileMenuOpen(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (!(this.player.containerMenu instanceof OguiReadOnlyChestMenu menu)) {
			return;
		}
		ServerboundPlayerActionPacket.Action action = packet.getAction();
		if (action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
			|| action == ServerboundPlayerActionPacket.Action.DROP_ITEM
			|| action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
			menu.syncClientState(this.player, -1);
			ci.cancel();
		}
	}

	@Inject(
		method = "handleSetCreativeModeSlot",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void ogui$blockCreativeIconInjection(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (this.player.containerMenu instanceof OguiReadOnlyChestMenu menu) {
			menu.syncClientState(this.player, -1);
			ci.cancel();
			return;
		}
		if (OguiReadOnlyChestMenu.hasOguiItemMarker(packet.itemStack())) {
			OguiReadOnlyChestMenu.scrubLeakedOguiItems(this.player.getInventory());
			OguiReadOnlyChestMenu.syncPlayerInventoryAndOffhand(this.player, -1);
			ci.cancel();
		}
	}

	@Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
	private void ogui$interceptFakeSignUpdate(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
		if (!FakeSignInputManager.instance().hasPending(this.player.getUUID())) {
			return;
		}
		PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, this.player.level());
		if (FakeSignInputManager.instance().handleSignUpdatePacket(this.player, packet)) {
			ci.cancel();
		}
	}
}
