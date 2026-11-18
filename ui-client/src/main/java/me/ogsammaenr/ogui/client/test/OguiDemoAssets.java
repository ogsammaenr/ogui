package me.ogsammaenr.ogui.client.test;

import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiService;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.client.assets.OguiClientAssetManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Generates lightweight procedural PNG thumbnails and 3D {@link OguiStructureShell} islands
 * for {@code /oguitest} showcase and ClientGameTest verification without relying on external assets or AWT.
 */
final class OguiDemoAssets {
	private OguiDemoAssets() {
	}

	static byte[] createIslandThumbnailPng(int topRgb, int bottomRgb, int landRgb, int accentRgb) {
		int width = 96;
		int height = 54;
		byte[] rawScanlines = new byte[height * (1 + width * 4)];
		int ptr = 0;
		for (int y = 0; y < height; y++) {
			rawScanlines[ptr++] = 0; // PNG filter type None
			float t = (float) y / (height - 1);
			int skyR = lerp((topRgb >>> 16) & 0xFF, (bottomRgb >>> 16) & 0xFF, t);
			int skyG = lerp((topRgb >>> 8) & 0xFF, (bottomRgb >>> 8) & 0xFF, t);
			int skyB = lerp(topRgb & 0xFF, bottomRgb & 0xFF, t);

			for (int x = 0; x < width; x++) {
				int r = skyR;
				int g = skyG;
				int b = skyB;

				// Stylized floating island silhouette in the middle
				int dx = x - (width / 2);
				int dy = y - (height / 2 + 4);
				if (Math.abs(dx) <= 20 && dy >= -2 && dy <= 10 - Math.abs(dx) / 3) {
					r = (landRgb >>> 16) & 0xFF;
					g = (landRgb >>> 8) & 0xFF;
					b = landRgb & 0xFF;
					if (dy <= 1) {
						r = (accentRgb >>> 16) & 0xFF;
						g = (accentRgb >>> 8) & 0xFF;
						b = accentRgb & 0xFF;
					}
				} else if (dx >= -6 && dx <= -4 && dy >= -14 && dy < -2) {
					// Small tree trunk
					r = 122;
					g = 86;
					b = 50;
				} else if (dx >= -11 && dx <= 1 && dy >= -21 && dy <= -12) {
					// Canopy / crystal highlight
					r = (accentRgb >>> 16) & 0xFF;
					g = Math.min(255, ((accentRgb >>> 8) & 0xFF) + 25);
					b = accentRgb & 0xFF;
				}

				rawScanlines[ptr++] = (byte) r;
				rawScanlines[ptr++] = (byte) g;
				rawScanlines[ptr++] = (byte) b;
				rawScanlines[ptr++] = (byte) 255;
			}
		}

		try {
			ByteArrayOutputStream idatCompressed = new ByteArrayOutputStream();
			try (DeflaterOutputStream def = new DeflaterOutputStream(idatCompressed)) {
				def.write(rawScanlines);
			}

			ByteArrayOutputStream png = new ByteArrayOutputStream();
			png.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
			ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
			try (DataOutputStream dos = new DataOutputStream(ihdr)) {
				dos.writeInt(width);
				dos.writeInt(height);
				dos.writeByte(8); // bit depth
				dos.writeByte(6); // RGBA
				dos.writeByte(0); // compression
				dos.writeByte(0); // filter
				dos.writeByte(0); // interlace
			}
			writeChunk(png, "IHDR", ihdr.toByteArray());
			writeChunk(png, "IDAT", idatCompressed.toByteArray());
			writeChunk(png, "IEND", new byte[0]);
			return png.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to generate demo PNG", e);
		}
	}

	static OguiStructureShell createClassicSkyblockShell(boolean netherVariant) {
		List<OguiStructureShell.PositionedBlock> blocks = new ArrayList<>();
		BlockState top = netherVariant ? Blocks.CRIMSON_NYLIUM.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState body = netherVariant ? Blocks.NETHERRACK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		BlockState core = netherVariant ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
		BlockState trunk = netherVariant ? Blocks.CRIMSON_STEM.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
		BlockState leaves = netherVariant ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : Blocks.OAK_LEAVES.defaultBlockState();

		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean cornerCut = (x == 0 || x == 6) && (z == 0 || z == 6);
				if (cornerCut) {
					continue;
				}
				blocks.add(new OguiStructureShell.PositionedBlock(x, 3, z, top));
				blocks.add(new OguiStructureShell.PositionedBlock(x, 2, z, body));
				if (x >= 1 && x <= 5 && z >= 1 && z <= 5) {
					blocks.add(new OguiStructureShell.PositionedBlock(x, 1, z, core));
				}
				if (x >= 2 && x <= 4 && z >= 2 && z <= 4) {
					blocks.add(new OguiStructureShell.PositionedBlock(x, 0, z, core));
				}
			}
		}

		// Water or lava pool
		blocks.add(new OguiStructureShell.PositionedBlock(1, 3, 2,
			netherVariant ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState()));

		// Chest on platform front-right corner so lock faces viewer
		blocks.add(new OguiStructureShell.PositionedBlock(4, 4, 1, Blocks.CHEST.defaultBlockState()));

		// Tree at corner (2, 4..8, 4)
		for (int y = 4; y <= 7; y++) {
			blocks.add(new OguiStructureShell.PositionedBlock(2, y, 4, trunk));
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 6; dy <= 8; dy++) {
					if (dy == 8 && (Math.abs(dx) + Math.abs(dz) > 1)) {
						continue;
					}
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
						continue;
					}
					if (dx == 0 && dz == 0 && dy <= 7) {
						continue;
					}
					blocks.add(new OguiStructureShell.PositionedBlock(2 + dx, dy, 4 + dz, leaves));
				}
			}
		}
		return OguiStructureShell.fromBlocks(blocks);
	}

	static OguiAssetRef registerDemoPng(
		@org.jspecify.annotations.Nullable OguiService service,
		byte[] bytes
	) {
		if (service != null) {
			return service.registerPngAsset(bytes);
		}
		OguiAssetRef ref = new OguiAssetRef(
			me.ogsammaenr.ogui.runtime.OguiAssetRegistry.sha256Hex(bytes),
			me.ogsammaenr.ogui.api.OguiAssetKind.PNG,
			bytes.length
		);
		OguiClientAssetManager.shared().seedLocalAsset(ref, bytes);
		return ref;
	}

	static OguiAssetRef registerDemoShell(
		@org.jspecify.annotations.Nullable OguiService service,
		OguiStructureShell shell
	) {
		if (service != null) {
			return service.registerStructureAsset(shell);
		}
		byte[] bytes = shell.toCompressedBytes();
		OguiAssetRef ref = new OguiAssetRef(
			me.ogsammaenr.ogui.runtime.OguiAssetRegistry.sha256Hex(bytes),
			me.ogsammaenr.ogui.api.OguiAssetKind.STRUCTURE_SHELL,
			bytes.length
		);
		OguiClientAssetManager.shared().seedLocalAsset(ref, bytes);
		return ref;
	}

	private static int lerp(int a, int b, float t) {
		return Math.clamp(Math.round(a + (b - a) * t), 0, 255);
	}

	private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		DataOutputStream dos = new DataOutputStream(out);
		dos.writeInt(data.length);
		dos.write(typeBytes);
		dos.write(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		dos.writeInt((int) crc.getValue());
	}
}
