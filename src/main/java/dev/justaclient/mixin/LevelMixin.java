package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.TimeFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
abstract class LevelMixin {
	/** Time of day: the client world reports the chosen time to the sky and lighting. Server worlds are untouched. */
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long justaclient$time(long time) {
		return (Object) this instanceof ClientLevel ? TimeFeature.dayTime(time) : time;
	}
}
