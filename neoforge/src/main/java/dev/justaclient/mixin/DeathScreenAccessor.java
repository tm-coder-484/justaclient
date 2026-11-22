package dev.justaclient.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The death message the server sent, for the death recap. */
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
	@Accessor("causeOfDeath")
	Component jac$causeOfDeath();
}
