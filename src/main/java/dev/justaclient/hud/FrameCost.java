package dev.justaclient.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rough, honest measure of what Justaclient costs: CPU time spent in its HUD, world and menu hooks as a share of the
 * frame time, smoothed over about a second. GPU time is not included.
 * <p>
 * Time is booked against named sections (usually one per feature) so the Performance page can show where it goes.
 * Sections must not nest; each one is timed on its own and the total is their sum.
 */
public final class FrameCost {
	/** The release target from the roadmap: everything on should stay under this share of the frame. */
	public static final float BUDGET = 0.05f;
	private static final float SMOOTHING = 0.03f;
	private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();
	private static long lastFrame = -1;
	private static float avgFrame = 16_000_000f;
	private static float avgSpent;
	private static Section running;

	private FrameCost() {
	}

	/** One named bucket of time, e.g. a feature. */
	public static final class Section {
		public final String name;
		private long started;
		private long spentThisFrame;
		private float avg;
		private float peak;

		private Section(String name) {
			this.name = name;
		}

		public void begin() {
			started = System.nanoTime();
			running = this;
		}

		public void end() {
			spentThisFrame += System.nanoTime() - started;
			running = null;
		}

		/** Smoothed nanoseconds per frame. */
		public float averageNanos() {
			return avg;
		}

		/** Highest single frame in the last few seconds, decaying slowly. */
		public float peakNanos() {
			return peak;
		}

		public float share() {
			return avgFrame <= 0 ? 0f : avg / avgFrame;
		}
	}

	public static Section section(String name) {
		return SECTIONS.computeIfAbsent(name, Section::new);
	}

	/** Times a block of work against {@code section}. */
	public static void time(Section section, Runnable work) {
		section.begin();
		try {
			work.run();
		} finally {
			section.end();
		}
	}

	/** True while a section is being timed, so callers can avoid starting a nested one. */
	public static boolean busy() {
		return running != null;
	}

	/** Called once per frame from the HUD pass. */
	public static void frame() {
		long now = System.nanoTime();
		long spent = 0;
		for (Section s : SECTIONS.values()) {
			spent += s.spentThisFrame;
			if (lastFrame > 0) {
				s.avg += (s.spentThisFrame - s.avg) * SMOOTHING;
				s.peak = Math.max(s.spentThisFrame, s.peak * 0.995f);
			}
			s.spentThisFrame = 0;
		}
		if (lastFrame > 0) {
			avgFrame += (now - lastFrame - avgFrame) * SMOOTHING;
			avgSpent += (spent - avgSpent) * SMOOTHING;
		}
		lastFrame = now;
	}

	/** Share of the frame, 0..1. */
	public static float share() {
		return avgFrame <= 0 ? 0f : Math.min(1f, avgSpent / avgFrame);
	}

	/** Smoothed frame time in nanoseconds. */
	public static float frameNanos() {
		return avgFrame;
	}

	/** Every section that has cost anything, most expensive first. */
	public static List<Section> sections() {
		List<Section> list = new ArrayList<>();
		for (Section s : SECTIONS.values()) {
			if (s.avg > 50f || s.peak > 1000f) {
				list.add(s);
			}
		}
		list.sort((a, b) -> Float.compare(b.avg, a.avg));
		return Collections.unmodifiableList(list);
	}
}
