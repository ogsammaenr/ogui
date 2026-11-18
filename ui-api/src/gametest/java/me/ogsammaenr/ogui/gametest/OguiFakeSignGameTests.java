package me.ogsammaenr.ogui.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.runtime.OguiSessionManager;
import me.ogsammaenr.ogui.vanilla.FakeSignInputManager;
import me.ogsammaenr.ogui.vanilla.OguiChestMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies packet-only Fake Sign text input for vanilla clients:
 * zero world mutation, interaction range validation, mixin interception, restoration packet, and live ChestMenu reopen.
 */
public final class OguiFakeSignGameTests {
	@GameTest
	public void fakeSignCapturesInputRestoresWorldAndReopensMenu(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		Identifier screenId = Identifier.fromNamespaceAndPath("ogui_test", "sign_" + UUID.randomUUID().toString().substring(0, 8));

		Ogui.service().registerScreen(screenId, ctx -> {
			String search = ctx.stateOrDefault("search", "");
			int price = ctx.stateInt("price", 10);

			OguiScreenModel.PagedGridBuilder builder = OguiScreenModel.pagedGrid("Sign Search Test")
				.addHeaderWidget(OguiWidget.textInput("search", OguiIcon.item("minecraft:compass"), OguiText.nonItalic("Search"))
					.value(search)
					.mode(OguiWidget.InputMode.SEARCH)
					.build())
				.addHeaderWidget(OguiWidget.textInput("price_input", OguiIcon.item("minecraft:gold_ingot"), OguiText.nonItalic("Price"))
					.stateKey("price")
					.value(Integer.toString(price))
					.mode(OguiWidget.InputMode.INTEGER)
					.build());

			for (String item : List.of("emerald_block", "diamond_block", "iron_block")) {
				if (search.isEmpty() || item.contains(search.replace(' ', '_'))) {
					builder.addEntry(OguiWidget.button("entry_" + item, "minecraft:stone", item, ChatFormatting.WHITE).build());
				}
			}
			return builder.build();
		});

		AtomicReference<EmbeddedChannel> chRef = new AtomicReference<>();
		ServerPlayer player = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "FakeSignPlayer", GameType.SURVIVAL, chRef);
		player.teleportTo(10.5, 80.0, 10.5);

