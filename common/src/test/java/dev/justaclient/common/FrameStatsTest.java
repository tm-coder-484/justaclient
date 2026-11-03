package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameStatsTest {
	private static final long MS = 1_000_000L;

	private static FrameStats steady(int capacity, int frames, long frameNanos) {
		FrameStats s = new FrameStats(capacity);
		long t = 1000;
		for (int i = 0; i <= frames; i++) {
			s.frame(t);
			t += frameNanos;
		}
		return s;
	}

	@Test
	void emptyStatsAreZeroAndSteady() {
		FrameStats s = new FrameStats(10);
		s.frame(5);
		assertEquals(0, s.count(), "the first frame only sets the clock");
		assertEquals(0f, s.averageFps());
		assertEquals(0f, s.onePercentLowFps());
		assertEquals(1f, s.stability());
		assertEquals(0f, s.worstMillis());
		assertEquals(0f, s.millisAgo(0));
	}

	@Test
	void evenFramesGiveExactFpsAndFullStability() {
		FrameStats s = steady(100, 50, 10 * MS);
		assertEquals(50, s.count());
		assertEquals(10f, s.averageMillis(), 1e-4);
		assertEquals(100f, s.averageFps(), 1e-3);
		assertEquals(100f, s.onePercentLowFps(), 1e-3);
		assertEquals(1f, s.stability(), 1e-6);
	}

	@Test
	void longPausesAndClockGoingBackAreIgnored() {
		FrameStats s = new FrameStats(10);
		s.frame(0);
		s.frame(10 * MS);
		s.frame(10 * MS + 3_000_000_000L);
		s.frame(10 * MS + 3_000_000_000L);
		s.frame(5 * MS);
		assertEquals(1, s.count());
		assertEquals(10f, s.worstMillis(), 1e-4);
	}

	@Test
	void millisAgoCountsBackFromTheLatestAcrossTheWrap() {
		FrameStats s = new FrameStats(3);
		long t = 0;
		s.frame(t);
		for (int ms : new int[] {1, 2, 3, 4, 5}) {
			t += ms * MS;
			s.frame(t);
		}
		assertEquals(3, s.count());
		assertEquals(5f, s.millisAgo(0), 1e-4);
		assertEquals(4f, s.millisAgo(1), 1e-4);
		assertEquals(3f, s.millisAgo(2), 1e-4);
		assertEquals(0f, s.millisAgo(3));
		assertEquals(0f, s.millisAgo(-1));
		assertEquals(4f, s.averageMillis(), 1e-4);
		assertEquals(5f, s.worstMillis(), 1e-4);
	}

	@Test
	void onePercentLowIsTheSlowestFrames() {
		FrameStats s = new FrameStats(200);
		long t = 0;
		s.frame(t);
		for (int i = 0; i < 199; i++) {
			t += 10 * MS;
			s.frame(t);
		}
		t += 100 * MS;
		s.frame(t);
		assertEquals(200, s.count());
		// The worst 2 of 200 frames: 100 ms and 10 ms, so 55 ms on average.
		assertEquals(1000f / 55f, s.onePercentLowFps(), 1e-2);
		assertEquals(100f, s.worstMillis(), 1e-4);
		assertTrue(s.averageFps() > s.onePercentLowFps());
	}

	@Test
	void unevenFramesLowerStability() {
		FrameStats s = new FrameStats(100);
		long t = 0;
		s.frame(t);
		for (int i = 0; i < 50; i++) {
			t += (i % 2 == 0 ? 5 : 25) * MS;
			s.frame(t);
		}
		float stability = s.stability();
		assertTrue(stability < 0.5f && stability >= 0f, String.valueOf(stability));
	}
}
