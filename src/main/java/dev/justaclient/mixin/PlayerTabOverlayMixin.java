package dev.justaclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.justaclient.feature.impl.FriendsFeature;
import dev.justaclient.feature.impl.TiersFeature;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
	/** Friends in the tab list in the friend colour, and tier badges in front of names. */
	@ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
	private Component justaclient$friendName(Component name, PlayerInfo info) {
		Component tinted = FriendsFeature.get().tint(name, info.getProfile().name(), false);
		return TiersFeature.get().decorate(tinted, info.getProfile().id(), false);
	}
}
