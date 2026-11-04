package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StopwatchTest {
	@Test
	void aNewStopwatchIsStoppedAtZero() {
		Stopwatch w = new Stopwatch();
		assertFalse(w.running());
		assertEquals(0, w.elapsedMillis(1000));
	}

	@Test
	void pausingBanksTheTimeAndResumingAddsToIt() {
		Stopwatch w = new Stopwatch();
		w.start(1000);
		assertEquals(500, w.elapsedMillis(1500));
		w.pause(2000);
		assertFalse(w.running());
		assertEquals(1000, w.elapsedMillis(9999));
		w.start(5000);
		assertEquals(1250, w.elapsedMillis(5250));
	}

	@Test
	void startingTwiceDoesNotRestart() {
		Stopwatch w = new Stopwatch();
		w.start(1000);
		w.start(1800);
		assertEquals(1000, w.elapsedMillis(2000));
	}

	@Test
	void startAtTimeZeroCountsAsRunning() {
		Stopwatch w = new Stopwatch();
		w.start(0);
		assertTrue(w.running());
		assertEquals(10, w.elapsedMillis(10));
	}

	@Test
	void toggleSwitchesBetweenRunningAndPaused() {
		Stopwatch w = new Stopwatch();
		w.toggle(100);
		assertTrue(w.running());
		w.toggle(400);
		assertFalse(w.running());
		assertEquals(300, w.elapsedMillis(1000));
	}

	@Test
	void resetKeepsRunningFromNowOrStaysPaused() {
		Stopwatch running = new Stopwatch();
		running.start(0);
		running.reset(5000);
		assertTrue(running.running());
		assertEquals(100, running.elapsedMillis(5100));

		Stopwatch paused = new Stopwatch();
		paused.start(0);
		paused.pause(700);
		paused.reset(1000);
		assertFalse(paused.running());
		assertEquals(0, paused.elapsedMillis(5000));
	}
}
