package dev.justaclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far the chat box is scrolled up, for chat scroll memory. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("chatScrollbarPos")
	int jac$scroll();
}
