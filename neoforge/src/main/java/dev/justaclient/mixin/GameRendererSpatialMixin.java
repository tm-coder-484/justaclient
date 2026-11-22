package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.justaclient.render.SpatialMenus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.DeltaTracker;
@Mixin(GameRenderer.class)
abstract class GameRendererSpatialMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void justaclient$frame(DeltaTracker delta,boolean renderLevel,CallbackInfo ci) { SpatialMenus.resetFrame(); }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void justaclient$spatial(Screen screen,GuiGraphics graphics,int mx,int my,float delta,Operation<Void> original) {
        SpatialMenus.beginScreen(graphics,screen);
        SpatialMenus.beginDraw();
        try { original.call(screen,graphics,mx,my,delta);graphics.flush(); }
        finally { SpatialMenus.composite(); }
    }
}
