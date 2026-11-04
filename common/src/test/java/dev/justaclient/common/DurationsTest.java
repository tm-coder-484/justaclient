package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {
	@Test
	void clockUnderAnHour() {
		assertEquals("0:00", Durations.clock(0));
		assertEquals("0:59", Durations.clock(59_999));
		assertEquals("4:05", Durations.clock(245_000));
		assertEquals("59:59", Durations.clock(3_599_999));
	}

	@Test
	void clockOverAnHourShowsHours() {
		assertEquals("1:00:00", Durations.clock(3_600_000));
		assertEquals("1:04:05", Durations.clock(3_845_000));
		assertEquals("25:00:00", Durations.clock(90_000_000));
	}

	@Test
	void negativeTimesShowAsZero() {
		assertEquals("0:00", Durations.clock(-5000));
		assertEquals("0s", Durations.words(-5000));
	}

	@Test
	void words() {
		assertEquals("12s", Durations.words(12_500));
		assertEquals("1m 0s", Durations.words(60_000));
		assertEquals("4m 5s", Durations.words(245_000));
		assertEquals("1h 4m", Durations.words(3_845_000));
	}

	@Test
	void ticksAreTwentyPerSecond() {
		assertEquals("1:30", Durations.ticks(1800));
		assertEquals("0:00", Durations.ticks(19));
		assertEquals("0:01", Durations.ticks(20));
	}
}
