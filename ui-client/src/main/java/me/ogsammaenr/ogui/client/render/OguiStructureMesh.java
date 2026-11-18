package me.ogsammaenr.ogui.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Baked static multi-layer block/fluid geometry and special block model list built once per {@link OguiStructureShell}.
 * Frame-to-frame rotation re-emits pre-tesselated vertices directly with zero chunk meshing or lookup overhead.
 */
public final class OguiStructureMesh {
	private static final int GRASS_FOLIAGE_TINT = 0xFF79C05A;
	private static final int DRY_FOLIAGE_TINT = 0xFFAEA473;
	private static final int WATER_TINT = 0xFF3F76E4;

	private static final class LayerBuilder {
		float[] positions = new float[256 * 3];
		float[] uvs = new float[256 * 2];
		float[] normals = new float[256 * 3];
		int[] colors = new int[256];
		int[] lights = new int[256];
		int[] overlays = new int[256];
		int count;

		void addVertex(
			float x, float y, float z,
			int color,
			float u, float v,
			int overlay, int light,
			float nx, float ny, float nz
		) {
			if (count >= colors.length) {
				int nextCap = colors.length * 2;
				positions = Arrays.copyOf(positions, nextCap * 3);
				uvs = Arrays.copyOf(uvs, nextCap * 2);
				normals = Arrays.copyOf(normals, nextCap * 3);
				colors = Arrays.copyOf(colors, nextCap);
				lights = Arrays.copyOf(lights, nextCap);
				overlays = Arrays.copyOf(overlays, nextCap);
			}
			int pIdx = count * 3;
			positions[pIdx] = x;
			positions[pIdx + 1] = y;
			positions[pIdx + 2] = z;
			int uIdx = count * 2;
			uvs[uIdx] = u;
			uvs[uIdx + 1] = v;
			normals[pIdx] = nx;
			normals[pIdx + 1] = ny;
			normals[pIdx + 2] = nz;
			colors[count] = color;
			lights[count] = light;
			overlays[count] = overlay;
			count++;
		}

		@Nullable BakedLayer build() {
			if (count == 0) {
				return null;
			}
			return new BakedLayer(
				Arrays.copyOf(positions, count * 3),
				Arrays.copyOf(uvs, count * 2),
				Arrays.copyOf(normals, count * 3),
				Arrays.copyOf(colors, count),
				Arrays.copyOf(lights, count),
				Arrays.copyOf(overlays, count),
				count
			);
		}
	}

	private record BakedLayer(
		float[] positions,
		float[] uvs,
		float[] normals,
		int[] colors,
		int[] lights,
		int[] overlays,
		int vertexCount
	) {
		void emit(PoseStack.Pose pose, VertexConsumer consumer) {
			Matrix4f mat = pose.pose();
			Matrix3f normMat = pose.normal();
			Vector3f posVec = new Vector3f();
			Vector3f normVec = new Vector3f();
			for (int i = 0; i < vertexCount; i++) {
				int pIdx = i * 3;
				int uIdx = i * 2;
				mat.transformPosition(positions[pIdx], positions[pIdx + 1], positions[pIdx + 2], posVec);
				normVec.set(normals[pIdx], normals[pIdx + 1], normals[pIdx + 2]).mul(normMat).normalize();
				consumer.addVertex(
					posVec.x, posVec.y, posVec.z,
					colors[i],
					uvs[uIdx], uvs[uIdx + 1],
					overlays[i],
					lights[i],
					normVec.x, normVec.y, normVec.z
				);
			}
		}
	}

	private record SpecialBlock(int x, int y, int z, BlockModelRenderState state) {
	}

	private static final class FluidSectionOutput implements FluidRenderer.Output {
		private final Map<ChunkSectionLayer, LayerBuilder> builders;
		private int sectionOffsetX;
		private int sectionOffsetY;
		private int sectionOffsetZ;

		private FluidSectionOutput(Map<ChunkSectionLayer, LayerBuilder> builders) {
			this.builders = builders;
		}

		void setBlockPos(BlockPos pos) {
			this.sectionOffsetX = pos.getX() & ~15;
			this.sectionOffsetY = pos.getY() & ~15;
			this.sectionOffsetZ = pos.getZ() & ~15;
		}

