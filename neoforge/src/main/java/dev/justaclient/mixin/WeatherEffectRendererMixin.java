package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class WeatherEffectRendererMixin {
	/** Hides falling rain and snow. Rain sounds and splashes carry on, so you still know it is raining. */
	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void justaclient$hide(CallbackInfo ci) {
		if (CleanWorldFeature.hidesWeather()) {
			ci.cancel();
		}
	}
}
