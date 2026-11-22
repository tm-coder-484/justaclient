package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.EndCrystalFeature;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalRendererMixin {
    @ModifyArgs(method="render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",ordinal=0))
    private void justaclient$size(Args args) { float scale=EndCrystalFeature.scale();for(int i=0;i<3;i++)args.set(i,(float)args.get(i)*scale); }
    @ModifyArg(method="render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At(value="INVOKE",target="Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"),index=0)
    private float justaclient$spin(float angle) { return EndCrystalFeature.age(angle/3f)*3f; }
    @ModifyReturnValue(method="getY",at=@At("RETURN"))
    private static float justaclient$bob(float y) { return EndCrystalFeature.bobY(y); }
}