		@Override
		public VertexConsumer getBuilder(ChunkSectionLayer layer) {
			LayerBuilder target = builders.computeIfAbsent(layer, ignored -> new LayerBuilder());
			return new VertexConsumer() {
				private float vx;
				private float vy;
				private float vz;
				private int color = -1;
				private float u;
				private float v;
				private int overlay = OverlayTexture.NO_OVERLAY;
				private int light = LightCoordsUtil.FULL_BRIGHT;
				private float nx;
				private float ny = 1.0F;
				private float nz;

				@Override
				public void addVertex(
					float x, float y, float z,
					int c, float uCoord, float vCoord,
					int ov, int lgt,
					float normX, float normY, float normZ
				) {
					target.addVertex(
						sectionOffsetX + x,
						sectionOffsetY + y,
						sectionOffsetZ + z,
						(c & 0xFF000000) == 0 ? (0xD0000000 | (c & 0x00FFFFFF)) : c,
						uCoord, vCoord,
						ov,
						lgt == 0 ? LightCoordsUtil.FULL_BRIGHT : lgt,
						normX, normY, normZ
					);
				}

				@Override
				public VertexConsumer addVertex(float x, float y, float z) {
					this.vx = sectionOffsetX + x;
					this.vy = sectionOffsetY + y;
					this.vz = sectionOffsetZ + z;
					return this;
				}

				@Override
				public VertexConsumer setColor(int r, int g, int b, int a) {
					this.color = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
					return this;
				}

				@Override
				public VertexConsumer setColor(int argb) {
					this.color = argb;
					return this;
				}

				@Override
				public VertexConsumer setUv(float u, float v) {
					this.u = u;
					this.v = v;
					return this;
				}

				@Override
				public VertexConsumer setUv1(int u, int v) {
					this.overlay = OverlayTexture.pack(u, v);
					return this;
				}

				@Override
				public VertexConsumer setUv2(int u, int v) {
					this.light = LightCoordsUtil.pack(u & 0xF, v & 0xF);
					return this;
				}

				@Override
				public VertexConsumer setUv3(float u, float v) {
					return this;
				}

				@Override
				public VertexConsumer setNormal(float x, float y, float z) {
					this.nx = x;
					this.ny = y;
					this.nz = z;
					target.addVertex(vx, vy, vz, color, u, v, overlay, light, nx, ny, nz);
					return this;
				}

				@Override
				public VertexConsumer setLineWidth(float width) {
					return this;
				}
			};
		}
	}

	private static final class ShellBlockView implements BlockAndTintGetter {
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final @Nullable BlockState[] grid;

		private ShellBlockView(OguiStructureShell shell, BlockState[] resolvedPalette) {
			this.sizeX = shell.sizeX();
			this.sizeY = shell.sizeY();
			this.sizeZ = shell.sizeZ();
			this.grid = new BlockState[sizeX * sizeY * sizeZ];
			for (OguiStructureShell.BlockEntry entry : shell.blocks()) {
				BlockState state = resolvedPalette[entry.paletteIndex()];
				if (state != null && !state.isAir()) {
					int idx = (entry.y() * sizeZ + entry.z()) * sizeX + entry.x();
					this.grid[idx] = state;
				}
			}
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
				return Blocks.AIR.defaultBlockState();
			}
			BlockState state = grid[(y * sizeZ + z) * sizeX + x];
			return state != null ? state : Blocks.AIR.defaultBlockState();
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public CardinalLighting cardinalLighting() {
			return CardinalLighting.DEFAULT;
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public int getBrightness(LightLayer layer, BlockPos pos) {
			return 15;
		}

		@Override
		public int getRawBrightness(BlockPos pos, int amount) {
			return 15;
		}

		@Override
		public boolean canSeeSky(BlockPos pos) {
			return true;
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver resolver) {
			if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
				return WATER_TINT;
			}
			if (resolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
				return DRY_FOLIAGE_TINT;
			}
			return GRASS_FOLIAGE_TINT;
		}

		@Override
		public int getHeight() {
			return Math.max(16, sizeY);
		}

