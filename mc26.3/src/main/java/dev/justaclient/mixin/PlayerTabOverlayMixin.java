package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.FriendsFeature;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
	/** Friends in the tab list in the friend colour. */
	@ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
	private Component justaclient$friendName(Component name, PlayerInfo info) {
		return FriendsFeature.get().tint(name, info.getProfile().name(), false);
	}
}
