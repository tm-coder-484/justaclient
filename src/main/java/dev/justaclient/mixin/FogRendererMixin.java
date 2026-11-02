package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.FogFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	/** After vanilla has worked out the fog distances and before they are uploaded (and read by Sodium). */
	@Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
	private void justaclient$fogDistance(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
		CallbackInfoReturnable<Vector4f> cir, @Local FogData fog) {
		Features.get(FogFeature.class).adjust(camera, fog);
	}

	@ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
	private Vector4f justaclient$fogColor(Vector4f color, Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken) {
		return Features.get(FogFeature.class).color(camera, level, color);
	}
}