		@Override
		public int getMinY() {
			return 0;
		}
	}

	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final int blockCount;
	private final float centerX;
	private final float centerY;
	private final float centerZ;
	private final float boundingRadius;
	private final @Nullable BakedLayer solidLayer;
	private final @Nullable BakedLayer cutoutLayer;
	private final @Nullable BakedLayer translucentLayer;
	private final List<SpecialBlock> specialBlocks;

	private OguiStructureMesh(
		int sizeX,
		int sizeY,
		int sizeZ,
		int blockCount,
		float centerX,
		float centerY,
		float centerZ,
		float boundingRadius,
		@Nullable BakedLayer solidLayer,
		@Nullable BakedLayer cutoutLayer,
		@Nullable BakedLayer translucentLayer,
		List<SpecialBlock> specialBlocks
	) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.blockCount = blockCount;
		this.centerX = centerX;
		this.centerY = centerY;
		this.centerZ = centerZ;
		this.boundingRadius = boundingRadius;
		this.solidLayer = solidLayer;
		this.cutoutLayer = cutoutLayer;
		this.translucentLayer = translucentLayer;
		this.specialBlocks = List.copyOf(specialBlocks);
	}

	public static OguiStructureMesh bake(OguiStructureShell shell, Minecraft mc) {
		List<String> paletteSpecs = shell.palette();
		BlockState[] resolvedPalette = new BlockState[paletteSpecs.size()];
		for (int i = 0; i < paletteSpecs.size(); i++) {
			resolvedPalette[i] = OguiStructureShell.decodeBlockState(BuiltInRegistries.BLOCK, paletteSpecs.get(i));
		}

		ShellBlockView view = new ShellBlockView(shell, resolvedPalette);
		ModelManager modelManager = mc.getModelManager();
		BlockStateModelSet stateModels = modelManager.getBlockStateModelSet();
		ModelBlockRenderer blockRenderer = new ModelBlockRenderer(true, false, mc.getBlockColors());
		FluidRenderer fluidRenderer = new FluidRenderer(modelManager.getFluidStateModelSet());
		BlockModelResolver specialResolver = new BlockModelResolver(modelManager);
		BlockDisplayContext displayContext = BlockDisplayContext.create();

		Map<ChunkSectionLayer, LayerBuilder> layers = new EnumMap<>(ChunkSectionLayer.class);
		FluidSectionOutput fluidOutput = new FluidSectionOutput(layers);
		List<SpecialBlock> specialBlocks = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;

		for (OguiStructureShell.BlockEntry entry : shell.blocks()) {
			BlockState state = resolvedPalette[entry.paletteIndex()];
			if (state == null || state.isAir()) {
				continue;
			}
			int bx = entry.x();
			int by = entry.y();
			int bz = entry.z();
			cursor.set(bx, by, bz);

			minX = Math.min(minX, bx);
			minY = Math.min(minY, by);
			minZ = Math.min(minZ, bz);
			maxX = Math.max(maxX, bx + 1.0F);
			maxY = Math.max(maxY, by + 1.0F);
			maxZ = Math.max(maxZ, bz + 1.0F);

			FluidState fluidState = state.getFluidState();
			if (!fluidState.isEmpty()) {
				fluidOutput.setBlockPos(cursor);
				fluidRenderer.tesselate(view, cursor, fluidOutput, state, fluidState);
			}

			var builtInOrWrapper = modelManager.getBlockModelSet().get(state);
			if (fluidState.isEmpty() && !(builtInOrWrapper instanceof BlockStateModelWrapper)) {
				BlockModelRenderState specialState = new BlockModelRenderState();
				specialResolver.update(specialState, state, displayContext);
				if (!specialState.isEmpty()) {
					specialBlocks.add(new SpecialBlock(bx, by, bz, specialState));
				}
			} else if (state.getRenderShape() == RenderShape.MODEL) {
				BlockStateModel model = stateModels.get(state);
				blockRenderer.tesselateBlock(
					(qx, qy, qz, quad, quadInstance) -> putBakedQuad(layers, qx, qy, qz, quad, quadInstance),
					bx, by, bz,
					view,
					cursor,
					state,
					model,
					state.getSeed(cursor)
				);
			}
		}

		float cx;
		float cy;
		float cz;
		float radius;
		if (Float.isFinite(minX)) {
			cx = (minX + maxX) * 0.5F;
			cy = (minY + maxY) * 0.5F;
			cz = (minZ + maxZ) * 0.5F;
			float dx = (maxX - minX) * 0.5F;
			float dy = (maxY - minY) * 0.5F;
			float dz = (maxZ - minZ) * 0.5F;
			radius = Math.max(1.0F, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
		} else {
			cx = shell.sizeX() * 0.5F;
			cy = shell.sizeY() * 0.5F;
			cz = shell.sizeZ() * 0.5F;
			radius = Math.max(1.0F, (float) Math.sqrt(cx * cx + cy * cy + cz * cz));
		}

		LayerBuilder solidBuilder = layers.get(ChunkSectionLayer.SOLID);
		LayerBuilder cutoutBuilder = layers.get(ChunkSectionLayer.CUTOUT);
		LayerBuilder translucentBuilder = layers.get(ChunkSectionLayer.TRANSLUCENT);
		return new OguiStructureMesh(
			shell.sizeX(),
			shell.sizeY(),
			shell.sizeZ(),
			shell.blocks().size(),
			cx, cy, cz,
			radius,
			solidBuilder != null ? solidBuilder.build() : null,
			cutoutBuilder != null ? cutoutBuilder.build() : null,
			translucentBuilder != null ? translucentBuilder.build() : null,
			specialBlocks
		);
	}

	private static void putBakedQuad(
		Map<ChunkSectionLayer, LayerBuilder> layers,
		float offsetX,
		float offsetY,
		float offsetZ,
		BakedQuad quad,
		QuadInstance instance
	) {
		ChunkSectionLayer sectionLayer = quad.materialInfo().layer();
		LayerBuilder builder = layers.computeIfAbsent(sectionLayer, ignored -> new LayerBuilder());
		Vector3fc normal = quad.direction().getUnitVec3f();
		int lightEmission = quad.materialInfo().lightEmission();
		int overlay = instance.overlayCoords();
		for (int i = 0; i < 4; i++) {
			Vector3fc p = quad.position(i);
			long packedUv = quad.packedUV(i);
			int color = instance.getColor(i);
			int light = instance.getLightCoordsWithEmission(i, lightEmission);
			if (light == 0) {
				light = LightCoordsUtil.FULL_BRIGHT;
			}
			builder.addVertex(
				offsetX + p.x(),
				offsetY + p.y(),
				offsetZ + p.z(),
				color,
				UVPair.unpackU(packedUv),
				UVPair.unpackV(packedUv),
				overlay,
				light,
				normal.x(),
				normal.y(),
				normal.z()
			);
		}
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

	public int blockCount() {
		return blockCount;
	}

	public float centerX() {
		return centerX;
	}

	public float centerY() {
		return centerY;
	}

	public float centerZ() {
		return centerZ;
	}

	public float boundingRadius() {
		return boundingRadius;
	}

	public boolean isEmpty() {
		return solidLayer == null && cutoutLayer == null && translucentLayer == null && specialBlocks.isEmpty();
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector collector) {
		if (solidLayer != null) {
			submitLayer(poseStack, collector, RenderTypes.solidMovingBlock(), solidLayer);
		}
		if (cutoutLayer != null) {
			submitLayer(poseStack, collector, RenderTypes.cutoutMovingBlock(), cutoutLayer);
		}
		if (translucentLayer != null) {
			submitLayer(poseStack, collector, RenderTypes.translucentMovingBlock(), translucentLayer);
		}
		if (!specialBlocks.isEmpty()) {
			for (SpecialBlock sb : specialBlocks) {
				poseStack.pushPose();
				poseStack.translate(sb.x(), sb.y(), sb.z());
				sb.state().submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
	}

	private static void submitLayer(
		PoseStack poseStack,
		SubmitNodeCollector collector,
		RenderType renderType,
		BakedLayer layer
	) {
		collector.submitCustomGeometry(poseStack, renderType, layer::emit);
	}
}
