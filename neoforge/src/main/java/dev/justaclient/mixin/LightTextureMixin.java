package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.render.LightTint;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LightTexture.class)
abstract class LightTextureMixin {
    @WrapOperation(method="updateLightTexture",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void justaclient$tint(NativeImage image,int x,int y,int color,Operation<Void> original) {
        original.call(image,x,y,LightTint.tint(x,y,color));
    }
}
