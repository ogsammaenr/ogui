package me.ogsammaenr.ogui.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.vanilla.OguiChestMenu;
import me.ogsammaenr.ogui.vanilla.OguiReadOnlyChestMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies {@link OguiReadOnlyChestMenu} and {@code ServerGamePacketListenerImplMixin}
 * block F-swap, hotbar swap, quick-move, throw/clone/drag, offhand action, and creative dupe injection.
 */
public final class OguiChestAntiDupeGameTests {
	@GameTest
	public void chestFallbackBlocksAllVanillaAndCreativeDupeExploits(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		Identifier screenId = Identifier.fromNamespaceAndPath("ogui_test", "dupe_" + UUID.randomUUID().toString().substring(0, 8));

		Ogui.service().registerScreen(screenId, ctx -> OguiScreenModel.fixedGrid("Anti-Dupe", 3)
			.slot(13, OguiWidget.button("prize", OguiIcon.item("minecraft:nether_star"), OguiText.nonItalic("Prize", ChatFormatting.AQUA)).build())
			.build());

		AtomicReference<EmbeddedChannel> survChRef = new AtomicReference<>();
		AtomicReference<EmbeddedChannel> creaChRef = new AtomicReference<>();
		ServerPlayer survival = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "DupeSurv", GameType.SURVIVAL, survChRef);
		ServerPlayer creative = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "DupeCrea", GameType.CREATIVE, creaChRef);

		survival.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
		survival.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD, 1));

		try {
			helper.assertTrue(Ogui.service().openScreen(survival, screenId), "Expected survival ChestMenu open");
			helper.assertTrue(Ogui.service().openScreen(creative, screenId), "Expected creative ChestMenu open");
			helper.assertTrue(survival.containerMenu instanceof OguiChestMenu, "Expected OguiChestMenu");
			helper.assertTrue(creative.containerMenu instanceof OguiChestMenu, "Expected OguiChestMenu");

			OguiChestMenu survMenu = (OguiChestMenu) survival.containerMenu;
			OguiChestMenu creaMenu = (OguiChestMenu) creative.containerMenu;

			ItemStack prizeCopy = creaMenu.getSlot(13).getItem().copy();
			CustomData marker = prizeCopy.get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(marker != null && marker.copyTag().getBooleanOr(OguiReadOnlyChestMenu.OGUI_ITEM_MARKER_KEY, false),
				"Expected menu icon to carry ogui_item marker");

			// Direct menu exploit attempts
			survMenu.clicked(13, 0, ContainerInput.THROW, survival);
			survMenu.clicked(13, 0, ContainerInput.CLONE, survival);
			survMenu.clicked(13, 0, ContainerInput.PICKUP_ALL, survival);
			survMenu.clicked(13, 0, ContainerInput.QUICK_CRAFT, survival);
			helper.assertTrue(survMenu.quickMoveStack(survival, 13).isEmpty(), "quickMoveStack must return EMPTY");
			helper.assertTrue(survMenu.getCarried().isEmpty(), "Carried stack must remain EMPTY");

			// Packet F-swap (button 40) on slot 13
			OguiGameTestSupport.drainOutboundPackets(survChRef.get());
			Int2ObjectMap<HashedStack> fSwapChanged = new Int2ObjectOpenHashMap<>();
			fSwapChanged.put(13, HashedStack.EMPTY);
			survival.connection.handleContainerClick(new ServerboundContainerClickPacket(
				survMenu.containerId, survMenu.getStateId(), (short) 13, (byte) 40,
				ContainerInput.SWAP, fSwapChanged, HashedStack.EMPTY
			));
			List<Packet<?>> survPackets = OguiGameTestSupport.drainOutboundPackets(survChRef.get());
			helper.assertTrue(survival.getOffhandItem().is(Items.SHIELD), "Offhand Shield must remain intact");
			helper.assertTrue(survPackets.stream().anyMatch(p -> p instanceof ClientboundSetPlayerInventoryPacket inv
				&& inv.slot() == Inventory.SLOT_OFFHAND && inv.contents().is(Items.SHIELD)),
				"Server must resync offhand Shield");
			helper.assertTrue(survPackets.stream().anyMatch(p -> p instanceof ClientboundSetCursorItemPacket cur
				&& cur.contents().isEmpty()),
				"Server must clear client cursor");
			helper.assertTrue(survPackets.stream().anyMatch(p -> p instanceof ClientboundContainerSetContentPacket content
				&& content.containerId() == survMenu.containerId && !content.items().get(13).isEmpty()),
				"Server must resync container content");

			// Hotbar 1-key swap (button 0)
			OguiGameTestSupport.drainOutboundPackets(survChRef.get());
			survival.connection.handleContainerClick(new ServerboundContainerClickPacket(
				survMenu.containerId, survMenu.getStateId(), (short) 13, (byte) 0,
				ContainerInput.SWAP, fSwapChanged, HashedStack.EMPTY
			));
			List<Packet<?>> hotbarPackets = OguiGameTestSupport.drainOutboundPackets(survChRef.get());
			helper.assertTrue(survival.getInventory().getItem(0).is(Items.DIAMOND) && survival.getInventory().getItem(0).getCount() == 3,
				"Hotbar diamond must remain untouched");
			helper.assertTrue(hotbarPackets.stream().anyMatch(p -> p instanceof ClientboundSetPlayerInventoryPacket inv
				&& inv.slot() == 0 && inv.contents().is(Items.DIAMOND)),
				"Server must resync hotbar slot 0");

			// Creative PlayerAction offhand swap & Creative slot packet injection blocked
			creative.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN
			));
			creative.connection.handleSetCreativeModeSlot(
				new ServerboundSetCreativeModeSlotPacket(InventoryMenu.SHIELD_SLOT, new ItemStack(Items.NETHERITE_BLOCK, 1))
			);
			helper.assertTrue(creative.getOffhandItem().isEmpty(), "Creative injection while menu open must be blocked");

			// Even after closing menu, forged Creative injection/drop of marked ogui_item must be rejected
			creative.closeContainer();
			creative.connection.handleSetCreativeModeSlot(
				new ServerboundSetCreativeModeSlotPacket(InventoryMenu.SHIELD_SLOT, prizeCopy.copy())
			);
			helper.assertTrue(creative.getOffhandItem().isEmpty(), "Marked ogui_item Creative injection after close must be blocked");

			List<ItemEntity> beforeDrop = level.getEntitiesOfClass(ItemEntity.class, creative.getBoundingBox().inflate(8.0));
			creative.connection.handleSetCreativeModeSlot(
				new ServerboundSetCreativeModeSlotPacket(-1, prizeCopy.copy())
			);
			List<ItemEntity> afterDrop = level.getEntitiesOfClass(ItemEntity.class, creative.getBoundingBox().inflate(8.0));
			helper.assertTrue(afterDrop.size() == beforeDrop.size(), "Marked ogui_item Creative drop must be blocked");

			helper.succeed();
		} finally {
			OguiGameTestSupport.cleanupPlayers(server, survival, creative);
		}
	}
}
