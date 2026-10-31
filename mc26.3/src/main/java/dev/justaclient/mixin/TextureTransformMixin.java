package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.GlintFeature;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextureTransform.class)
abstract class TextureTransformMixin {
	@ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;glintSpeed:D"))
	private static double justaclient$glintSpeed(double speed) {
		GlintFeature glint = Features.get(GlintFeature.class);
		return glint == null ? speed : glint.speed(speed);
	}
}
