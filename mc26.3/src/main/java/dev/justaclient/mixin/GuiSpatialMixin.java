package dev.justaclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiSpatialMixin {
	@Shadow
	@Final
	private GuiRenderState guiRenderState;

	@Shadow
	private Screen screen;

	/** Spatial menus: split the GUI here so everything the screen draws can go to its own texture. */
	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void justaclient$spatialSplit(DeltaTracker delta, boolean renderLevel, boolean resourcesLoaded, CallbackInfo ci,
		@Local GuiGraphicsExtractor g) {
		if (screen != null) {
			SpatialMenus.beginScreen(g, guiRenderState, ((GuiRenderStateAccessor) guiRenderState).justaclient$firstStratumAfterBlur(), screen);
		}
	}
}
