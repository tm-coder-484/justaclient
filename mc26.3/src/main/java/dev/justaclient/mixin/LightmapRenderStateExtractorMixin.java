package dev.justaclient.mixin;

import dev.justaclient.render.LightTint;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRenderStateExtractorMixin {
	/** Light colour: tints block and sky light once vanilla has worked out this frame's lightmap inputs. */
	@Inject(method = "extract", at = @At("TAIL"))
	private void justaclient$tint(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		LightTint.apply(state);
	}
}
