package dev.justaclient.hud;

/**
 * A rough, honest measure of what Justaclient costs: CPU time spent in its HUD and world hooks as a share of the frame
 * time, smoothed over about a second. GPU time is not included.
 */
public final class FrameCost {
	private static final float SMOOTHING = 0.03f;
	private static long lastFrame = -1;
	private static long spentThisFrame;
	private static long started;
	private static float avgFrame = 16_000_000f;
	private static float avgSpent;

	private FrameCost() {
	}

	public static void begin() {
		started = System.nanoTime();
	}

	public static void end() {
		spentThisFrame += System.nanoTime() - started;
	}

	/** Called once per frame from the HUD pass. */
	public static void frame() {
		long now = System.nanoTime();
		if (lastFrame > 0) {
			avgFrame += (now - lastFrame - avgFrame) * SMOOTHING;
			avgSpent += (spentThisFrame - avgSpent) * SMOOTHING;
		}
		lastFrame = now;
		spentThisFrame = 0;
	}

	/** Share of the frame, 0..1. */
	public static float share() {
		return avgFrame <= 0 ? 0f : Math.min(1f, avgSpent / avgFrame);
	}
}
