package dev.justaclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.hud.ClickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void justaclient$countClicks(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == InputConstants.PRESS && minecraft.gui.screen() == null && window == minecraft.getWindow().handle()) {
			ClickTracker.onPress(info.button());
		}
	}

	/** While zooming, the wheel changes the zoom level instead of the hotbar slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void justaclient$zoomScroll(long window, double x, double y, CallbackInfo ci) {
		if (minecraft.gui.screen() == null && window == minecraft.getWindow().handle() && Features.get(ZoomFeature.class).onScroll(y)) {
			ci.cancel();
		}
	}

	/** Slows the camera while zoomed so aiming feels the same at every zoom level. */
	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object justaclient$zoomSensitivity(Object sensitivity) {
		return Features.get(ZoomFeature.class).adjustSensitivity((Double) sensitivity);
	}
}
