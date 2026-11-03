package dev.justaclient.ui.widget;

/** A scroll position that glides to its target, for the client's lists. */
public final class Scroller {
	private float target;
	private float current;
	private float max;
	private long last = System.nanoTime();

	/** Sets how far the content can scroll, and keeps the position inside it. */
	public void setMax(float max) {
		this.max = Math.max(0f, max);
		target = clamp(target);
		current = Math.min(current, this.max);
	}

	public void scroll(double amount, float step) {
		target = clamp(target - (float) amount * step);
	}

	public void scrollTo(float value) {
		target = clamp(value);
	}

	/** Makes sure the band {@code top} to {@code bottom} (content coordinates) is in view in a window {@code view} tall. */
	public void reveal(float top, float bottom, float view) {
		if (top < target) {
			target = clamp(top);
		} else if (bottom > target + view) {
			target = clamp(bottom - view);
		}
	}

	public void snap() {
		current = target;
	}

	/** The position to draw with this frame. */
	public float get() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float k = 1f - (float) Math.exp(-dt * 18f);
		current += (target - current) * k;
		if (Math.abs(target - current) < 0.05f) {
			current = target;
		}
		return current;
	}

	public float max() {
		return max;
	}

	private float clamp(float v) {
		return Math.max(0f, Math.min(max, v));
	}
}
