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
	/** A combo ends when you are hit or after this long without landing one. */
	private static final long COMBO_NANOS = 3_000_000_000L;
	private static float pendingReach = -1f;
	private static float lastReach = -1f;
	private static long lastReachAt;
	private static int combo;
	private static int bestCombo;
	private static int hits;

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

	/** Distance from your eyes to the nearest point of the last entity you hit in melee, or -1 before the first hit. */
	public static float lastReach() {
		return lastReach;
	}

	/** Seconds since {@link #lastReach} was measured. */
	public static float secondsSinceReach() {
		return lastReachAt == 0 ? 1e6f : (System.nanoTime() - lastReachAt) / 1e9f;
	}

	/** Hits in a row without being hit back. */
	public static int combo() {
		return System.nanoTime() - lastHit > COMBO_NANOS ? 0 : combo;
	}

	public static int bestCombo() {
		return bestCombo;
	}

	/** Hits landed since joining. */
	public static int hits() {
		return hits;
	}

	public static void resetSession() {
		combo = 0;
		bestCombo = 0;
		hits = 0;
		lastReach = -1f;
		lastReachAt = 0;
	}

	public static void onAttack(Entity entity) {
		LocalPlayer self = Minecraft.getInstance().player;
		if (self != null) {
			// Measured from the eyes to the closest point of the hitbox, the same way reach is checked.
			pendingReach = (float) Math.sqrt(entity.getBoundingBox().distanceToSqr(self.getEyePosition()));
		}
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
		if (player != null && entity == player) {
			combo = 0;
		}
		if (player != null && causeId == player.getId() && entity != player && entity instanceof LivingEntity living) {
			if (living != target) {
				reportedDeath = false;
			}
			target = living;
			lastAttack = now;
			boolean melee = directId == causeId;
			if (!melee) {
				// A projectile hit: there was no swing, so pulse the crosshair now as a hit marker.
				Features.get(CrosshairFeature.class).onAttack();
			}
			hit(living, now, melee);
		} else if (causeId < 0 && entity == target && now - lastAttack < HIT_WINDOW_NANOS) {
			// No source sent (some servers strip it): fall back to timing against the last swing.
			hit(target, now, true);
		}
	}

	private static void hit(LivingEntity entity, long now, boolean melee) {
		combo = now - lastHit > COMBO_NANOS ? 1 : combo + 1;
		bestCombo = Math.max(bestCombo, combo);
		hits++;
		if (melee && pendingReach >= 0f) {
			lastReach = pendingReach;
			lastReachAt = now;
			pendingReach = -1f;
		}
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
