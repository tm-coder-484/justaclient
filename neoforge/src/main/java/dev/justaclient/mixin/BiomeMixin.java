package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.WaterColorFeature;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Biome.class)
abstract class BiomeMixin {
	/** Water colour. Sodium reads water tint through the same resolver, so it follows too. */
	@ModifyReturnValue(method = "getWaterColor", at = @At("RETURN"))
	private int justaclient$water(int color) {
		return WaterColorFeature.recolor(color);
	}
}
