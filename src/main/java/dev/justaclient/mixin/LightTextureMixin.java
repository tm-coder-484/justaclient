package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import dev.justaclient.render.LightTint;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
abstract class LightTextureMixin {
	@Inject(method = "updateLightTexture", at = @At("HEAD"))
	private void justaclient$prepareTint(float partialTick, CallbackInfo ci) {
		LightTint.prepare();
	}

	/** Light colour: draws the lightmap with the tinting copy of vanilla's shader. */
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;draw(II)V"))
	private void justaclient$tint(RenderPass pass, int first, int count, Operation<Void> original) {
		LightTint.apply(pass);
		original.call(pass, first, count);
	}
}
