package dev.justaclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.feature.impl.CombatTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	/** The server confirmed damage to an entity; hit effects only play for hits the server accepted. The packet says who caused it. */
	@Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V"))
	private void justaclient$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci, @Local Entity entity) {
		CombatTracker.onDamage(entity, packet.sourceCauseId(), packet.sourceDirectId());
	}

	/** Entity event 35 is a totem of undying popping; this runs where vanilla starts its totem particles. */
	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
	private void justaclient$onTotem(ClientboundEntityEventPacket packet, CallbackInfo ci, @Local Entity entity) {
		CombatTracker.onTotem(entity);
	}
}
