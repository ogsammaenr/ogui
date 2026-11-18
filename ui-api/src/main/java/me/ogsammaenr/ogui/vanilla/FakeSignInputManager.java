package me.ogsammaenr.ogui.vanilla;

import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.runtime.OguiSession;
import me.ogsammaenr.ogui.runtime.OguiSessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-only Fake Sign text input bridge for vanilla clients without {@code ogui-client}.
 *
 * <p>Never mutates server world chunks or blocks. Sends a temporary per-player {@code OAK_SIGN}
 * illusion within block interaction range ({@code <= 4.0} tolerance required by vanilla
 * {@code AbstractSignEditScreen}), intercepts {@link ServerboundSignUpdatePacket}, restores the
 * real client block state/entity, and reopens the player's {@link OguiChestMenu} session.</p>
 */
public final class FakeSignInputManager {
	private static final Duration SIGN_SESSION_TTL = Duration.ofSeconds(60);
	private static final String LINE2_MARKER = "▲ Type above ▲";
	private static final FakeSignInputManager INSTANCE = new FakeSignInputManager();

	public record PendingFakeSign(
		UUID playerUuid,
		UUID sessionId,
		int expectedRevision,
		String widgetId,
		OguiWidget.InputMode mode,
		int maxLength,
		ResourceKey<Level> dimension,
		BlockPos fakePos,
		Instant expiresAt
	) {
		public boolean isExpired(Instant now) {
			return now.isAfter(expiresAt);
		}
	}

	private final Map<UUID, PendingFakeSign> pendingByPlayer = new ConcurrentHashMap<>();
	private final Set<UUID> chestToSignTransition = ConcurrentHashMap.newKeySet();

	private FakeSignInputManager() {
	}

	public static FakeSignInputManager instance() {
		return INSTANCE;
	}

	public boolean hasPending(UUID playerUuid) {
		return pendingByPlayer.containsKey(playerUuid);
	}

	public @Nullable PendingFakeSign pendingFor(UUID playerUuid) {
		return pendingByPlayer.get(playerUuid);
	}

	public boolean consumeChestToSignTransition(UUID playerUuid) {
		return chestToSignTransition.remove(playerUuid);
	}

	public void startInput(
		ServerPlayer player,
		OguiSession session,
		OguiWidget.TextInputWidget widget
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(session, "session");
		Objects.requireNonNull(widget, "widget");

		if (player.connection == null || player.hasDisconnected()) {
			return;
		}

		// Restore any older fake sign before opening a new one.
		cancelAndRestore(player);

		ServerLevel level = player.level();
		BlockPos fakePos = chooseFakeSignPos(player, level);

		// Preserve the OguiSession when closing the transient ChestMenu container.
		chestToSignTransition.add(player.getUUID());
		try {
			if (player.containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}
		} finally {
			chestToSignTransition.remove(player.getUUID());
		}

		PendingFakeSign pending = new PendingFakeSign(
			player.getUUID(),
			session.sessionId(),
			session.revision(),
			widget.widgetId(),
			widget.mode(),
			widget.maxLength(),
			level.dimension(),
			fakePos,
			Instant.now().plus(SIGN_SESSION_TTL)
		);
		pendingByPlayer.put(player.getUUID(), pending);

		player.connection.send(new ClientboundBlockUpdatePacket(fakePos, Blocks.OAK_SIGN.defaultBlockState()));
		player.connection.send(createFakeSignDataPacket(level, fakePos, widget));
		player.connection.send(new ClientboundOpenSignEditorPacket(fakePos, SignTextSlot.FRONT));
	}

	public boolean handleSignUpdatePacket(ServerPlayer player, ServerboundSignUpdatePacket packet) {
		PendingFakeSign pending = pendingByPlayer.get(player.getUUID());
		if (pending == null) {
			return false;
		}
		if (!pending.fakePos().equals(packet.pos())) {
			// Player submitted an unrelated world sign; clean up pending fake sign safely.
			cancelAndRestore(player);
			return false;
		}

		pendingByPlayer.remove(player.getUUID(), pending);
		restoreRealBlock(player, pending);

		String extracted = extractInputText(packet.lines(), pending.maxLength());
		OguiSessionManager.instance().completeFakeSignInput(
			player,
			pending.sessionId(),
			pending.expectedRevision(),
			pending.widgetId(),
			extracted
		);
		return true;
	}

	public void cancelAndRestore(ServerPlayer player) {
		PendingFakeSign pending = pendingByPlayer.remove(player.getUUID());
		if (pending != null) {
			restoreRealBlock(player, pending);
		}
	}

