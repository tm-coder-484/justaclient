package dev.justaclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.justaclient.render.Projection;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	/** Keeps this frame's camera so HUD labels can sit over world positions. */
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void justaclient$captureCamera(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera, Matrix4f modelView,
		Matrix4f projection, Matrix4f culling, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		Projection.capture(camera.position(), modelView, projection);
	}
}
