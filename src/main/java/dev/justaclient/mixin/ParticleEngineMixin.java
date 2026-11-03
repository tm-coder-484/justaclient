package dev.justaclient.mixin;

import dev.justaclient.feature.impl.CleanWorldFeature;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clean world's "Hide explosion particles" and "No particles". */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void justaclient$skipParticle(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
		CallbackInfoReturnable<Particle> cir) {
		if (CleanWorldFeature.hidesAllParticles()
			|| CleanWorldFeature.hidesExplosions() && (options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
			cir.setReturnValue(null);
		}
	}

	/** Block breaking and other effects add their particles here directly. */
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void justaclient$skipAdd(Particle particle, CallbackInfo ci) {
		if (CleanWorldFeature.hidesAllParticles()) {
			ci.cancel();
		}
	}

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"),
		cancellable = true)
	private void justaclient$skipEmitter(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
		if (CleanWorldFeature.hidesAllParticles()) {
			ci.cancel();
		}
	}

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"),
		cancellable = true)
	private void justaclient$skipEmitterShort(Entity entity, ParticleOptions options, CallbackInfo ci) {
		if (CleanWorldFeature.hidesAllParticles()) {
			ci.cancel();
		}
	}
}
