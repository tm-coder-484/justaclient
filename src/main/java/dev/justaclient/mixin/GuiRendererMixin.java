package dev.justaclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.justaclient.render.BlurEngine;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
	/** The main target still holds only the world (or title panorama) here; blur it before any GUI is drawn over it. */
	@Inject(method = "draw", at = @At("HEAD"))
	private void justaclient$captureBlur(GpuBufferSlice fog, CallbackInfo ci) {
		BlurEngine.capture();
	}
}
