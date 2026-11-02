package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
	/** Hides falling rain and snow. Rain sounds and splashes carry on, so you still know it is raining. */
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void justaclient$hide(MultiBufferSource buffers, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
		if (CleanWorldFeature.hidesWeather()) {
			ci.cancel();
		}
	}
}
