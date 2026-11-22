package dev.justaclient.mixin;

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
	protected abstract void move(float forward, float up, float right);

	@Shadow
	public abstract Vec3 getPosition();

	@Shadow
	public abstract Vector3f getLookVector();

	@Shadow
	public abstract Vector3f getUpVector();

	@Shadow
	public abstract Vector3f getLeftVector();

	/** Spatial menus in third person: slide the camera over the shoulder so the menu has room beside the character. */
	@Inject(method = "setup", at = @At("TAIL"))
	private void justaclient$spatialShoulder(net.minecraft.world.level.BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
		float want = SpatialMenus.shoulder(detached);
		if (want != 0 && level instanceof Level && entity != null) {
			Vector3fc left = getLeftVector();
			float room = SpatialMenus.clipSideways((Level)level, entity, getPosition(), new Vector3f(left).negate(), want);
			if (room != 0) {
				move(0, 0, room);
			}
		}
		if (detached && entity != null) {
			SpatialMenus.cameraPlaced(entity.getEyePosition(partialTick), getPosition(), getLookVector(), getUpVector(), getLeftVector());
		}
	}
}
