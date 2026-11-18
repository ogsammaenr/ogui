package me.ogsammaenr.ogui.vanilla;

import me.ogsammaenr.ogui.runtime.OguiSession;
import me.ogsammaenr.ogui.runtime.OguiSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Live server-side {@link OguiReadOnlyChestMenu} instance synchronized with an {@link OguiSession}.
 */
public final class OguiChestMenu extends OguiReadOnlyChestMenu {
	private final OguiSessionManager sessionManager;
	private final UUID sessionId;
	private OguiChestLayout.PlannedChestView plannedView;

	public OguiChestMenu(
		int containerId,
		Inventory playerInventory,
		OguiSessionManager sessionManager,
		OguiSession initialSession,
		OguiChestLayout.PlannedChestView initialPlan
	) {
		super(containerId, playerInventory, initialPlan.rows());
		this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
		this.sessionId = initialSession.sessionId();
		this.plannedView = Objects.requireNonNull(initialPlan, "initialPlan");
		renderPlan(initialPlan);
	}

	public UUID sessionId() {
		return sessionId;
	}

	public void renderSession(OguiSession session, MinecraftServer server) {
		OguiChestLayout.PlannedChestView nextPlan = OguiChestLayout.plan(session, server);
		renderPlan(nextPlan);
	}

	private void renderPlan(OguiChestLayout.PlannedChestView plan) {
		this.plannedView = plan;
		Container container = getContainer();
		fillBackground(container);
		for (var entry : plan.slots().entrySet()) {
			int slot = entry.getKey();
			if (slot >= 0 && slot < menuSize()) {
				container.setItem(slot, entry.getValue().icon());
			}
		}
		setCarried(ItemStack.EMPTY);
		broadcastChanges();
	}

	@Override
	protected void onMenuSlotClick(ServerPlayer player, int slotIndex, int buttonNum, ContainerInput containerInput) {
		OguiChestLayout.SlotBinding binding = plannedView.slots().get(slotIndex);
		if (binding == null || binding.actionId() == null) {
			return;
		}
		sessionManager.handleChestInteraction(player, this, binding, buttonNum, containerInput);
	}

	@Override
	protected void onMenuClosed(Player player) {
		if (!FakeSignInputManager.instance().consumeChestToSignTransition(player.getUUID())) {
			sessionManager.onChestMenuClosed(player.getUUID(), this.sessionId);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive() && !player.isRemoved();
	}
}
