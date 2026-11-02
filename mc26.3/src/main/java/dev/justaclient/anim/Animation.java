package dev.justaclient.anim;

import dev.justaclient.config.JacConfig;

/**
 * A float that eases towards a target over a fixed duration, driven by wall-clock time so it is frame-rate independent.
 * Duration is scaled by the player's motion setting; with motion off, values jump straight to the target.
 */
public final class Animation {
	private final Easing easing;
	private final long baseDurationMs;
	private float from;
	private float to;
	private long startNanos;

	public Animation(Easing easing, long durationMs, float initial) {
		this.easing = easing;
		this.baseDurationMs = durationMs;
		this.from = initial;
		this.to = initial;
		this.startNanos = System.nanoTime();
	}

	/** Starts easing from the current value to {@code target}. Calling with the current target does nothing. */
	public Animation animateTo(float target) {
		if (target != to) {
			from = get();
			to = target;
			startNanos = System.nanoTime();
		}
		return this;
	}

	/** Jumps to {@code value} with no transition. */
	public Animation snap(float value) {
		from = value;
		to = value;
		startNanos = System.nanoTime();
		return this;
	}

	public float get() {
		float t = progress();
		return from + (to - from) * easing.apply(t);
	}

	public float target() {
		return to;
	}

	public boolean isDone() {
		return progress() >= 1f;
	}

	private float progress() {
		long duration = Math.round(baseDurationMs * JacConfig.get().style.motion.scale);
		if (duration <= 0) {
			return 1f;
		}
		float t = (System.nanoTime() - startNanos) / 1_000_000f / duration;
		return Math.min(1f, Math.max(0f, t));
	}
}
