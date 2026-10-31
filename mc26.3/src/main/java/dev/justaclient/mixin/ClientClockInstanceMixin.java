package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.TimeFeature;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time of day on 26.3: the sky, sun, moon and lighting all read the world clocks, and the client keeps its own copy of
 * them. Reporting the chosen time here changes what you see while the server's clock is untouched.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
abstract class ClientClockInstanceMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long justaclient$time(long ticks) {
		return TimeFeature.dayTime(ticks);
	}

	@ModifyReturnValue(method = "partialTick", at = @At("RETURN"))
	private float justaclient$still(float partial) {
		return TimeFeature.active() ? 0f : partial;
	}
}
