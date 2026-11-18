package me.ogsammaenr.ogui.client.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable GUI render state describing a 3D {@link OguiStructureMesh} PiP viewport.
 */
public record OguiStructurePipRenderState(
	OguiStructureMesh mesh,
	String meshKey,
	float yawDegrees,
	float pitchDegrees,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable ScreenRectangle scissorArea,
	ScreenRectangle bounds
) implements PictureInPictureRenderState {
	public OguiStructurePipRenderState(
		OguiStructureMesh mesh,
		String meshKey,
		float yawDegrees,
		float pitchDegrees,
		int x0,
		int y0,
		int x1,
		int y1,
		float scale,
		@Nullable ScreenRectangle scissorArea
	) {
		this(
			mesh,
			meshKey,
			yawDegrees,
			pitchDegrees,
			x0,
			y0,
			x1,
			y1,
			scale,
			scissorArea,
			PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea)
		);
	}

	public OguiStructurePipRenderState {
		Objects.requireNonNull(mesh, "mesh");
		Objects.requireNonNull(meshKey, "meshKey");
		Objects.requireNonNull(bounds, "bounds");
	}
}
