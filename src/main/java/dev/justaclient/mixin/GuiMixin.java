package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.OverlaysFeature;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void justaclient$hideOverlay(GuiGraphics g, Identifier texture, float alpha, CallbackInfo ci) {
		if (Features.get(OverlaysFeature.class).hides(texture)) {
			ci.cancel();
		}
	}

	@ModifyExpressionValue(method = "renderCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float justaclient$nauseaOverlay(float nausea) {
		return Features.get(OverlaysFeature.class).hidesNausea() ? 0f : nausea;
	}

	@Inject(method = "renderFood", at = @At("TAIL"))
	private void justaclient$foodInfo(GuiGraphics g, Player player, int y, int right, CallbackInfo ci) {
		Features.get(dev.justaclient.feature.impl.FoodInfoFeature.class).drawHungerBar(g, player, y, right);
	}
}