		try {
			helper.assertTrue(Ogui.service().openScreen(player, screenId), "Expected ChestMenu open");
			helper.assertTrue(player.containerMenu instanceof OguiChestMenu, "Expected OguiChestMenu");
			OguiChestMenu menu = (OguiChestMenu) player.containerMenu;
			helper.assertTrue(OguiGameTestSupport.findSlotByName(menu, "emerald_block") >= 0, "Expected emerald_block initially");

			int searchSlot = OguiGameTestSupport.findSlotByName(menu, "Search");
			helper.assertTrue(searchSlot >= 0, "Expected Search TextInputWidget in header row");

			// Click Search widget -> opens Fake Sign for vanilla client
			OguiGameTestSupport.drainOutboundPackets(chRef.get());
			menu.clicked(searchSlot, 0, ContainerInput.PICKUP, player);

			helper.assertTrue(!(player.containerMenu instanceof OguiChestMenu), "ChestMenu must close during Fake Sign editing");
			helper.assertTrue(OguiSessionManager.instance().activeSession(player.getUUID()) != null,
				"OguiSession must stay alive in RAM during Fake Sign transition");

			FakeSignInputManager.PendingFakeSign pending = FakeSignInputManager.instance().pendingFor(player.getUUID());
			helper.assertTrue(pending != null, "Expected PendingFakeSign recorded");
			BlockPos fakePos = pending.fakePos();
			helper.assertTrue(player.isWithinBlockInteractionRange(fakePos, 4.0),
				"fakePos must be within vanilla AbstractSignEditScreen 4.0 interaction tolerance");

			// Verify server world itself was NOT mutated
			BlockState realState = level.getBlockState(fakePos);
			helper.assertTrue(!realState.is(Blocks.OAK_SIGN), "ServerLevel must NOT contain a real OAK_SIGN at fakePos");
			helper.assertTrue(level.getBlockEntity(fakePos) == null, "ServerLevel must NOT contain a real SignBlockEntity");

			List<Packet<?>> openSignPackets = OguiGameTestSupport.drainOutboundPackets(chRef.get());
			helper.assertTrue(openSignPackets.stream().anyMatch(p -> p instanceof ClientboundBlockUpdatePacket bu
				&& bu.getPos().equals(fakePos) && bu.getBlockState().is(Blocks.OAK_SIGN)),
				"Expected ClientboundBlockUpdatePacket(OAK_SIGN) at fakePos");
			helper.assertTrue(openSignPackets.stream().anyMatch(p -> p instanceof ClientboundBlockEntityDataPacket be
				&& be.getPos().equals(fakePos)),
				"Expected ClientboundBlockEntityDataPacket at fakePos");
			helper.assertTrue(openSignPackets.stream().anyMatch(p -> p instanceof ClientboundOpenSignEditorPacket oe
				&& oe.pos().equals(fakePos) && oe.slot() == SignTextSlot.FRONT),
				"Expected ClientboundOpenSignEditorPacket at fakePos");

			// Client submits Sign update -> intercepted by ServerGamePacketListenerImplMixin
			player.connection.handleSignUpdate(new ServerboundSignUpdatePacket(
				fakePos,
				List.of("emerald ", "block", "▲ Type above ▲", "Search"),
				SignTextSlot.FRONT
			));

			helper.assertTrue(!FakeSignInputManager.instance().hasPending(player.getUUID()),
				"Pending fake sign must be consumed on submission");
			List<Packet<?>> afterSignPackets = OguiGameTestSupport.drainOutboundPackets(chRef.get());
			helper.assertTrue(afterSignPackets.stream().anyMatch(p -> p instanceof ClientboundBlockUpdatePacket bu
				&& bu.getPos().equals(fakePos) && bu.getBlockState().equals(realState)),
				"Expected ClientboundBlockUpdatePacket restoring original BlockState at fakePos");

			// Verify ChestMenu reopened with updated search filter
			helper.assertTrue(player.containerMenu instanceof OguiChestMenu, "Expected OguiChestMenu to reopen after Fake Sign");
			OguiChestMenu reopened = (OguiChestMenu) player.containerMenu;
			helper.assertTrue(OguiGameTestSupport.findSlotByName(reopened, "emerald_block") >= 0,
				"Expected filtered emerald_block entry");
			helper.assertTrue(OguiGameTestSupport.findSlotByName(reopened, "diamond_block") < 0,
				"Expected diamond_block filtered out by Fake Sign input 'emerald block'");

			// Integer validation guard: invalid integer leaves price unchanged and reopens ChestMenu cleanly
			int priceSlot = OguiGameTestSupport.findSlotByName(reopened, "Price");
			helper.assertTrue(priceSlot >= 0, "Expected Price TextInputWidget");
			reopened.clicked(priceSlot, 0, ContainerInput.PICKUP, player);
			FakeSignInputManager.PendingFakeSign pendingPrice = FakeSignInputManager.instance().pendingFor(player.getUUID());
			helper.assertTrue(pendingPrice != null, "Expected second fake sign pending for Price");
			player.connection.handleSignUpdate(new ServerboundSignUpdatePacket(
				pendingPrice.fakePos(),
				List.of("not_a_number", "", "▲ Type above ▲", "Price"),
				SignTextSlot.FRONT
			));
			helper.assertTrue(player.containerMenu instanceof OguiChestMenu,
				"Expected ChestMenu to reopen even after invalid integer rejection");
			helper.assertTrue("10".equals(OguiSessionManager.instance().activeSession(player.getUUID()).request().state().get("price"))
				|| !OguiSessionManager.instance().activeSession(player.getUUID()).request().state().containsKey("price"),
				"Invalid integer input must not corrupt state");

			// Disconnect cleanup: opening Fake Sign then disconnecting clears pending sign & active session cleanly
			OguiChestMenu currentMenu = (OguiChestMenu) player.containerMenu;
			int searchAgainSlot = OguiGameTestSupport.findSlotByName(currentMenu, "Search");
			currentMenu.clicked(searchAgainSlot, 0, ContainerInput.PICKUP, player);
			helper.assertTrue(FakeSignInputManager.instance().hasPending(player.getUUID()),
				"Expected pending fake sign before disconnect");
			OguiSessionManager.instance().onPlayerDisconnect(player.getUUID());
			helper.assertTrue(!FakeSignInputManager.instance().hasPending(player.getUUID()),
				"Pending fake sign must be cleaned up on disconnect");
			helper.assertTrue(OguiSessionManager.instance().activeSession(player.getUUID()) == null,
				"Active session must be cleaned up on disconnect");

			helper.succeed();
		} finally {
			OguiGameTestSupport.cleanupPlayers(server, player);
		}
	}

	@GameTest
	public void fakeSignRestoresRealSolidBlockInCrampedArea(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		Identifier screenId = Identifier.fromNamespaceAndPath("ogui_test", "cramped_sign_" + UUID.randomUUID().toString().substring(0, 8));

		Ogui.service().registerScreen(screenId, ctx -> OguiScreenModel.pagedGrid("Cramped Sign")
			.addHeaderWidget(OguiWidget.textInput("note", OguiIcon.item("minecraft:name_tag"), OguiText.nonItalic("Note"))
				.value(ctx.stateOrDefault("note", ""))
				.build())
			.build());

		AtomicReference<EmbeddedChannel> chRef = new AtomicReference<>();
		ServerPlayer player = OguiGameTestSupport.createOnlineMockPlayer(server, level, UUID.randomUUID(), "CrampedSign", GameType.SURVIVAL, chRef);
		BlockPos base = new BlockPos(40, 90, 40);
		player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);

		// Surround all candidate air offsets with real STONE so fallback pos replaces STONE in client illusion only
		int[][] offsets = {{0,2,0},{0,3,0},{0,-2,0},{1,2,0},{-1,2,0},{0,2,1},{0,2,-1},{0,1,0},{0,0,0}};
		for (int[] off : offsets) {
			level.setBlock(base.offset(off[0], off[1], off[2]), Blocks.STONE.defaultBlockState(), 18);
		}

		try {
			helper.assertTrue(Ogui.service().openScreen(player, screenId), "Expected ChestMenu open");
			OguiChestMenu menu = (OguiChestMenu) player.containerMenu;
			int noteSlot = OguiGameTestSupport.findSlotByName(menu, "Note");
			helper.assertTrue(noteSlot >= 0, "Expected Note TextInputWidget");

			OguiGameTestSupport.drainOutboundPackets(chRef.get());
			menu.clicked(noteSlot, 0, ContainerInput.PICKUP, player);
			FakeSignInputManager.PendingFakeSign pending = FakeSignInputManager.instance().pendingFor(player.getUUID());
			helper.assertTrue(pending != null, "Expected pending fake sign");
			BlockPos fakePos = pending.fakePos();
			helper.assertTrue(level.getBlockState(fakePos).is(Blocks.STONE),
				"Real world block at fakePos must remain STONE while sign editor is open");

			player.connection.handleSignUpdate(new ServerboundSignUpdatePacket(
				fakePos, List.of("hello", "", "", ""), SignTextSlot.FRONT
			));
			List<Packet<?>> restorePackets = OguiGameTestSupport.drainOutboundPackets(chRef.get());
			helper.assertTrue(restorePackets.stream().anyMatch(p -> p instanceof ClientboundBlockUpdatePacket bu
				&& bu.getPos().equals(fakePos) && bu.getBlockState().is(Blocks.STONE)),
				"Expected restoration packet to send real STONE back to client");
			helper.assertTrue(level.getBlockState(fakePos).is(Blocks.STONE),
				"Real world block at fakePos must stay STONE after fake sign finishes");
			helper.assertTrue("hello".equals(OguiSessionManager.instance().activeSession(player.getUUID()).request().state().get("note")),
				"Expected captured note text in session state");

			helper.succeed();
		} finally {
			for (int[] off : offsets) {
				level.setBlock(base.offset(off[0], off[1], off[2]), Blocks.AIR.defaultBlockState(), 18);
			}
			OguiGameTestSupport.cleanupPlayers(server, player);
		}
	}
}
