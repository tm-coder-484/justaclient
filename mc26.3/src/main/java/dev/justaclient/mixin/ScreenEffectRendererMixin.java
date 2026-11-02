package dev.justaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.FireOverlayFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
	@Unique
	private static boolean justaclient$pushed;

	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void justaclient$lowerFire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		FireOverlayFeature fire = Features.get(FireOverlayFeature.class);
		justaclient$pushed = false;
		if (!fire.isEnabled()) {
			return;
		}
		if (fire.hidden()) {
			ci.cancel();
			return;
		}
		pose.pushPose();
		pose.translate(0f, -fire.offset(), 0f);
		justaclient$pushed = true;
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void justaclient$restore(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (justaclient$pushed) {
			pose.popPose();
			justaclient$pushed = false;
		}
	}
}
