package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	/** After vanilla has worked out the fog distances and before they are uploaded (and read by Sodium). */
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData justaclient$fogDistance(FogData fog, Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level) {
		Features.get(FogFeature.class).adjust(camera, fog);
		return fog;
	}

	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void justaclient$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, Vector4f dest, CallbackInfo ci) {
		dest.set(Features.get(FogFeature.class).color(camera, level, dest));
	}
}
