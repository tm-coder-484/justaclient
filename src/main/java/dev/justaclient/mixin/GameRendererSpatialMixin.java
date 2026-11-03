package dev.justaclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererSpatialMixin {
	@Shadow
	@Final
	private GuiRenderState guiRenderState;

	@Shadow
	@Final
	private Minecraft minecraft;

	/** Spatial menus: split the GUI here so everything the screen draws can go to its own texture. */
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void justaclient$spatialSplit(DeltaTracker delta, boolean renderLevel, CallbackInfo ci, @Local GuiGraphics g) {
		if (minecraft.screen != null) {
			SpatialMenus.beginScreen(g, guiRenderState, ((GuiRenderStateAccessor) guiRenderState).justaclient$firstStratumAfterBlur(), minecraft.screen);
		}
	}
}
