package dev.justaclient.anim;

import dev.justaclient.config.JacConfig;

/**
 * Critically-damped-ish spring for values that follow a moving target (sliders, hover glow, dragged widgets).
 * Call {@link #update()} once per frame before reading {@link #get()}.
 */
public final class Spring {
	private final float stiffness;
	private final float damping;
	private float value;
	private float velocity;
	private float target;
	private long lastNanos = -1;

	public Spring(float stiffness, float damping, float initial) {
		this.stiffness = stiffness;
		this.damping = damping;
		this.value = initial;
		this.target = initial;
	}

	/** A responsive default tuned for UI: settles in roughly 250 ms. */
	public static Spring ui(float initial) {
		return new Spring(220f, 26f, initial);
	}

	public void setTarget(float target) {
		this.target = target;
	}

	public void snap(float value) {
		this.value = value;
		this.target = value;
		this.velocity = 0f;
	}

	public float update() {
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		float speed = (float) JacConfig.get().style.motion.scale;
		if (speed <= 0f) {
			snap(target);
			return value;
		}
		// Faster motion settings shorten the simulated time constant.
		dt /= speed;
		// Semi-implicit Euler in a few substeps keeps it stable at low frame rates.
		int steps = 4;
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			float force = stiffness * (target - value) - damping * velocity;
			velocity += force * h;
			value += velocity * h;
		}
		if (Math.abs(target - value) < 1e-4f && Math.abs(velocity) < 1e-3f) {
			value = target;
			velocity = 0f;
		}
		return value;
	}

	public float get() {
		return value;
	}
}
