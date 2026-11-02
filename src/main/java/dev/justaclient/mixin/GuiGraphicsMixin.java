package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ItemOverlayFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
	@Inject(method = "renderItemBar", at = @At("HEAD"), cancellable = true)
	private void justaclient$bar(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (Features.get(ItemOverlayFeature.class).drawBar((GuiGraphics) (Object) this, stack, x, y)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemCooldown", at = @At("HEAD"), cancellable = true)
	private void justaclient$cooldown(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (Features.get(ItemOverlayFeature.class).drawCooldown((GuiGraphics) (Object) this, stack, x, y)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemCount", at = @At("HEAD"), cancellable = true)
	private void justaclient$count(Font font, ItemStack stack, int x, int y, @Nullable String text, CallbackInfo ci) {
		if (Features.get(ItemOverlayFeature.class).drawCount((GuiGraphics) (Object) this, stack, x, y, text)) {
			ci.cancel();
		}
	}
}
