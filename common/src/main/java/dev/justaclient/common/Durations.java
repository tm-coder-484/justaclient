package dev.justaclient.common;

/** Short time and number formats used across the HUD. Plain Java, shared by both Minecraft versions. */
public final class Durations {
	private Durations() {
	}

	/** "4:05" under an hour, "1:04:05" over. */
	public static String clock(long millis) {
		long s = Math.max(0, millis / 1000);
		long h = s / 3600;
		long m = s / 60 % 60;
		long sec = s % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
	}

	/** "12s", "4m 5s", "1h 4m". */
	public static String words(long millis) {
		long s = Math.max(0, millis / 1000);
		if (s < 60) {
			return s + "s";
		}
		if (s < 3600) {
			return s / 60 + "m " + s % 60 + "s";
		}
		return s / 3600 + "h " + s / 60 % 60 + "m";
	}

	/** Game ticks as a short countdown, e.g. "1:30" for 1800 ticks. */
	public static String ticks(int ticks) {
		return clock(ticks * 50L);
	}
}
