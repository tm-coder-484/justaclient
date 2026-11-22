package dev.justaclient.mixin;
import com.mojang.blaze3d.vertex.*;
import dev.justaclient.feature.impl.HitboxesFeature;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
abstract class EntityHitboxDebugRendererMixin {
    @Inject(method="renderHitbox",at=@At("HEAD"),cancellable=true)
    private static void justaclient$hitbox(PoseStack pose,VertexConsumer lines,Entity entity,float partial,float red,float green,float blue,CallbackInfo ci) {
        if(!(red==0 && blue==0) && HitboxesFeature.draw(pose,lines,entity,partial))ci.cancel();
    }
}
