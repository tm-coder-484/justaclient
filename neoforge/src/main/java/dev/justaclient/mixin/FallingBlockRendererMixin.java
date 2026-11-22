package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class FallingBlockRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"),
		cancellable = true)
	private void justaclient$hide(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof FallingBlockEntity && CleanWorldFeature.hidesFallingBlocks()) {
			cir.setReturnValue(false);
		}
	}
}
