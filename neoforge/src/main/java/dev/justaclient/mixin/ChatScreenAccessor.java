package dev.justaclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The chat screen's text box, for the saved message keys. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	@Accessor("input")
	EditBox jac$input();
}
