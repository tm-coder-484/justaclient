package dev.justaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.SkyFeature;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
	private int justaclient$tintSky(int color) {
		return Features.get(SkyFeature.class).tintSky(color);
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void justaclient$night(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase, float rainBrightness,
		float starBrightness, CallbackInfo ci) {
		Features.get(SkyFeature.class).renderNight(starAngle, rainBrightness, starBrightness);
	}
}
