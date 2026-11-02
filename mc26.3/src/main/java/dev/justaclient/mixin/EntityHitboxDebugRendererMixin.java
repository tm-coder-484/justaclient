package dev.justaclient.mixin;

import dev.justaclient.feature.impl.HitboxesFeature;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
abstract class EntityHitboxDebugRendererMixin {
	@Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
	private void justaclient$restyle(Entity entity, float partialTick, boolean server, CallbackInfo ci) {
		if (!server && HitboxesFeature.draw(entity, partialTick)) {
			ci.cancel();
		}
	}
}
