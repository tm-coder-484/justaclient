package dev.justaclient.feature.impl;

import dev.justaclient.feature.Features;
import dev.justaclient.hud.SessionStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Remembers who the player is fighting, from the player's own attacks and the server's damage and death events. It only
 * reacts to things vanilla already shows (the hit, the hurt flash, the death), so the effects built on it stay cosmetic.
 */
public final class CombatTracker {
	/** A damage event this soon after an attack counts as the player's hit. */
	private static final long HIT_WINDOW_NANOS = 600_000_000L;
	/** How long the target stays the target after the last hit. */
	private static final long TARGET_NANOS = 6_000_000_000L;
	private static @Nullable LivingEntity target;
	private static long lastAttack;
	private static long lastHit;
	private static boolean reportedDeath;

	private CombatTracker() {
	}

	/** The entity the player is fighting, if it is still around and was hit in the last few seconds. */
	public static @Nullable LivingEntity target() {
		LivingEntity t = target;
		if (t == null || t.isRemoved() && !t.isDeadOrDying() || System.nanoTime() - lastAttack > TARGET_NANOS
			|| t.level() != Minecraft.getInstance().level) {
			return null;
		}
		return t;
	}

	/** Seconds since the last confirmed hit on the target, or a large number. */
	public static float secondsSinceHit() {
		return lastHit == 0 ? 1e6f : (System.nanoTime() - lastHit) / 1e9f;
	}

	public static void onAttack(Entity entity) {
		if (entity instanceof LivingEntity living) {
			if (living != target) {
				reportedDeath = false;
			}
			target = living;
			lastAttack = System.nanoTime();
		}
		Features.get(SlashTrailFeature.class).onAttack();
	}

	/**
	 * The server says {@code entity} took damage. {@code causeId} is whoever dealt it and {@code directId} what touched
	 * the entity (the arrow, trident or the attacker itself); both are -1 when unknown. A hit caused by the local player
	 * counts wherever it came from, so arrows, tridents and other projectiles get the same feedback as a melee hit.
	 */
	public static void onDamage(Entity entity, int causeId, int directId) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		long now = System.nanoTime();
		if (player != null && causeId == player.getId() && entity != player && entity instanceof LivingEntity living) {
			if (living != target) {
				reportedDeath = false;
			}
			target = living;
			lastAttack = now;
			if (directId != causeId) {
				// A projectile hit: there was no swing, so pulse the crosshair now as a hit marker.
				Features.get(CrosshairFeature.class).onAttack();
			}
			hit(living, now);
		} else if (causeId < 0 && entity == target && now - lastAttack < HIT_WINDOW_NANOS) {
			// No source sent (some servers strip it): fall back to timing against the last swing.
			hit(target, now);
		}
	}

	private static void hit(LivingEntity entity, long now) {
		lastHit = now;
		Features.get(HitParticlesFeature.class).onHit(entity);
		Features.get(HitBubblesFeature.class).onHit(entity);
		Features.get(HitSoundsFeature.class).onHit();
	}

	/** Someone used a totem of undying. */
	public static void onTotem(Entity entity) {
		Features.get(TotemPopFeature.class).onPop(entity);
	}

	public static void tick() {
		LivingEntity t = target;
		if (t == null) {
			return;
		}
		if (t.level() != Minecraft.getInstance().level || System.nanoTime() - lastAttack > TARGET_NANOS) {
			target = null;
			return;
		}
		if (!reportedDeath && t.isDeadOrDying()) {
			reportedDeath = true;
			Features.get(KillEffectsFeature.class).onKill(t);
			SessionStats.onKill();
		}
	}
}
