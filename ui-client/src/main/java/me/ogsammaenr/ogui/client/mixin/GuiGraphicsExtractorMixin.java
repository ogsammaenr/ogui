package me.ogsammaenr.ogui.client.mixin;

import me.ogsammaenr.ogui.client.render.OguiGuiGraphicsBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements OguiGuiGraphicsBridge {
	@Shadow
	@Final
	private GuiRenderState guiRenderState;

	@Override
	public void ogui$submitPip(PictureInPictureRenderState state) {
		this.guiRenderState.addPicturesInPictureState(state);
	}
}
