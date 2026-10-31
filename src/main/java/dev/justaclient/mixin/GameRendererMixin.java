package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.CameraFeature;
import dev.justaclient.feature.impl.OverlaysFeature;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.render.PostFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Unique
	private boolean justaclient$worldDrawn;

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void justaclient$worldDone(DeltaTracker delta, CallbackInfo ci) {
		justaclient$worldDrawn = true;
	}

	/** The world, hand and entity outlines are done and the GUI has not started: grade the frame now. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void justaclient$postFx(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
		if (justaclient$worldDrawn) {
			justaclient$worldDrawn = false;
			PostFx.apply();
		}
	}

	/** Stretched aspect ratio: the world is projected narrower or wider than the window, then fills it. */
	@ModifyArg(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"), index = 1)
	private float justaclient$aspect(float aspect) {
		return Features.get(CameraFeature.class).aspect(aspect);
	}

	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float justaclient$nauseaWarp(float nausea) {
		return Features.get(OverlaysFeature.class).hidesNausea() ? 0f : nausea;
	}

	/** Zoom narrows the world FOV only; the held item keeps its normal FOV (useFovSetting is false for the hand). */
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float justaclient$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		return useFovSetting ? fov / Features.get(ZoomFeature.class).factor() : Features.get(CameraFeature.class).handFov(fov);
	}
}
