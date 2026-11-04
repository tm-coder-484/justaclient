package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.CapeFeature;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WingsLayer.class)
abstract class WingsLayerMixin {
	/** Custom cape: keeps it off your elytra when "Also on elytra" is off. */
	@ModifyReturnValue(method = "getPlayerElytraTexture", at = @At("RETURN"))
	private static Identifier justaclient$elytra(Identifier chosen) {
		return CapeFeature.elytraTexture(chosen);
	}
}
