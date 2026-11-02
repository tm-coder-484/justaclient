package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ZoomFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	/** Zoom narrows the world FOV only; the held item keeps its normal FOV (useFovSetting is false for the hand). */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float justaclient$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		return useFovSetting ? fov / Features.get(ZoomFeature.class).factor() : fov;
	}
}
