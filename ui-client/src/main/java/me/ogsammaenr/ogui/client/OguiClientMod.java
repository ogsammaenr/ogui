package me.ogsammaenr.ogui.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.client.assets.OguiClientAssetManager;
import me.ogsammaenr.ogui.client.render.OguiStructurePipRenderer;
import me.ogsammaenr.ogui.client.test.OguiClientTestCommands;
import me.ogsammaenr.ogui.network.C2SClientHelloPayload;
import me.ogsammaenr.ogui.network.C2SShortcutTriggerPayload;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.network.S2CAssetUnavailablePayload;
import me.ogsammaenr.ogui.network.S2CCloseScreenPayload;
import me.ogsammaenr.ogui.network.S2CFeedbackPayload;
import me.ogsammaenr.ogui.network.S2COpenScreenPayload;
import me.ogsammaenr.ogui.network.S2CServerHelloPayload;
import me.ogsammaenr.ogui.network.S2CUpdateScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Client entrypoint for {@code ogui-client}, registering payload receivers,
 * protocol handshake, local preferences, and quick-menu keybindings.
 */
public final class OguiClientMod implements ClientModInitializer {
	public static final String MOD_ID = "ogui-client";
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Ogui.NAMESPACE, "general"));

	private final OguiClientSessionState sessionState = new OguiClientSessionState();
	private final OguiClientAssetManager assetManager = OguiClientAssetManager.shared();
	private OguiClientPreferences preferences;
	private KeyMapping quickMenuKey;
	private KeyMapping quickSlot1Key;
	private KeyMapping quickSlot2Key;
	private KeyMapping quickSlot3Key;

	@Override
	public void onInitializeClient() {
		this.preferences = OguiClientPreferences.loadFromConfigDir();

		this.quickMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ogui.quick_menu",
			InputConstants.KEY_G,
			KEY_CATEGORY
		));
		this.quickSlot1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ogui.quick_slot_1",
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY
		));
		this.quickSlot2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ogui.quick_slot_2",
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY
		));
		this.quickSlot3Key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.ogui.quick_slot_3",
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY
		));

		PictureInPictureRendererRegistry.register(ctx -> new OguiStructurePipRenderer());
		registerNetworking();
		registerTickHandlers();
		OguiClientTestCommands.register(preferences, sessionState, quickMenuKey);
	}

	private void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(S2CServerHelloPayload.TYPE, (payload, context) ->
			context.client().execute(() -> sessionState.onServerHello(payload))
		);

		ClientPlayNetworking.registerGlobalReceiver(S2COpenScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				Minecraft mc = context.client();
				sessionState.onOpenScreen(payload);
				if (mc.gui.screen() instanceof OguiScreen current) {
					current.suppressNextCloseNotify();
				}
				mc.gui.setScreen(new OguiScreen(
					sessionState,
					preferences,
					payload.sessionId(),
					payload.revision(),
					payload.snapshot()
				));
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(S2CUpdateScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				if (!sessionState.onUpdateScreen(payload)) {
					return;
				}
				Minecraft mc = context.client();
				if (mc.gui.screen() instanceof OguiScreen screen && screen.sessionId().equals(payload.sessionId())) {
					screen.applyLiveUpdate(payload.sessionId(), payload.revision(), payload.snapshot());
				}
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(S2CCloseScreenPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				if (!sessionState.onCloseScreen(payload)) {
					return;
				}
				Minecraft mc = context.client();
				if (mc.gui.screen() instanceof OguiScreen screen && screen.sessionId().equals(payload.sessionId())) {
					screen.suppressNextCloseNotify();
					mc.gui.setScreen(null);
				}
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(S2CFeedbackPayload.TYPE, (payload, context) ->
			context.client().execute(() -> {
				if (!sessionState.onFeedback(payload, System.currentTimeMillis())) {
					return;
				}
				if (preferences.uiSoundsEnabled()) {
					playFeedbackSound(context.client(), preferences, payload.sound());
				}
			})
		);

		ClientPlayNetworking.registerGlobalReceiver(S2CAssetChunkPayload.TYPE, (payload, context) ->
			context.client().execute(() -> assetManager.onAssetChunk(payload))
		);

		ClientPlayNetworking.registerGlobalReceiver(S2CAssetUnavailablePayload.TYPE, (payload, context) ->
			context.client().execute(() -> assetManager.onAssetUnavailable(payload.sha256()))
		);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> trySendClientHello());
		ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> trySendClientHello());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			sessionState.resetOnDisconnect();
			assetManager.resetSessionTransfers();
		});
	}

	private void registerTickHandlers() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.gui.screen() != null || !sessionState.handshakeAccepted()) {
				return;
			}
			List<S2CServerHelloPayload.ShortcutInfo> shortcuts = sessionState.shortcuts();
			if (shortcuts.isEmpty()) {
				while (quickMenuKey.consumeClick()) {}
				while (quickSlot1Key.consumeClick()) {}
				while (quickSlot2Key.consumeClick()) {}
				while (quickSlot3Key.consumeClick()) {}
				return;
			}
			if (quickMenuKey.consumeClick()) {
				if (shortcuts.size() == 1) {
					triggerShortcut(shortcuts.getFirst().id());
				} else {
					client.gui.setScreen(new OguiQuickLauncherScreen(shortcuts, preferences));
				}
			}
			consumeIndexedShortcut(quickSlot1Key, shortcuts, 0);
			consumeIndexedShortcut(quickSlot2Key, shortcuts, 1);
			consumeIndexedShortcut(quickSlot3Key, shortcuts, 2);
		});
	}

	private void consumeIndexedShortcut(
		KeyMapping mapping,
		List<S2CServerHelloPayload.ShortcutInfo> shortcuts,
		int index
	) {
		if (mapping.consumeClick() && index < shortcuts.size()) {
			triggerShortcut(shortcuts.get(index).id());
		}
	}

	private void triggerShortcut(Identifier shortcutId) {
		if (ClientPlayNetworking.canSend(C2SShortcutTriggerPayload.TYPE)) {
			ClientPlayNetworking.send(new C2SShortcutTriggerPayload(shortcutId));
		}
	}

	private void trySendClientHello() {
		if (sessionState.helloSentThisConnection()) {
			return;
		}
		if (ClientPlayNetworking.canSend(C2SClientHelloPayload.TYPE)) {
			sessionState.markHelloSent();
			String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("1.0.0");
			ClientPlayNetworking.send(new C2SClientHelloPayload(Ogui.PROTOCOL_VERSION, version));
		}
	}

	public static void playFeedbackSound(
		Minecraft mc,
		OguiClientPreferences preferences,
		S2CFeedbackPayload.FeedbackSound sound
	) {
		if (!preferences.uiSoundsEnabled()) {
			return;
		}
		float vol = preferences.soundVolume();
		switch (sound) {
			case CLICK -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, vol));
			case SUCCESS -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25F, vol));
			case ERROR -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, vol));
			case NONE -> {
			}
		}
	}
}
