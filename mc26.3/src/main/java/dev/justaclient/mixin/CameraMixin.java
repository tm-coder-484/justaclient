package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.CameraFeature;
import dev.justaclient.feature.impl.ZoomFeature;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
abstract class CameraMixin {
	/** Zoom narrows the world FOV only; the held item keeps its own FOV. */
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float justaclient$zoom(float fov) {
		return fov / Features.get(ZoomFeature.class).factor();
	}

	@ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
	private float justaclient$handFov(float fov) {
		return Features.get(CameraFeature.class).handFov(fov);
	}

	/** Stretched aspect ratio: the world is projected narrower or wider than the window, then fills it. */
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"))
	private void justaclient$aspect(Camera camera, float near, float far, float fov, float width, float height, Operation<Void> original) {
		float aspect = Features.get(CameraFeature.class).aspect(width / height);
		original.call(camera, near, far, fov, aspect * height, height);
	}
}
