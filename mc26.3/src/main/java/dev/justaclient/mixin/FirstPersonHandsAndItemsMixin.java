package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CameraFeature;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
abstract class FirstPersonHandsAndItemsMixin {
	/** Keep held item still: the hand stops trailing behind the camera when you turn. */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void justaclient$stillHand(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (CameraFeature.keepsHandStill()) {
			state.xBob = state.viewXRot;
			state.yBob = state.viewYRot;
		}
	}
}
