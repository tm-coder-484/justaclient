package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ScreenStyleFeature;
import dev.justaclient.feature.impl.TitleScreenFeature;
import dev.justaclient.mods.ModConfigScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
abstract class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private @Nullable Screen justaclient$titleScreen(@Nullable Screen screen) {
		if (screen == null) {
			// A mod's settings screen closing to nothing goes back to the Mods panel that opened it. Otherwise do what
			// vanilla does a few lines later (title screen when not in a world) here, so our title screen replaces it.
			Minecraft mc = Minecraft.getInstance();
			Screen back = ModConfigScreens.closedTo(((Gui) (Object) this).screen());
			if (back != null) {
				screen = back;
			} else if (mc != null && mc.level == null) {
				screen = new TitleScreen();
			}
		}
		TitleScreenFeature feature = Features.get(TitleScreenFeature.class);
		Screen result = screen == null || feature == null ? screen : feature.replace(screen);
		ScreenStyleFeature.seen(result);
		return result;
	}
}
