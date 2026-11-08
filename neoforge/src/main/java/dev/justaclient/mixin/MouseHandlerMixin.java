package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ZoomFeature;
import dev.justaclient.hud.ClickTracker;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import org.lwjgl.glfw.GLFW;
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

	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;

	@Inject(method = "onPress", at = @At("HEAD"))
	private void justaclient$countClicks(long window, int button, int action, int modifiers, CallbackInfo ci) {
		if (action == GLFW.GLFW_PRESS && minecraft.screen == null && window == minecraft.getWindow().getWindow()) {
			ClickTracker.onPress(button);
		}
	}

	/** While zooming, the wheel changes the zoom level instead of the hotbar slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void justaclient$zoomScroll(long window, double x, double y, CallbackInfo ci) {
		if (minecraft.screen == null && window == minecraft.getWindow().getWindow() && Features.get(ZoomFeature.class).onScroll(y)) {
			ci.cancel();
		}
	}

	/** Slows the camera while zoomed so aiming feels the same at every zoom level. */
	@ModifyExpressionValue(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object justaclient$zoomSensitivity(Object sensitivity) {
		return Features.get(ZoomFeature.class).adjustSensitivity((Double) sensitivity);
	}

	/** Spatial menus: the mouse points at the floating menu, so screens get the spot on the menu under it. */
	@ModifyReturnValue(method = "xpos", at = @At("RETURN"))
	private double justaclient$spatialX(double x) {
		double[] at = SpatialMenus.mapMouse(xpos, ypos);
		return at == null ? x : at[0]*minecraft.getWindow().getScreenWidth()/minecraft.getWindow().getGuiScaledWidth();
	}

	@ModifyReturnValue(method = "ypos", at = @At("RETURN"))
	private double justaclient$spatialY(double y) {
		double[] at = SpatialMenus.mapMouse(xpos, ypos);
		return at == null ? y : at[1]*minecraft.getWindow().getScreenHeight()/minecraft.getWindow().getGuiScaledHeight();
	}
    @ModifyExpressionValue(method={"onPress","onScroll","handleAccumulatedMovement"},at=@At(value="FIELD",target="Lnet/minecraft/client/MouseHandler;xpos:D",opcode=180))
    private double justaclient$eventX(double x) { return justaclient$spatialX(x); }
    @ModifyExpressionValue(method={"onPress","onScroll","handleAccumulatedMovement"},at=@At(value="FIELD",target="Lnet/minecraft/client/MouseHandler;ypos:D",opcode=180))
    private double justaclient$eventY(double y) { return justaclient$spatialY(y); }

    // Keep GLFW's accumulated motion in window pixels. Only the dispatched GUI delta
    // is transformed, using both endpoints on the same curved panel.
    @ModifyExpressionValue(method="handleAccumulatedMovement",at=@At(value="FIELD",target="Lnet/minecraft/client/MouseHandler;accumulatedDX:D",opcode=180))
    private double justaclient$dragX(double dx) { return justaclient$dragDelta(dx,true); }
    @ModifyExpressionValue(method="handleAccumulatedMovement",at=@At(value="FIELD",target="Lnet/minecraft/client/MouseHandler;accumulatedDY:D",opcode=180))
    private double justaclient$dragY(double dy) { return justaclient$dragDelta(dy,false); }

    @org.spongepowered.asm.mixin.Unique
    private double justaclient$dragDelta(double original,boolean horizontal) {
        double[] current=SpatialMenus.mapMouse(xpos,ypos);
        double[] previous=SpatialMenus.mapMouse(xpos-accumulatedDX,ypos-accumulatedDY);
        if(current==null || previous==null) return original;
        var window=minecraft.getWindow();
        return horizontal ? (current[0]-previous[0])*window.getScreenWidth()/window.getGuiScaledWidth()
            : (current[1]-previous[1])*window.getScreenHeight()/window.getGuiScaledHeight();
    }
}
