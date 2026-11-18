package me.ogsammaenr.ogui.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiScreenModel;
import me.ogsammaenr.ogui.api.OguiShortcut;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiWidget;
import me.ogsammaenr.ogui.client.OguiClientPreferences;
import me.ogsammaenr.ogui.client.OguiLayoutEngine;
import me.ogsammaenr.ogui.client.OguiPreferencesScreen;
import me.ogsammaenr.ogui.client.OguiQuickLauncherScreen;
import me.ogsammaenr.ogui.client.OguiScreen;
import me.ogsammaenr.ogui.client.assets.OguiClientAssetManager;
import me.ogsammaenr.ogui.client.render.OguiStructureMesh;
import me.ogsammaenr.ogui.client.test.OguiDemoScreens;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real Minecraft 26.3 client GameTests verifying renderer initialization, keybindings,
 * local preferences modal, quick-menu launcher, and end-to-end server-driven {@link OguiScreen}.
 */
public final class OguiClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTick();
		verifyDefaultKeyMappings(context);
		verifyPreferencesScreen(context);
		verifyIntegratedServerClientFlow(context);
		context.waitForScreen(TitleScreen.class);
	}

	private static void verifyDefaultKeyMappings(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			KeyMapping quickMenu = findKeyMapping(mc.options.keyMappings, "key.ogui.quick_menu");
			if (quickMenu == null) {
				throw new AssertionError("Missing key.ogui.quick_menu KeyMapping");
			}
			String boundName = quickMenu.getDefaultKey().getName();
			String expectedGName = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_G).getName();
			if (!expectedGName.equals(boundName)) {
				throw new AssertionError("Expected quick_menu default key " + expectedGName + " but got " + boundName);
			}
		});
	}

	private static void verifyPreferencesScreen(ClientGameTestContext context) {
		OguiClientPreferences prefs = new OguiClientPreferences();
		OguiClientPreferences.ThemePreset initialTheme = prefs.themePreset();

		context.setScreen(() -> new OguiPreferencesScreen(new TitleScreen(), prefs));
		context.waitForScreen(OguiPreferencesScreen.class);
		context.waitTicks(2);

		context.runOnClient(mc -> {
			Screen screen = mc.gui.screen();
			if (!(screen instanceof OguiPreferencesScreen)) {
				throw new AssertionError("Expected OguiPreferencesScreen, got " + screen);
			}
			Button themeBtn = screen.children().stream()
				.filter(Button.class::isInstance)
				.map(Button.class::cast)
				.filter(b -> b.getMessage().getString().contains(initialTheme.displayName()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Theme toggle button not found on OguiPreferencesScreen"));
			themeBtn.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));
		});
		context.waitTick();

		if (prefs.themePreset() == initialTheme) {
			throw new AssertionError("Clicking Theme button did not advance ThemePreset");
		}

		context.clickScreenButton("ogui.pref.done");
		context.waitForScreen(TitleScreen.class);
	}

	private static void verifyIntegratedServerClientFlow(ClientGameTestContext context) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Identifier screenId = Identifier.fromNamespaceAndPath("ogui_client_test", "detail_" + suffix);
		Identifier shortcutPrimary = Identifier.fromNamespaceAndPath("ogui_client_test", "open_primary_" + suffix);
		Identifier shortcutSecondary = Identifier.fromNamespaceAndPath("ogui_client_test", "open_secondary_" + suffix);
		String primaryLabel = "Open Detail Test " + suffix;
		String secondaryLabel = "Secondary Test " + suffix;

		AtomicReference<String> submittedInput = new AtomicReference<>("");
		AtomicBoolean actionClicked = new AtomicBoolean(false);

		Ogui.service().registerScreen(screenId, ctx -> OguiScreenModel.detailForm("Client GameTest Form")
			.heroCard(OguiWidget.button("hero", OguiIcon.item("minecraft:diamond"), OguiText.nonItalic("Test Item", ChatFormatting.AQUA)).build())
			.addControl(OguiWidget.textInput("search_box", OguiIcon.item("minecraft:name_tag"), OguiText.nonItalic("Query"))
				.placeholder("Type here...")
				.value(ctx.stateOrDefault("query", ""))
				.liveDebounce(true)
				.onSubmit((click, text) -> {
					submittedInput.set(text);
					click.sendFeedback(true, "Query=" + text);
				})
				.build())
			.addAction(OguiWidget.button("confirm_btn", OguiIcon.item("minecraft:emerald"), OguiText.nonItalic("Apply", ChatFormatting.GREEN))
				.onClick(click -> actionClicked.set(true))
				.build())
			.build());

		Ogui.service().registerShortcut(new OguiShortcut(
			shortcutPrimary,
			Component.literal(primaryLabel),
			Component.literal("Primary client gametest shortcut"),
			player -> Ogui.service().openScreen(player, screenId)
		));
		Ogui.service().registerShortcut(new OguiShortcut(
			shortcutSecondary,
			Component.literal(secondaryLabel),
			Component.literal("Secondary client gametest shortcut"),
			player -> {}
		));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(mc -> mc.player != null && mc.gui.screen() == null);
			singleplayer.getServer().waitFor(server -> {
				ServerPlayer sp = singleplayer.getConnection().getServerPlayer();
				return sp != null && Ogui.service().isClientRendererAvailable(sp);
			});
			singleplayer.getConnection().waitForClientboundPackets();
			context.waitTicks(2);

			// Press G (key.ogui.quick_menu) -> opens OguiQuickLauncherScreen because >= 2 shortcuts exist
			context.getInput().pressKey(options -> findKeyMapping(options.keyMappings, "key.ogui.quick_menu"));
			context.waitForScreen(OguiQuickLauncherScreen.class);
			context.waitTicks(2);

			// Click shortcut button -> server opens OguiScreen on client
			context.clickScreenButton(primaryLabel);
			context.waitForScreen(OguiScreen.class);
			context.waitTicks(2);

			// Focus EditBox and type text -> verifies debounced CLIENT_INPUT reaches server TextInputWidget
			context.runOnClient(mc -> {
				Screen screen = mc.gui.screen();
				EditBox editBox = screen.children().stream()
					.filter(EditBox.class::isInstance)
					.map(EditBox.class::cast)
					.findFirst()
					.orElseThrow(() -> new AssertionError("Expected EditBox on OguiScreen"));
				editBox.setFocused(true);
				screen.setFocused(editBox);
			});
			context.getInput().typeChars("emerald");
			context.waitTicks(10);
			singleplayer.getConnection().waitForServerboundPackets();
			singleplayer.getConnection().waitForClientboundPackets();

			if (!"emerald".equals(submittedInput.get())) {
				throw new AssertionError("Expected server submittedInput='emerald', got '" + submittedInput.get() + "'");
			}

			// Server-initiated closePlayerScreen sends S2CCloseScreenPayload cleanly
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer sp = singleplayer.getConnection().getServerPlayer();
				Ogui.service().closePlayerScreen(sp);
			});
			context.waitFor(mc -> mc.gui.screen() == null);

			// Client /oguitest commands work end-to-end (preview + launcher + live server session)
			context.runOnClient(mc -> mc.player.connection.sendCommand("oguitest status"));
			context.waitTick();

			context.runOnClient(mc -> mc.player.connection.sendCommand("oguitest open tabbed"));
			context.waitForScreen(OguiScreen.class);
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null);

			context.runOnClient(mc -> mc.player.connection.sendCommand("oguitest launcher"));
			context.waitForScreen(OguiQuickLauncherScreen.class);
			context.waitTick();
			context.clickScreenButton("ogui.screen.close");
			context.waitFor(mc -> mc.gui.screen() == null);

			context.runOnClient(mc -> mc.player.connection.sendCommand("oguitest live fixed"));
			context.waitForScreen(OguiScreen.class);
			context.waitTicks(2);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer sp = singleplayer.getConnection().getServerPlayer();
				Ogui.service().closePlayerScreen(sp);
			});
			context.waitFor(mc -> mc.gui.screen() == null);

			// Live PNG Gallery -> Click Card -> Rotatable 3D Structure Preview end-to-end with GPU PiP + chunk transfer
			verifyGalleryTo3dPreviewFlow(context, singleplayer);
		}
	}

	private static void verifyGalleryTo3dPreviewFlow(
		ClientGameTestContext context,
		TestSingleplayerContext singleplayer
	) {
		context.runOnClient(mc -> mc.player.connection.sendCommand("oguitest live gallery"));
		context.waitForScreen(OguiScreen.class);

		AtomicReference<OguiAssetRef> firstPngRef = new AtomicReference<>();

		// Wait until at least one gallery PNG thumbnail arrives over chunked C2S/S2C and uploads as DynamicTexture
		context.waitFor(mc -> {
			for (OguiWidgetDto entry : currentEntriesSnapshot(mc)) {
				if (entry instanceof OguiWidgetDto.ButtonDto b && b.previewImage() != null) {
					firstPngRef.set(b.previewImage());
					var slot = OguiClientAssetManager.shared().resolveOrRequest(b.previewImage());
					if (slot.status() != OguiClientAssetManager.Status.READY || slot.png() == null) {
						return false;
					}
				}
			}
			return firstPngRef.get() != null;
		});
		context.waitTicks(2);
		Path galleryShot = context.takeScreenshot("ogui_gallery_screen");
		verifyRenderedImageHasVariety(galleryShot, "PNG Gallery screenshot");

		// Click the first gallery card -> transitions server-side to 3D StructurePreview screen
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof OguiScreen screen)) {
				throw new AssertionError("Expected gallery OguiScreen");
			}
			var layout = OguiLayoutEngine.compute(currentSnapshot(mc), screen.width, screen.height, OguiClientPreferences.UiScaleMode.AUTO);
			var firstCard = layout.widgets().stream()
				.filter(w -> w.region() == OguiLayoutEngine.Region.ENTRY)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing gallery card widget"));
			double cx = firstCard.bounds().x() + firstCard.bounds().width() * 0.5;
			double cy = firstCard.bounds().y() + firstCard.bounds().height() * 0.5;
			screen.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
		});

		singleplayer.getConnection().waitForServerboundPackets();
		singleplayer.getConnection().waitForClientboundPackets();
		context.waitFor(mc -> {
			if (!(mc.gui.screen() instanceof OguiScreen)) {
				return false;
			}
			ScreenSnapshotDto snap = currentSnapshot(mc);
			return snap != null && snap.heroCard() instanceof OguiWidgetDto.StructurePreviewDto;
		});

		// Wait for 3D structure shell chunk transfer + GZIP decode + static/fluid/chest mesh bake
		AtomicReference<OguiAssetRef> firstStructRef = new AtomicReference<>();
		context.waitFor(mc -> {
			ScreenSnapshotDto snap = currentSnapshot(mc);
			if (snap == null || !(snap.heroCard() instanceof OguiWidgetDto.StructurePreviewDto sp) || sp.structureAsset() == null) {
				return false;
			}
			firstStructRef.set(sp.structureAsset());
			var slot = OguiClientAssetManager.shared().resolveOrRequest(sp.structureAsset());
			if (slot.status() != OguiClientAssetManager.Status.READY) {
				return false;
			}
			OguiStructureMesh mesh = slot.ensureMesh(mc);
			return mesh != null && !mesh.isEmpty();
		});

		// Render multiple frames with interactive orbit drag and wheel zoom in PiP renderer
		context.waitTicks(3);
		Path preview3dInitial = context.takeScreenshot("ogui_3d_overworld");
		verifyRenderedImageHasVariety(preview3dInitial, "3D Overworld screenshot");
		context.runOnClient(mc -> {
			OguiScreen screen = (OguiScreen) mc.gui.screen();
			ScreenSnapshotDto snap = currentSnapshot(mc);
			var layout = OguiLayoutEngine.compute(snap, screen.width, screen.height, OguiClientPreferences.UiScaleMode.AUTO);
			var hero = layout.widgets().stream()
				.filter(w -> w.region() == OguiLayoutEngine.Region.HERO)
				.findFirst()
				.orElseThrow();
			double cx = hero.bounds().x() + hero.bounds().width() * 0.5;
			double cy = hero.bounds().y() + hero.bounds().height() * 0.5;
			screen.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
			screen.mouseDragged(new MouseButtonEvent(cx + 28, cy - 12, new MouseButtonInfo(0, 0)), 28.0, -12.0);
			screen.mouseReleased(new MouseButtonEvent(cx + 28, cy - 12, new MouseButtonInfo(0, 0)));
			screen.mouseScrolled(cx, cy, 0.0, 1.0);
		});
		context.waitTicks(3);

		// Cycle preview dimension Overworld -> Nether and verify new structure shell renders cleanly
		context.runOnClient(mc -> {
			OguiScreen screen = (OguiScreen) mc.gui.screen();
			ScreenSnapshotDto snap = currentSnapshot(mc);
			var layout = OguiLayoutEngine.compute(snap, screen.width, screen.height, OguiClientPreferences.UiScaleMode.AUTO);
			var dimCycle = layout.widgets().stream()
				.filter(w -> "preview_dim".equals(w.widget().widgetId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing preview_dim CycleWidget"));
			double cx = dimCycle.bounds().x() + dimCycle.bounds().width() * 0.5;
			double cy = dimCycle.bounds().y() + dimCycle.bounds().height() * 0.5;
			screen.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
		});
		singleplayer.getConnection().waitForServerboundPackets();
		singleplayer.getConnection().waitForClientboundPackets();
		context.waitFor(mc -> {
			ScreenSnapshotDto snap = currentSnapshot(mc);
			if (snap == null || !(snap.heroCard() instanceof OguiWidgetDto.StructurePreviewDto sp) || sp.structureAsset() == null) {
				return false;
			}
			if (sp.structureAsset().equals(firstStructRef.get())) {
				return false;
			}
			var slot = OguiClientAssetManager.shared().resolveOrRequest(sp.structureAsset());
			return slot.status() == OguiClientAssetManager.Status.READY && slot.ensureMesh(mc) != null;
		});
		context.waitTicks(2);
		Path preview3dNether = context.takeScreenshot("ogui_3d_nether_rotated");
		verifyRenderedImageHasVariety(preview3dNether, "3D Nether rotated screenshot");

		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer sp = singleplayer.getConnection().getServerPlayer();
			Ogui.service().closePlayerScreen(sp);
		});
		context.waitFor(mc -> mc.gui.screen() == null);
	}

	private static ScreenSnapshotDto currentSnapshot(net.minecraft.client.Minecraft mc) {
		var sp = mc.getSingleplayerServer() != null
			? mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())
			: null;
		if (sp == null) {
			return null;
		}
		var session = me.ogsammaenr.ogui.runtime.OguiSessionManager.instance().activeSession(sp.getUUID());
		return session != null ? session.toDto() : null;
	}

	private static List<OguiWidgetDto> currentEntriesSnapshot(net.minecraft.client.Minecraft mc) {
		ScreenSnapshotDto snap = currentSnapshot(mc);
		return snap != null ? snap.entries() : List.of();
	}

	private static void verifyRenderedImageHasVariety(Path pngPath, String label) {
		try (var in = Files.newInputStream(pngPath);
			var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
			Set<Integer> centerColors = new HashSet<>();
			int cx = img.getWidth() / 2;
			int cy = img.getHeight() / 2;
			for (int y = Math.max(0, cy - img.getHeight() / 4); y < Math.min(img.getHeight(), cy + img.getHeight() / 4); y += 2) {
				for (int x = Math.max(0, cx - img.getWidth() / 4); x < Math.min(img.getWidth(), cx + img.getWidth() / 4); x += 2) {
					centerColors.add(img.getPixel(x, y));
				}
			}
			if (centerColors.size() < 24) {
				throw new AssertionError(label + " rendered too few distinct colors in center: " + centerColors.size());
			}
		} catch (Exception e) {
			throw new AssertionError("Failed to inspect rendered screenshot " + pngPath + ": " + e.getMessage(), e);
		}
	}

	private static KeyMapping findKeyMapping(KeyMapping[] mappings, String translationKey) {
		return Arrays.stream(mappings)
			.filter(mapping -> translationKey.equals(mapping.getName()))
			.findFirst()
			.orElse(null);
	}
}
