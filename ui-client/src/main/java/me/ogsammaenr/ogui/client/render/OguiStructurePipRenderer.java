package me.ogsammaenr.ogui.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

import java.util.Objects;

/**
 * Picture-in-Picture offscreen renderer drawing an interactive rotating {@link OguiStructureMesh} inside Ogui screens.
 * Caches the previous FBO frame when camera orientation, zoom scale, and mesh key have not changed.
 */
public final class OguiStructurePipRenderer extends PictureInPictureRenderer<OguiStructurePipRenderState> {
	private String lastMeshKey = "";
	private float lastYaw = Float.NaN;
	private float lastPitch = Float.NaN;
	private float lastScale = Float.NaN;
	private int lastWidth = -1;
	private int lastHeight = -1;

	@Override
	public Class<OguiStructurePipRenderState> getRenderStateClass() {
		return OguiStructurePipRenderState.class;
	}

	@Override
	protected boolean textureIsReadyToBlit(OguiStructurePipRenderState state) {
		int width = state.x1() - state.x0();
		int height = state.y1() - state.y0();
		return Objects.equals(lastMeshKey, state.meshKey())
			&& Float.compare(lastYaw, state.yawDegrees()) == 0
			&& Float.compare(lastPitch, state.pitchDegrees()) == 0
			&& Float.compare(lastScale, state.scale()) == 0
			&& lastWidth == width
			&& lastHeight == height;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected void renderToTexture(
		OguiStructurePipRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector collector
	) {
		this.lastMeshKey = state.meshKey();
		this.lastYaw = state.yawDegrees();
		this.lastPitch = state.pitchDegrees();
		this.lastScale = state.scale();
		this.lastWidth = state.x1() - state.x0();
		this.lastHeight = state.y1() - state.y0();

		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);

		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.XP, 180.0F);
		poseStack.rotateDegrees(Axis.XP, state.pitchDegrees());
		poseStack.rotateDegrees(Axis.YP, state.yawDegrees());
		OguiStructureMesh mesh = state.mesh();
		poseStack.translate(-mesh.centerX(), -mesh.centerY(), -mesh.centerZ());
		mesh.submit(poseStack, collector);
		poseStack.popPose();
	}

	@Override
	protected String getTextureLabel() {
		return "ogui structure preview";
	}
}
