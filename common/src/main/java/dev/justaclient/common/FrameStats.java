package dev.justaclient.common;

import java.util.Arrays;

/**
 * Rolling frame times for the performance overlay: average FPS, the 1% low, how steady the pacing is, and the samples
 * for the frame-time graph. Plain Java, shared by both Minecraft versions.
 */
public final class FrameStats {
	private final long[] nanos;
	private int next;
	private int count;
	private long last = -1;
	private final long[] sorted;

	public FrameStats(int capacity) {
		nanos = new long[capacity];
		sorted = new long[capacity];
	}

	/** Records one frame at {@code now} (System.nanoTime()). */
	public void frame(long now) {
		if (last >= 0) {
			long dt = now - last;
			// Ignore pauses such as the window losing focus or a world loading.
			if (dt > 0 && dt < 2_000_000_000L) {
				nanos[next] = dt;
				next = (next + 1) % nanos.length;
				count = Math.min(count + 1, nanos.length);
			}
		}
		last = now;
	}

	public int count() {
		return count;
	}

	/** Frame time in milliseconds, {@code i} frames ago (0 = the latest). */
	public float millisAgo(int i) {
		if (i < 0 || i >= count) {
			return 0f;
		}
		int index = Math.floorMod(next - 1 - i, nanos.length);
		return nanos[index] / 1e6f;
	}

	public float averageMillis() {
		if (count == 0) {
			return 0f;
		}
		long sum = 0;
		for (int i = 0; i < count; i++) {
			sum += nanos[i];
		}
		return sum / (float) count / 1e6f;
	}

	public float averageFps() {
		float ms = averageMillis();
		return ms <= 0f ? 0f : 1000f / ms;
	}

	/** The FPS of the slowest 1% of frames. */
	public float onePercentLowFps() {
		if (count == 0) {
			return 0f;
		}
		System.arraycopy(nanos, 0, sorted, 0, count);
		Arrays.sort(sorted, 0, count);
		int worst = Math.max(1, count / 100);
		long sum = 0;
		for (int i = count - worst; i < count; i++) {
			sum += sorted[i];
		}
		float ms = sum / (float) worst / 1e6f;
		return ms <= 0f ? 0f : 1000f / ms;
	}

	/** 0..1, how evenly frames arrive: 1 when every frame takes the same time, lower as frame times swing. */
	public float stability() {
		if (count < 2) {
			return 1f;
		}
		double mean = 0;
		for (int i = 0; i < count; i++) {
			mean += nanos[i];
		}
		mean /= count;
		double var = 0;
		for (int i = 0; i < count; i++) {
			double d = nanos[i] - mean;
			var += d * d;
		}
		double cv = Math.sqrt(var / count) / Math.max(1.0, mean);
		return (float) Math.max(0.0, Math.min(1.0, 1.0 - cv));
	}

	/** The longest frame in the window, in milliseconds. */
	public float worstMillis() {
		long worst = 0;
		for (int i = 0; i < count; i++) {
			worst = Math.max(worst, nanos[i]);
		}
		return worst / 1e6f;
	}
}
