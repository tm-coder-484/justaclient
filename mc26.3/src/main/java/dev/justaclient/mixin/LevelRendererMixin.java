package dev.justaclient.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.justaclient.render.Projection;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	/** Keeps this frame's camera so HUD labels can sit over world positions. */
	@Inject(method = "render", at = @At("HEAD"))
	private void justaclient$captureCamera(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera, GpuBufferSlice fog,
		Vector4f fogColor, boolean renderSky, boolean consistentDepth, CallbackInfo ci) {
		Projection.capture(camera.pos, camera.viewRotationMatrix, camera.projectionMatrix);
	}
}
