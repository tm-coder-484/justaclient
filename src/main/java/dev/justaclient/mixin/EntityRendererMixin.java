package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.FriendsFeature;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	/** Friends' name tags in the friend colour. */
	@ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
	private Component justaclient$friendTag(Component tag, Entity entity) {
		if (tag != null && entity instanceof Player player) {
			return FriendsFeature.get().tint(tag, player.getGameProfile().name(), true);
		}
		return tag;
	}
}
