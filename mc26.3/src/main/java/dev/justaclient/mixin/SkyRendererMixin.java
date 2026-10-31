package dev.justaclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.SkyFeature;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
	private Vector3fc justaclient$tintSky(Vector3fc color) {
		return Features.get(SkyFeature.class).tintSky(color);
	}

	/** Buffers can't be written while the sky pass is open, so the night sky is uploaded first. */
	@Inject(method = "render", at = @At("HEAD"))
	private void justaclient$prepareNight(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
		Features.get(SkyFeature.class).prepareNight(state);
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void justaclient$night(RenderPass pass, PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
		float rainBrightness, float starBrightness, CallbackInfo ci) {
		Features.get(SkyFeature.class).renderNight(pass);
	}
}
