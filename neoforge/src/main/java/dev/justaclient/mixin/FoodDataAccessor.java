package dev.justaclient.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exhaustion is never sent to the client, so it is only read from the singleplayer server's copy. */
@Mixin(FoodData.class)
public interface FoodDataAccessor {
	@Accessor("exhaustionLevel")
	float jac$exhaustion();
}
