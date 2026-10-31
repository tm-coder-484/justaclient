package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.EndCrystalFeature;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalRendererMixin {
	@ModifyArgs(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void justaclient$size(Args args) {
		float s = EndCrystalFeature.scale();
		args.set(0, (float) args.get(0) * s);
		args.set(1, (float) args.get(1) * s);
		args.set(2, (float) args.get(2) * s);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
		at = @At("TAIL"))
	private void justaclient$spin(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
		state.ageInTicks = EndCrystalFeature.age(state.ageInTicks);
	}

	@ModifyReturnValue(method = "getY", at = @At("RETURN"))
	private static float justaclient$bob(float y) {
		return EndCrystalFeature.bobY(y);
	}
}
