package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.GlintFeature;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderSetup.class)
abstract class RenderSetupMixin {
	/** Points the glint render types at the tinted glint texture. */
	@ModifyArg(method = "getTextures", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
	private Identifier justaclient$glint(Identifier id) {
		GlintFeature glint = Features.get(GlintFeature.class);
		return glint == null ? id : glint.texture(id);
	}
}
