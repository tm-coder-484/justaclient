package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRendererMixin {
    @ModifyExpressionValue(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"))
    private boolean justaclient$fire(boolean original) { return original && !CleanWorldFeature.hidesEntityFire(); }
}
