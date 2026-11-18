package me.ogsammaenr.ogui.api;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compact, interior-culled 3D block shell representation transferred to {@code ogui-client}
 * for interactive rotating structure previews without exposing full server blueprint files.
 */
public record OguiStructureShell(
	int sizeX,
	int sizeY,
	int sizeZ,
	List<String> palette,
	List<BlockEntry> blocks
) {
	public static final int MAX_DIMENSION = 128;
	public static final int MAX_PALETTE_SIZE = 4096;
	public static final int MAX_BLOCKS = 65536;
	public static final int DEFAULT_MAX_SHELL_BLOCKS = 24000;
	private static final int BINARY_MAGIC = 0x4F475348; // "OGSH"
	private static final int BINARY_VERSION = 1;

	public record PositionedBlock(int x, int y, int z, BlockState state) {
		public PositionedBlock {
			Objects.requireNonNull(state, "state");
		}
	}

	public record BlockEntry(int x, int y, int z, int paletteIndex) {
	}

	public OguiStructureShell {
		if (sizeX < 1 || sizeX > MAX_DIMENSION || sizeY < 1 || sizeY > MAX_DIMENSION || sizeZ < 1 || sizeZ > MAX_DIMENSION) {
			throw new IllegalArgumentException(
				"Shell dimensions (" + sizeX + "x" + sizeY + "x" + sizeZ + ") out of bounds (1.." + MAX_DIMENSION + ")"
			);
		}
		palette = palette == null ? List.of() : List.copyOf(palette);
		if (palette.isEmpty() || palette.size() > MAX_PALETTE_SIZE) {
			throw new IllegalArgumentException("Shell palette size " + palette.size() + " out of bounds (1.." + MAX_PALETTE_SIZE + ")");
		}
		blocks = blocks == null ? List.of() : List.copyOf(blocks);
		if (blocks.size() > MAX_BLOCKS) {
			throw new IllegalArgumentException("Shell block count " + blocks.size() + " exceeds limit " + MAX_BLOCKS);
		}
		for (BlockEntry entry : blocks) {
			if (entry.x() < 0 || entry.x() >= sizeX
				|| entry.y() < 0 || entry.y() >= sizeY
				|| entry.z() < 0 || entry.z() >= sizeZ
				|| entry.paletteIndex() < 0 || entry.paletteIndex() >= palette.size()) {
				throw new IllegalArgumentException("Invalid BlockEntry in shell: " + entry);
			}
		}
	}

	/**
	 * Builds a compact exterior shell by removing occluded interior blocks and bounding oversized structures.
	 */
	public static OguiStructureShell fromBlocks(Collection<PositionedBlock> rawBlocks) {
		return fromBlocks(rawBlocks, DEFAULT_MAX_SHELL_BLOCKS);
	}

	/**
	 * Builds a compact exterior shell from arbitrary coordinates, performing interior-face occlusion culling
	 * and deterministic spatial downsampling if dimensions exceed {@value #MAX_DIMENSION} or block count exceeds {@code maxOutputBlocks}.
	 */
	public static OguiStructureShell fromBlocks(Collection<PositionedBlock> rawBlocks, int maxOutputBlocks) {
		Objects.requireNonNull(rawBlocks, "rawBlocks");
		int clampedMaxBlocks = Math.clamp(maxOutputBlocks, 1, MAX_BLOCKS);

		List<PositionedBlock> nonAir = new ArrayList<>(rawBlocks.size());
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (PositionedBlock pb : rawBlocks) {
			if (pb == null || pb.state().isAir()) {
				continue;
			}
			nonAir.add(pb);
			minX = Math.min(minX, pb.x());
			minY = Math.min(minY, pb.y());
			minZ = Math.min(minZ, pb.z());
			maxX = Math.max(maxX, pb.x());
			maxY = Math.max(maxY, pb.y());
			maxZ = Math.max(maxZ, pb.z());
		}

		if (nonAir.isEmpty()) {
			return new OguiStructureShell(
				1, 1, 1,
				List.of(BlockStateParser.serialize(Blocks.GRASS_BLOCK.defaultBlockState())),
				List.of()
			);
		}

		int rawSpanX = maxX - minX + 1;
		int rawSpanY = maxY - minY + 1;
		int rawSpanZ = maxZ - minZ + 1;
		int maxRawSpan = Math.max(rawSpanX, Math.max(rawSpanY, rawSpanZ));
		int scaleStep = Math.max(1, (maxRawSpan + MAX_DIMENSION - 1) / MAX_DIMENSION);

		int sizeX = Math.max(1, (rawSpanX + scaleStep - 1) / scaleStep);
		int sizeY = Math.max(1, (rawSpanY + scaleStep - 1) / scaleStep);
		int sizeZ = Math.max(1, (rawSpanZ + scaleStep - 1) / scaleStep);

		Map<Long, PositionedBlock> normalized = new LinkedHashMap<>();
		for (PositionedBlock pb : nonAir) {
			int nx = (pb.x() - minX) / scaleStep;
			int ny = (pb.y() - minY) / scaleStep;
			int nz = (pb.z() - minZ) / scaleStep;
			long key = packKey(nx, ny, nz);
			if (scaleStep == 1 || !normalized.containsKey(key) || isMoreDetailedState(pb.state(), normalized.get(key).state())) {
				normalized.put(key, new PositionedBlock(nx, ny, nz, pb.state()));
			}
		}

		// Strip fully surrounded interior opaque blocks while keeping all transparent/non-cube blocks and their neighbors.
		List<PositionedBlock> shellBlocks = new ArrayList<>(normalized.size());
		for (PositionedBlock pb : normalized.values()) {
			if (!isFullySurrounded(pb, normalized)) {
				shellBlocks.add(pb);
			}
		}

		if (shellBlocks.size() > clampedMaxBlocks) {
			shellBlocks.sort(Comparator.comparingInt(PositionedBlock::y)
				.thenComparingInt(PositionedBlock::z)
				.thenComparingInt(PositionedBlock::x));
			List<PositionedBlock> thinned = new ArrayList<>(clampedMaxBlocks);
			double stride = (double) shellBlocks.size() / clampedMaxBlocks;
			for (int i = 0; i < clampedMaxBlocks; i++) {
				int idx = Math.min(shellBlocks.size() - 1, (int) Math.floor(i * stride));
				thinned.add(shellBlocks.get(idx));
			}
			shellBlocks = thinned;
		} else {
			shellBlocks.sort(Comparator.comparingInt(PositionedBlock::y)
				.thenComparingInt(PositionedBlock::z)
				.thenComparingInt(PositionedBlock::x));
		}

		Map<String, Integer> paletteMap = new LinkedHashMap<>();
		List<String> palette = new ArrayList<>();
		List<BlockEntry> entries = new ArrayList<>(shellBlocks.size());

		for (PositionedBlock pb : shellBlocks) {
			String serialized = BlockStateParser.serialize(pb.state());
			Integer idx = paletteMap.get(serialized);
			if (idx == null) {
				if (palette.size() >= MAX_PALETTE_SIZE) {
					continue;
				}
				idx = palette.size();
				paletteMap.put(serialized, idx);
				palette.add(serialized);
			}
			entries.add(new BlockEntry(pb.x(), pb.y(), pb.z(), idx));
		}

		if (palette.isEmpty()) {
			palette.add(BlockStateParser.serialize(Blocks.STONE.defaultBlockState()));
		}

		return new OguiStructureShell(sizeX, sizeY, sizeZ, palette, entries);
	}

	private static boolean isMoreDetailedState(BlockState candidate, BlockState existing) {
		return !candidate.canOcclude() && existing.canOcclude();
	}

	private static boolean isFullySurrounded(PositionedBlock pb, Map<Long, PositionedBlock> grid) {
		return isFullOccluder(grid.get(packKey(pb.x() + 1, pb.y(), pb.z())))
			&& isFullOccluder(grid.get(packKey(pb.x() - 1, pb.y(), pb.z())))
			&& isFullOccluder(grid.get(packKey(pb.x(), pb.y() + 1, pb.z())))
			&& isFullOccluder(grid.get(packKey(pb.x(), pb.y() - 1, pb.z())))
			&& isFullOccluder(grid.get(packKey(pb.x(), pb.y(), pb.z() + 1)))
			&& isFullOccluder(grid.get(packKey(pb.x(), pb.y(), pb.z() - 1)));
	}

	private static boolean isFullOccluder(PositionedBlock neighbor) {
		if (neighbor == null) {
			return false;
		}
		BlockState state = neighbor.state();
		return !state.isAir() && state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape());
	}

	private static long packKey(int x, int y, int z) {
		return BlockPos.asLong(x, y, z);
	}

	public byte[] toCompressedBytes() {
		try {
			ByteArrayOutputStream raw = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(raw);
				DataOutputStream out = new DataOutputStream(gzip)) {
				out.writeInt(BINARY_MAGIC);
				out.writeByte(BINARY_VERSION);
				out.writeShort(sizeX);
				out.writeShort(sizeY);
				out.writeShort(sizeZ);
				out.writeInt(palette.size());
				for (String spec : palette) {
					out.writeUTF(spec);
				}
				out.writeInt(blocks.size());
				for (BlockEntry entry : blocks) {
					out.writeByte(entry.x());
					out.writeByte(entry.y());
					out.writeByte(entry.z());
					out.writeShort(entry.paletteIndex());
				}
			}
			return raw.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to serialize OguiStructureShell", e);
		}
	}

	public static OguiStructureShell fromCompressedBytes(byte[] compressed) throws IOException {
		Objects.requireNonNull(compressed, "compressed");
		if (compressed.length == 0 || compressed.length > OguiAssetRef.MAX_ASSET_BYTES) {
			throw new IOException("Compressed shell size out of bounds: " + compressed.length);
		}
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
			DataInputStream in = new DataInputStream(gzip)) {
			int magic = in.readInt();
			if (magic != BINARY_MAGIC) {
				throw new IOException("Invalid OguiStructureShell magic header");
			}
			int version = in.readUnsignedByte();
			if (version != BINARY_VERSION) {
				throw new IOException("Unsupported OguiStructureShell version: " + version);
			}
			int sx = in.readUnsignedShort();
			int sy = in.readUnsignedShort();
			int sz = in.readUnsignedShort();
			if (sx < 1 || sx > MAX_DIMENSION || sy < 1 || sy > MAX_DIMENSION || sz < 1 || sz > MAX_DIMENSION) {
				throw new IOException("Shell dimensions out of bounds: " + sx + "x" + sy + "x" + sz);
			}
			int paletteCount = in.readInt();
			if (paletteCount < 1 || paletteCount > MAX_PALETTE_SIZE) {
				throw new IOException("Shell palette count out of bounds: " + paletteCount);
			}
			List<String> palette = new ArrayList<>(paletteCount);
			for (int i = 0; i < paletteCount; i++) {
				palette.add(in.readUTF());
			}
			int blockCount = in.readInt();
			if (blockCount < 0 || blockCount > MAX_BLOCKS) {
				throw new IOException("Shell block count out of bounds: " + blockCount);
			}
			List<BlockEntry> entries = new ArrayList<>(blockCount);
			for (int i = 0; i < blockCount; i++) {
				int x = in.readUnsignedByte();
				int y = in.readUnsignedByte();
				int z = in.readUnsignedByte();
				int paletteIndex = in.readUnsignedShort();
				if (x >= sx || y >= sy || z >= sz || paletteIndex >= paletteCount) {
					throw new IOException("Invalid shell block coordinate/index at " + i);
				}
				entries.add(new BlockEntry(x, y, z, paletteIndex));
			}
			return new OguiStructureShell(sx, sy, sz, palette, entries);
		}
	}

	public static BlockState decodeBlockState(HolderLookup<Block> blocks, String spec) {
		if (spec == null || spec.isBlank()) {
			return Blocks.AIR.defaultBlockState();
		}
		try {
			return BlockStateParser.parseForBlock(blocks, spec, false).blockState();
		} catch (Exception ignored) {
			return Blocks.STONE.defaultBlockState();
		}
	}
}
