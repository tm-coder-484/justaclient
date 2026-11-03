package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.CameraFeature;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private Level level;

	@Shadow
	private Entity entity;

	@Shadow
	protected abstract void move(float forward, float up, float right);

	@Shadow
	public abstract Vec3 position();

	@Shadow
	public abstract boolean isDetached();

	@Shadow
	public abstract Vector3fc forwardVector();

	@Shadow
	public abstract Vector3fc upVector();

	@Shadow
	public abstract Vector3fc leftVector();

	/** Spatial menus in third person: slide the camera over the shoulder so the menu has room beside the character. */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void justaclient$spatialShoulder(float partialTick, CallbackInfo ci) {
		boolean detached = isDetached();
		float want = SpatialMenus.shoulder(detached);
		if (want != 0 && level != null && entity != null) {
			Vector3fc left = leftVector();
			float room = SpatialMenus.clipSideways(level, entity, position(), new Vector3f(left).negate(), want);
			if (room != 0) {
				move(0, 0, room);
			}
		}
		if (detached && entity != null) {
			SpatialMenus.cameraPlaced(entity.getEyePosition(partialTick), position(), forwardVector(), upVector(), leftVector());
		}
	}

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
