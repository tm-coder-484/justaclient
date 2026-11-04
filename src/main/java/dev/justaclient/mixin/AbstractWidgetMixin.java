package dev.justaclient.mixin;

import dev.justaclient.feature.impl.GuiSoundsFeature;
import dev.justaclient.ui.ScrollGlide;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
abstract class AbstractWidgetMixin {
	/** Moves a smooth scroll on once per frame, before the list draws. */
	@Inject(method = "render", at = @At("HEAD"))
	private void justaclient$glide(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (this instanceof ScrollGlide glide) {
			glide.justaclient$step();
		}
	}

	@Inject(method = "playButtonClickSound", at = @At("HEAD"), cancellable = true)
	private static void justaclient$click(SoundManager sounds, CallbackInfo ci) {
		if (GuiSoundsFeature.click(sounds)) {
			ci.cancel();
		}
	}
}
