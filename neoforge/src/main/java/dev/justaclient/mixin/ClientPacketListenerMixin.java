package dev.justaclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.justaclient.feature.impl.CombatTracker;
import dev.justaclient.hud.PickupFeed;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Shadow;
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
	@Shadow
	private ClientLevel level;

	/** Something was picked up; runs on the game thread, before the item entity is removed. */
	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;", ordinal = 0))
	private void justaclient$onTake(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Entity taken = level.getEntity(packet.getItemId());
		if (taken != null) {
			PickupFeed.onTake(taken, packet.getPlayerId(), packet.getAmount());
		}
	}

	/** The server confirmed damage to an entity; hit effects only play for hits the server accepted. The packet says who caused it. */
	@Inject(method = "handleDamageEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V"))
	private void justaclient$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci, @Local Entity entity) {
		CombatTracker.onDamage(entity, packet.sourceCauseId(), packet.sourceDirectId());
		dev.justaclient.feature.impl.DeathRecapFeature.onHurt(entity, packet.sourceType().unwrapKey().map(k -> k.location().toString()).orElse(""),
			packet.sourceCauseId(), packet.sourceDirectId());
	}

	/** Entity event 35 is a totem of undying popping; this runs where vanilla starts its totem particles. */
	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"))
	private void justaclient$onTotem(ClientboundEntityEventPacket packet, CallbackInfo ci, @Local Entity entity) {
		CombatTracker.onTotem(entity);
	}
}
