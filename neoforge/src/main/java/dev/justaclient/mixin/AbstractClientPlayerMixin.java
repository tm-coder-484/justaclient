package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.CapeFeature;
import dev.justaclient.feature.impl.SkinFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	/** Skin swap and custom cape: only your own player, only on your screen. */
	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private PlayerSkin justaclient$skin(PlayerSkin skin) {
		return (Object) this == Minecraft.getInstance().player ? CapeFeature.apply(SkinFeature.apply(skin)) : skin;
	}
}
