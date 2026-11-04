package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.justaclient.render.BlurEngine;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
	/** The main target still holds only the world (or title panorama) here; blur it before any GUI is drawn over it. */
	@Inject(method = "draw", at = @At("HEAD"))
	private void justaclient$captureBlur(CallbackInfo ci) {
		BlurEngine.capture();
		SpatialMenus.beginDraw();
	}

	/** Spatial menus: the screen's part of the GUI (after the split) goes to the menu texture. */
	@ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange("
		+ "Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V",
		ordinal = 1), index = 1)
	private RenderTarget justaclient$spatialTarget(RenderTarget target) {
		return SpatialMenus.target(target);
	}

	@WrapWithCondition(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
	private boolean justaclient$spatialBlur(GameRenderer renderer) {
		return SpatialMenus.vanillaBlur();
	}

	@Inject(method = "draw", at = @At("TAIL"))
	private void justaclient$spatialComposite(CallbackInfo ci) {
		SpatialMenus.composite();
	}
}
