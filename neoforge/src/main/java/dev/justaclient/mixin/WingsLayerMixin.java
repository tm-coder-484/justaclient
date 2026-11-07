package dev.justaclient.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.impl.CapeFeature;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ElytraLayer.class)
abstract class WingsLayerMixin {
    @ModifyExpressionValue(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation justaclient$elytra(ResourceLocation chosen) { return CapeFeature.elytraTexture(chosen); }
}
