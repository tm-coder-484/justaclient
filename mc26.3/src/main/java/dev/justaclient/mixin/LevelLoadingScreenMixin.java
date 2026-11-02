package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.LoadingScreenFeature;
import dev.justaclient.render.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the loading card in place of vanilla's chunk map. Nether and End portal backgrounds are left as they are. */
@Mixin(LevelLoadingScreen.class)
abstract class LevelLoadingScreenMixin extends Screen {
	@Shadow
	private LevelLoadTracker loadTracker;
	@Shadow
	private LevelLoadingScreen.Reason reason;

	private LevelLoadingScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void justaclient$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		LoadingScreenFeature feature = Features.get(LoadingScreenFeature.class);
		if (feature == null || !feature.isEnabled()) {
			return;
		}
		String label = Component.translatable("multiplayer.downloadingTerrain").getString();
		feature.draw(g, width, height, label, loadTracker.hasProgress(), loadTracker.serverProgress());
		ci.cancel();
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void justaclient$background(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		LoadingScreenFeature feature = Features.get(LoadingScreenFeature.class);
		if (feature == null || !feature.isEnabled() || reason != LevelLoadingScreen.Reason.OTHER) {
			return;
		}
		extractPanorama(g, partialTick);
		Draw.glass(g, 0, 0, width, height, 0, 0x40101218, 1f);
		ci.cancel();
	}
}
