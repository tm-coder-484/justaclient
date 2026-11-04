package dev.justaclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.hud.ClickTracker;
import dev.justaclient.render.SpatialMenus;
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

	@Shadow
	private double xpos;

	@Shadow
	private double ypos;

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

	/** Spatial menus: the mouse points at the floating menu, so screens get the spot on the menu under it. */
	@ModifyReturnValue(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"))
	private double justaclient$spatialX(double x) {
		double[] at = SpatialMenus.mapMouse(xpos, ypos);
		return at == null ? x : at[0];
	}

	@ModifyReturnValue(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"))
	private double justaclient$spatialY(double y) {
		double[] at = SpatialMenus.mapMouse(xpos, ypos);
		return at == null ? y : at[1];
	}
}
