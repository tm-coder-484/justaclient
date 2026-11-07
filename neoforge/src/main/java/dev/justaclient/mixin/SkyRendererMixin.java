package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.SkyFeature;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LevelRenderer.class)
abstract class SkyRendererMixin {
    @ModifyExpressionValue(method="renderSky",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 justaclient$skyTint(Vec3 color) {
        int packed=0xff000000 | Math.round((float)color.x*255)<<16 | Math.round((float)color.y*255)<<8 | Math.round((float)color.z*255);
        int tint=Features.get(SkyFeature.class).tintSky(packed);
        return new Vec3((tint>>16&255)/255.0,(tint>>8&255)/255.0,(tint&255)/255.0);
    }
}
