package dev.justaclient.common.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudSnapTest {
	private static final float MARGIN = 4f;
	private static final float GAP = 2f;
	private static final float RANGE = 4f;

	private static float[] snap(float pos, float size, boolean centres, HudSnap.Span... others) {
		return HudSnap.snap(pos, size, 400, MARGIN, GAP, centres, List.of(others), RANGE);
	}

	@Test
	void farFromEverythingStaysPut() {
		float[] r = snap(120, 50, false);
		assertEquals(120f, r[0]);
		assertTrue(Float.isNaN(r[1]));
	}

	@Test
	void snapsInsideTheScreenMargins() {
		assertEquals(MARGIN, snap(6, 50, false)[0]);
		assertEquals(400 - MARGIN - 50, snap(344, 50, false)[0]);
		assertEquals(400 - MARGIN, snap(344, 50, false)[1]);
	}

	@Test
	void centreOnlyWhenAsked() {
		assertEquals(176f, snap(176, 50, false)[0]);
		assertEquals(175f, snap(176, 50, true)[0]);
		assertEquals(200f, snap(176, 50, true)[1]);
	}

	@Test
	void alignsWithAndSitsBesideOtherWidgets() {
		HudSnap.Span other = new HudSnap.Span(100, 40);
		// Same start, same end.
		assertEquals(100f, snap(102, 20, false, other)[0]);
		assertEquals(120f, snap(118, 20, false, other)[0]);
		assertEquals(140f, snap(118, 20, false, other)[1]);
		// Just after it with the gap, guide in the middle of the gap.
		float[] after = snap(141, 20, false, other);
		assertEquals(142f, after[0]);
		assertEquals(141f, after[1]);
		// Just before it.
		assertEquals(78f, snap(80, 20, false, other)[0]);
	}

	@Test
	void theNearestTargetWins() {
		HudSnap.Span a = new HudSnap.Span(100, 40);
		HudSnap.Span b = new HudSnap.Span(103, 40);
		assertEquals(103f, snap(104, 20, false, a, b)[0]);
		assertEquals(100f, snap(101, 20, false, a, b)[0]);
	}

	@Test
	void exactlyAtTheRangeDoesNotSnap() {
		assertEquals(8f, snap(8, 50, false)[0]);
	}
}
