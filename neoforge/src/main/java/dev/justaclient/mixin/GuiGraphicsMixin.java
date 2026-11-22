package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ItemOverlayFeature;
import dev.justaclient.render.ScreenSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
    @Inject(method="blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIII)V",at=@At("HEAD"),cancellable=true)
    private void justaclient$skinSprite(ResourceLocation id,int x,int y,int z,int w,int h,CallbackInfo ci) {
        float[] rgba=com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        int color=Math.round(rgba[3]*255)<<24 | 0xffffff;
        if(ScreenSkin.sprite((GuiGraphics)(Object)this,id,x,y,w,h,color))ci.cancel();
    }
    @Inject(method="blit(Lnet/minecraft/resources/ResourceLocation;IIIIIIIFFII)V",at=@At("HEAD"),cancellable=true)
    private void justaclient$skinTexture(ResourceLocation id,int x0,int x1,int y0,int y1,int z,int sw,int sh,float u,float v,int tw,int th,CallbackInfo ci) {
        if(ScreenSkin.texture((GuiGraphics)(Object)this,id,x0,y0,x1-x0,y1-y0))ci.cancel();
    }
    @ModifyExpressionValue(method="renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;isBarVisible()Z"))
    private boolean justaclient$bar(boolean visible,Font font,ItemStack stack,int x,int y,String text) {
        return visible && !Features.get(ItemOverlayFeature.class).drawBar((GuiGraphics)(Object)this,stack,x,y);
    }
    @ModifyExpressionValue(method="renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/Item;F)F"))
    private float justaclient$cooldown(float amount,Font font,ItemStack stack,int x,int y,String text) {
        return Features.get(ItemOverlayFeature.class).drawCooldown((GuiGraphics)(Object)this,stack,x,y)?0f:amount;
    }
    @WrapOperation(method="renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int justaclient$count(GuiGraphics g,Font font,String count,int dx,int dy,int color,boolean shadow,Operation<Integer> original,Font itemFont,ItemStack stack,int x,int y,String override) {
        return Features.get(ItemOverlayFeature.class).drawCount(g,stack,x,y,override)?0:original.call(g,font,count,dx,dy,color,shadow);
    }
}