	public void onPlayerDisconnect(UUID playerUuid) {
		pendingByPlayer.remove(playerUuid);
		chestToSignTransition.remove(playerUuid);
	}

	public void tickCleanup(MinecraftServer server) {
		if (pendingByPlayer.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (var entry : pendingByPlayer.entrySet()) {
			PendingFakeSign pending = entry.getValue();
			if (!pending.isExpired(now)) {
				continue;
			}
			if (pendingByPlayer.remove(entry.getKey(), pending)) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					restoreRealBlock(player, pending);
				}
			}
		}
	}

	public void clearAll() {
		pendingByPlayer.clear();
		chestToSignTransition.clear();
	}

	public static BlockPos chooseFakeSignPos(ServerPlayer player, ServerLevel level) {
		BlockPos origin = player.blockPosition();
		int minY = level.getMinY();
		int maxY = level.getMaxY() - 1;
		int[][] offsets = {
			{0, 2, 0},
			{0, 3, 0},
			{0, -2, 0},
			{1, 2, 0},
			{-1, 2, 0},
			{0, 2, 1},
			{0, 2, -1},
			{0, 1, 0},
			{0, 0, 0}
		};
		for (int[] off : offsets) {
			int y = Math.clamp(origin.getY() + off[1], minY, maxY);
			BlockPos candidate = new BlockPos(origin.getX() + off[0], y, origin.getZ() + off[2]);
			if (player.isWithinBlockInteractionRange(candidate, 4.0)
				&& level.getBlockState(candidate).isAir()
				&& level.getBlockEntity(candidate) == null) {
				return candidate;
			}
		}
		int fallbackY = Math.clamp(origin.getY() + 2, minY, maxY);
		BlockPos fallback = new BlockPos(origin.getX(), fallbackY, origin.getZ());
		return player.isWithinBlockInteractionRange(fallback, 4.0) ? fallback : origin;
	}

	public static String extractInputText(List<String> lines, int maxLength) {
		if (lines == null || lines.isEmpty()) {
			return "";
		}
		String l0 = sanitizeLine(lines.get(0));
		String l1 = lines.size() > 1 ? sanitizeLine(lines.get(1)) : "";
		String combined = (l0 + l1).trim();
		if (combined.length() > maxLength) {
			return combined.substring(0, maxLength);
		}
		return combined;
	}

	private static String sanitizeLine(@Nullable String raw) {
		if (raw == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(raw);
		return stripped == null ? "" : stripped;
	}

	private static void restoreRealBlock(ServerPlayer player, PendingFakeSign pending) {
		if (player.connection == null || player.hasDisconnected()) {
			return;
		}
		ServerLevel level = player.level();
		if (!level.dimension().equals(pending.dimension())) {
			return;
		}
		BlockPos pos = pending.fakePos();
		if (!level.hasChunkAt(pos)) {
			return;
		}
		player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
		BlockEntity realEntity = level.getBlockEntity(pos);
		if (realEntity != null) {
			Packet<?> bePacket = realEntity.getUpdatePacket();
			if (bePacket != null) {
				player.connection.send(bePacket);
			}
		}
	}

	private static ClientboundBlockEntityDataPacket createFakeSignDataPacket(
		ServerLevel level,
		BlockPos fakePos,
		OguiWidget.TextInputWidget widget
	) {
		SignBlockEntity tempSign = new SignBlockEntity(fakePos, Blocks.OAK_SIGN.defaultBlockState());
		tempSign.setLevel(level);

		String initial = widget.value();
		String line0 = initial.length() > 15 ? initial.substring(0, 15) : initial;
		String line1 = initial.length() > 15 ? initial.substring(15, Math.min(30, initial.length())) : "";
		String labelText = widget.label().getString();
		String line3 = labelText.length() > 18 ? labelText.substring(0, 18) : labelText;

		List<Component> messages = List.of(
			Component.literal(line0),
			Component.literal(line1),
			Component.literal(LINE2_MARKER),
			Component.literal(line3)
		);
		SignText signText = new SignText(messages, messages, DyeColor.BLACK, false);

		return ClientboundBlockEntityDataPacket.create(tempSign, (be, registries) -> {
			try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(be.problemPath(), org.slf4j.LoggerFactory.getLogger(FakeSignInputManager.class))) {
				TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
				output.store("front_text", SignText.CODEC, signText);
				output.store("back_text", SignText.CODEC, SignText.EMPTY);
				output.putBoolean("is_waxed", false);
				return output.buildResult();
			}
		});
	}
}
