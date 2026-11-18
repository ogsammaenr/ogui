package me.ogsammaenr.ogui.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.network.S2CAssetUnavailablePayload;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared mock-player, packet capture, and Fabric channel simulation helpers for {@code ogui-api} GameTests.
 */
final class OguiGameTestSupport {
	private OguiGameTestSupport() {
	}

	static ServerPlayer createOnlineMockPlayer(
		MinecraftServer server,
		ServerLevel level,
		UUID uuid,
		String username,
		GameType mode,
		AtomicReference<EmbeddedChannel> outChannel
	) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, username), false);
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return mode;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		mode.updatePlayerAbilities(player.getAbilities());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		if (outChannel != null) {
			outChannel.set(channel);
		}
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	static void registerOguiClientChannels(ServerPlayer player) {
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(new RegistrationPayload(
			RegistrationPayload.REGISTER,
			List.of(
				S2CServerHelloPayload.TYPE.id(),
				S2COpenScreenPayload.TYPE.id(),
				S2CUpdateScreenPayload.TYPE.id(),
				S2CCloseScreenPayload.TYPE.id(),
				S2CFeedbackPayload.TYPE.id(),
				S2CAssetChunkPayload.TYPE.id(),
				S2CAssetUnavailablePayload.TYPE.id()
			)
		)));
	}

	static List<Packet<?>> drainOutboundPackets(EmbeddedChannel channel) {
		channel.flush();
		channel.runPendingTasks();
		List<Packet<?>> drained = new ArrayList<>();
		Object msg;
		while ((msg = channel.readOutbound()) != null) {
			if (msg instanceof Packet<?> packet) {
				drained.add(packet);
			}
		}
		return drained;
	}

	static int findSlotByName(ChestMenu menu, String expectedName) {
		int limit = menu.getRowCount() * 9;
		for (int slot = 0; slot < limit; slot++) {
			ItemStack stack = menu.getSlot(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			var customName = stack.get(DataComponents.CUSTOM_NAME);
			if (customName != null && expectedName.equals(customName.getString())) {
				return slot;
			}
		}
		return -1;
	}

	static void cleanupPlayers(MinecraftServer server, ServerPlayer... players) {
		for (ServerPlayer player : players) {
			if (player == null) {
				continue;
			}
			try {
				player.closeContainer();
			} catch (Exception ignored) {
			}
			server.getPlayerList().remove(player);
		}
	}
}
