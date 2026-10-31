package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.CleanWorldFeature;
import dev.justaclient.feature.impl.FriendsFeature;
import dev.justaclient.feature.impl.TiersFeature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	/** Clean world: no fire drawn on burning entities. */
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;displayFireAnimation()Z"))
	private boolean justaclient$fire(boolean burning) {
		return burning && !CleanWorldFeature.hidesEntityFire();
	}

	/** Friends' name tags in the friend colour. */
	@ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
	private Component justaclient$friendTag(Component tag, Entity entity) {
		if (tag != null && entity instanceof Player player) {
			Component tinted = FriendsFeature.get().tint(tag, player.getGameProfile().name(), true);
			return TiersFeature.get().decorate(tinted, player.getUUID(), true);
		}
		return tag;
	}
}
