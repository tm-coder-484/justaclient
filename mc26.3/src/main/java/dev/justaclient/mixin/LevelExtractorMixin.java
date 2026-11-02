package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.OverlaysFeature;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
	@ModifyExpressionValue(method = "extractPlayerState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float justaclient$nauseaWarp(float nausea) {
		return Features.get(OverlaysFeature.class).hidesNausea() ? 0f : nausea;
	}
}
