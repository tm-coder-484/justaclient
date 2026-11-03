package dev.justaclient.common.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnvilMathTest {
	@Test
	void equalLevelsGoUpByOne() {
		assertEquals(4, AnvilMath.merged(3, 3, 5));
		assertEquals(1, AnvilMath.merged(0, 0, 1));
	}

	@Test
	void otherwiseTheHigherWins() {
		assertEquals(4, AnvilMath.merged(2, 4, 5));
		assertEquals(4, AnvilMath.merged(4, 2, 5));
		assertEquals(1, AnvilMath.merged(0, 1, 3));
	}

	@Test
	void resultIsCappedAtTheMaximum() {
		assertEquals(5, AnvilMath.merged(5, 5, 5));
		assertEquals(5, AnvilMath.merged(0, 10, 5));
		assertEquals(1, AnvilMath.merged(1, 1, 1));
	}

	@Test
	void improves() {
		assertTrue(AnvilMath.improves(4, 4, 5));
		assertTrue(AnvilMath.improves(0, 1, 1));
		assertTrue(AnvilMath.improves(1, 3, 5));
		assertFalse(AnvilMath.improves(4, 2, 5));
		assertFalse(AnvilMath.improves(5, 5, 5));
		assertFalse(AnvilMath.improves(3, 3, 3));
		// An over-levelled item (from commands) is never made better by the anvil.
		assertFalse(AnvilMath.improves(10, 10, 5));
	}

	@Test
	void priorWorkAddsBothPenalties() {
		// Vanilla penalties after n uses are 2^n - 1.
		assertEquals(0, AnvilMath.priorWork(0, 0));
		assertEquals(4, AnvilMath.priorWork(3, 1));
		assertEquals(38, AnvilMath.priorWork(31, 7));
		assertEquals(7, AnvilMath.priorWork(-5, 7));
	}

	@Test
	void fortyLevelsIsTooExpensive() {
		assertFalse(AnvilMath.tooExpensive(39));
		assertTrue(AnvilMath.tooExpensive(40));
		assertTrue(AnvilMath.tooExpensive(AnvilMath.priorWork(31, 15)));
		assertFalse(AnvilMath.tooExpensive(AnvilMath.priorWork(31, 7)));
	}
}
