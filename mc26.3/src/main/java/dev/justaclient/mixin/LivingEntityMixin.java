package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/** Your own swing animation only. Attack cooldown and the server's idea of your swing are separate and unchanged. */
	@ModifyReturnValue(method = "getModifiedSwingDuration", at = @At("RETURN"))
	private int justaclient$swingSpeed(int ticks) {
		if ((Object) this != Minecraft.getInstance().player) {
			return ticks;
		}
		HandFeature hand = Features.get(HandFeature.class);
		return hand != null && hand.isEnabled() ? hand.swingDuration(ticks) : ticks;
	}
}
