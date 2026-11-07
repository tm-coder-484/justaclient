package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ScreenBackgroundFeature;
import dev.justaclient.render.SpatialMenus;
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
	protected abstract void renderPanorama(GuiGraphics g, float partialTick);

	@Shadow
	protected abstract void renderBlurredBackground(float partialTick);

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void justaclient$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (SpatialMenus.drawing()) {
			// The menu floats: the world shows around it (dimmed flat behind it by Spatial menus), so no background.
			
			ci.cancel();
			return;
		}
		ScreenBackgroundFeature feature = Features.get(ScreenBackgroundFeature.class);
		if (feature == null) {
			return;
		}
		Screen self = (Screen) (Object) this;
		if (feature.draw(self, g, minecraft.level != null, mouseX, mouseY, () -> renderPanorama(g, partialTick), () -> renderBlurredBackground(partialTick))) {
			
			ci.cancel();
		}
	}
}
