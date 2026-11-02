package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.CrosshairFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	/** Lets the crosshair pulse when the player lands a hit. */
	@Inject(method = "attack", at = @At("HEAD"))
	private void justaclient$onAttack(Player player, Entity target, CallbackInfo ci) {
		Features.get(CrosshairFeature.class).onAttack();
	}
}
