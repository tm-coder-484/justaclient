package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
	/**
	 * Hides falling rain and snow by emptying the extracted columns, so nothing is drawn. Rain sounds and splashes carry
	 * on, so you still know it is raining.
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void justaclient$hide(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (CleanWorldFeature.hidesWeather()) {
			state.reset();
		}
	}
}
