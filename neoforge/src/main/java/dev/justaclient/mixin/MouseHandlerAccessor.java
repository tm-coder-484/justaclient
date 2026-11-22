package dev.justaclient.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw window coordinates, before the spatial-menu GUI mapping. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") double justaclient$rawX();
    @Accessor("ypos") double justaclient$rawY();
}
