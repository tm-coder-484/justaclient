package dev.justaclient.mixin;

import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiRenderState.class)
public interface GuiRenderStateAccessor {
	@Accessor("firstStratumAfterBlur")
	int justaclient$firstStratumAfterBlur();
}
