package dev.justaclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's box is, so overlays can sit beside it and outline its slots. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int jac$left();

	@Accessor("topPos")
	int jac$top();

	@Accessor("imageWidth")
	int jac$imageWidth();

	@Accessor("imageHeight")
	int jac$imageHeight();

	@Accessor("hoveredSlot")
	@Nullable Slot jac$hoveredSlot();
}
