package me.ogsammaenr.ogui.runtime;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Shared vanilla feedback and UI sound delivery helpers.
 */
public final class OguiFeedbackSupport {
	private OguiFeedbackSupport() {
	}

	public static void sendChatFeedback(ServerPlayer player, boolean success, Component message) {
		Component formatted = message.copy().withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED);
		player.sendSystemMessage(formatted);
		if (success) {
			playSuccessSound(player);
		} else {
			playErrorSound(player);
		}
	}

	public static void playClickSound(ServerPlayer player) {
		sendSound(player, SoundEvents.UI_BUTTON_CLICK, 0.7F, 1.0F);
	}

	public static void playSuccessSound(ServerPlayer player) {
		sendSound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP), 0.5F, 1.25F);
	}

	public static void playErrorSound(ServerPlayer player) {
		sendSound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.VILLAGER_NO), 0.7F, 1.0F);
	}

	private static void sendSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		if (player.connection != null && !player.hasDisconnected()) {
			player.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.PLAYERS,
				player.getX(),
				player.getY(),
				player.getZ(),
				volume,
				pitch,
				0L
			));
		}
	}
}
