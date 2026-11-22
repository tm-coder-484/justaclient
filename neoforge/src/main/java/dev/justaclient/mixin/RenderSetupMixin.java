package dev.justaclient.mixin;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.GlintFeature;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TextureManager.class)
abstract class RenderSetupMixin {
    @ModifyVariable(method="getTexture",at=@At("HEAD"),argsOnly=true)
    private ResourceLocation justaclient$glint(ResourceLocation id) {
        var feature=Features.get(GlintFeature.class);return feature==null?id:feature.texture(id);
    }
}
