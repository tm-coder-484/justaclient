package dev.justaclient.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The furnace's synced numbers: 0 fuel left, 1 fuel total, 2 cook progress, 3 cook total (all in ticks). */
@Mixin(AbstractFurnaceMenu.class)
public interface AbstractFurnaceMenuAccessor {
	@Accessor("data")
	ContainerData jac$data();
}
