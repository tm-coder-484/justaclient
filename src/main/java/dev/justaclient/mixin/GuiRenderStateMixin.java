package dev.justaclient.mixin;

import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
abstract class GuiRenderStateMixin {
	/** Spatial menus took this frame's one split; a screen asking for vanilla's blur afterwards would otherwise crash. */
	@Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
	private void justaclient$spatialSplit(CallbackInfo ci) {
		if (!SpatialMenus.blurAllowed()) {
			ci.cancel();
		}
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void justaclient$spatialReset(CallbackInfo ci) {
		SpatialMenus.resetFrame();
	}
}
