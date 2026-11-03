package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ScreenBackgroundFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class ScreenMixin {
	@Shadow
	protected Minecraft minecraft;

	@Shadow
	public abstract boolean isInGameUi();

	@Shadow
	protected abstract void renderPanorama(GuiGraphics g, float partialTick);

	@Shadow
	protected abstract void renderBlurredBackground(GuiGraphics g);

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void justaclient$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		ScreenBackgroundFeature feature = Features.get(ScreenBackgroundFeature.class);
		if (feature == null) {
			return;
		}
		Screen self = (Screen) (Object) this;
		if (feature.draw(self, g, isInGameUi(), mouseX, mouseY, () -> renderPanorama(g, partialTick), () -> renderBlurredBackground(g))) {
			minecraft.gui.renderDeferredSubtitles();
			ci.cancel();
		}
	}
}
