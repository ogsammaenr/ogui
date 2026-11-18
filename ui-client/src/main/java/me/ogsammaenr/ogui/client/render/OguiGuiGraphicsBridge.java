package me.ogsammaenr.ogui.client.render;

import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * Mixin-backed bridge on {@code GuiGraphicsExtractor} for submitting custom Picture-in-Picture states.
 */
public interface OguiGuiGraphicsBridge {
	void ogui$submitPip(PictureInPictureRenderState state);
}
