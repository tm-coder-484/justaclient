package dev.justaclient.mixin;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.BlockFoundFeature;
import dev.justaclient.feature.impl.CombatTracker;
import dev.justaclient.feature.impl.CrosshairFeature;
import dev.justaclient.hud.SessionStats;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	/** Lets the crosshair pulse when the player swings at something, and remembers what they are fighting. */
	@Inject(method = "attack", at = @At("HEAD"))
	private void justaclient$onAttack(Player player, Entity target, CallbackInfo ci) {
		Features.get(CrosshairFeature.class).onAttack();
		CombatTracker.onAttack(target);
	}

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void justaclient$beforeBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockFoundFeature.beforeBreak(pos);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void justaclient$onBlockBroken(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			SessionStats.onBlockBroken();
			BlockFoundFeature.afterBreak(pos);
		}
	}
}
