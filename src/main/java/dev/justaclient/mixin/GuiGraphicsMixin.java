package dev.justaclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ItemOverlayFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import dev.justaclient.render.ScreenSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
	@Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"),
		cancellable = true)
	private void justaclient$skinSprite(RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, int color, CallbackInfo ci) {
		if (ScreenSkin.sprite((GuiGraphics) (Object) this, id, x, y, w, h, color)) {
			ci.cancel();
		}
	}

	@Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"),
		cancellable = true)
	private void justaclient$skinTexture(RenderPipeline pipeline, Identifier id, int x, int y, float u, float v, int w, int h, int srcW, int srcH,
		int texW, int texH, int color, CallbackInfo ci) {
		if (ScreenSkin.texture((GuiGraphics) (Object) this, id, x, y, w, h)) {
			ci.cancel();
		}
	}

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
