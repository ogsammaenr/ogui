package me.ogsammaenr.ogui;

import me.ogsammaenr.ogui.api.LayoutArchetype;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiIcon;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.api.OguiText;
import me.ogsammaenr.ogui.api.OguiTheme;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;
import me.ogsammaenr.ogui.runtime.OguiAssetRegistry;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiAssetAndShellTest {
	@BeforeAll
	static void bootstrap() {
		OguiTestBootstrap.ensureBootstrapped();
	}

	@Test
	void structureShellCullsOpaqueInteriorAndPreservesNeighborsOfTransparentBlocks() throws IOException {
		List<OguiStructureShell.PositionedBlock> cube = new ArrayList<>();
		for (int x = 10; x < 15; x++) {
			for (int y = 20; y < 25; y++) {
				for (int z = -5; z < 0; z++) {
					cube.add(new OguiStructureShell.PositionedBlock(x, y, z, Blocks.STONE.defaultBlockState()));
				}
			}
		}
		OguiStructureShell culled = OguiStructureShell.fromBlocks(cube);
		assertEquals(5, culled.sizeX());
		assertEquals(5, culled.sizeY());
		assertEquals(5, culled.sizeZ());
		// 5x5x5 = 125, minus occluded 3x3x3 interior (27) = 98 surface blocks
		assertEquals(98, culled.blocks().size());

		// Placing glass on one side must keep the previously occluded adjacent block visible
		List<OguiStructureShell.PositionedBlock> withWindow = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				for (int z = 0; z < 3; z++) {
					boolean frontCenter = (x == 1 && y == 1 && z == 0);
					var state = frontCenter ? Blocks.GLASS.defaultBlockState() : Blocks.STONE.defaultBlockState();
					withWindow.add(new OguiStructureShell.PositionedBlock(x, y, z, state));
				}
			}
		}
		OguiStructureShell windowShell = OguiStructureShell.fromBlocks(withWindow);
		// Center block (1,1,1) touches glass at (1,1,0), so ALL 27 blocks remain visible
		assertEquals(27, windowShell.blocks().size());

		// Compressed GZIP round-trip
		byte[] compressed = culled.toCompressedBytes();
		OguiStructureShell decoded = OguiStructureShell.fromCompressedBytes(compressed);
		assertEquals(culled, decoded);

		assertThrows(IOException.class, () -> OguiStructureShell.fromCompressedBytes(new byte[]{1, 2, 3, 4}));
	}

	@Test
	void structureShellBoundsLargeSpansAndBlockCountCap() {
		List<OguiStructureShell.PositionedBlock> wide = new ArrayList<>();
		for (int x = 0; x < 160; x++) {
			wide.add(new OguiStructureShell.PositionedBlock(x, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));
		}
		OguiStructureShell downsampled = OguiStructureShell.fromBlocks(wide, 40);
		assertTrue(downsampled.sizeX() <= OguiStructureShell.MAX_DIMENSION);
		assertTrue(downsampled.blocks().size() <= 40);
	}

	@Test
	void assetRegistryValidatesPngMagicDeduplicatesAndTracksPerPlayerAccess() {
		OguiAssetRegistry registry = new OguiAssetRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.registerPng(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}));
		assertThrows(IllegalArgumentException.class, () -> registry.registerPng(new byte[0]));

		byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
		OguiAssetRef ref1 = registry.registerPng(fakePng);
		OguiAssetRef ref2 = registry.registerPng(fakePng.clone());
		assertEquals(ref1, ref2);
		assertEquals(OguiAssetKind.PNG, ref1.kind());
		assertArrayEquals(fakePng, registry.find(ref1.sha256()).orElseThrow().bytes());

		OguiAssetRef structRef = registry.registerStructure(OguiStructureShell.fromBlocks(List.of(
			new OguiStructureShell.PositionedBlock(0, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState())
		)));
		assertEquals(OguiAssetKind.STRUCTURE_SHELL, structRef.kind());

		UUID player = UUID.randomUUID();
		assertFalse(registry.isAllowedForPlayer(player, ref1.sha256()));

		ScreenSnapshotDto snap = new ScreenSnapshotDto(
			Ogui.id("gallery"), null, OguiText.nonItalic("Gallery"),
			LayoutArchetype.PAGED_GRID, OguiTheme.DEFAULT, List.of(), null, 0, 1, 1, false,
			List.of(),
			List.of(new OguiWidgetDto.ButtonDto(
				"card1", OguiIcon.item("minecraft:grass_block"), OguiText.nonItalic("Classic"),
				List.of(), null, true, null, ref1
			)),
			List.of(),
			new OguiWidgetDto.StructurePreviewDto(
				"view", OguiIcon.item("minecraft:grass_block"), OguiText.nonItalic("3D"),
				List.of(), structRef, 45.0F, 25.0F, false, true
			),
			List.of(), List.of(), List.of(), 6, Map.of(), null, null
		);
		registry.allowAssetsFromSnapshot(player, snap);
		assertTrue(registry.isAllowedForPlayer(player, ref1.sha256()));
		assertTrue(registry.isAllowedForPlayer(player, structRef.sha256()));

		registry.evictPlayer(player);
		assertFalse(registry.isAllowedForPlayer(player, ref1.sha256()));
	}
}
