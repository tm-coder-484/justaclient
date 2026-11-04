package dev.justaclient.common.spatial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpatialPanelTest {
	private static final double EPS = 1e-9;

	private static double[] hitPoint(SpatialPanel p, double u, double v, boolean extend) {
		double[] pt = p.point(u, v);
		return p.hit(pt[0], pt[1], pt[2], extend);
	}

	private static void roundTrips(SpatialPanel p) {
		double[][] uvs = {{0, 0}, {0.5, -0.25}, {-0.9, 0.9}, {0.999, -0.999}, {-0.999, 0.999}};
		for (double[] uv : uvs) {
			double[] back = hitPoint(p, uv[0], uv[1], false);
			assertNotNull(back, uv[0] + "," + uv[1]);
			assertArrayEquals(uv, back, 1e-7);
		}
	}

	@Test
	void flatPanelStraightAhead() {
		SpatialPanel p = SpatialPanel.place(2, 0, 1, 0.5, 0, 0, 0);
		assertEquals(0, p.radius);
		assertArrayEquals(new double[] {0, 0, -2}, p.point(0, 0), EPS);
		assertArrayEquals(new double[] {1, 0.5, -2}, p.point(1, 1), EPS);
		assertArrayEquals(new double[] {0, 0}, p.hit(0, 0, -1, false), EPS);
		roundTrips(p);
	}

	@Test
	void pointAndHitAreInversesForEveryShape() {
		roundTrips(SpatialPanel.place(2, 0.3, 1, 0.6, 20, 0, 0));
		roundTrips(SpatialPanel.place(2, 0, 1, 0.6, 0, 30, 0));
		roundTrips(SpatialPanel.place(0.4, 2, -0.2, 1, 0.6, 15, -25, 0));
		roundTrips(SpatialPanel.place(2, 0, 1, 0.6, 0, 0, 0.1));
		roundTrips(SpatialPanel.place(2, 0, 1, 0.6, 0, 0, 0.5));
		roundTrips(SpatialPanel.place(2, 0, 1, 0.6, 0, 0, 1));
		roundTrips(SpatialPanel.place(1.5, 0.2, 1.2, 0.5, 10, 20, 0.7));
	}

	@Test
	void leanAndTurnGoTheDocumentedWay() {
		SpatialPanel leaned = SpatialPanel.place(2, 0, 1, 1, 30, 0, 0);
		assertTrue(leaned.point(0, 1)[2] < -2, "top leans away");
		SpatialPanel turned = SpatialPanel.place(2, 0, 1, 1, 0, 30, 0);
		assertTrue(turned.point(1, 0)[2] < -2, "right edge turns away");
		SpatialPanel bent = SpatialPanel.place(2, 0, 1, 1, 0, 0, 1);
		assertTrue(bent.point(1, 0)[2] > -2, "a bent panel's edges come towards the viewer");
		assertTrue(bent.point(-1, 0)[2] > -2);
	}

	@Test
	void bendIsClamped() {
		SpatialPanel full = SpatialPanel.place(2, 0, 1, 1, 0, 0, 1);
		assertEquals(full.radius, SpatialPanel.place(2, 0, 1, 1, 0, 0, 5).radius, EPS);
		assertEquals(1 / (SpatialPanel.MAX_BEND / 2), full.radius, EPS);
		assertEquals(0, SpatialPanel.place(2, 0, 1, 1, 0, 0, -1).radius);
		assertEquals(0, SpatialPanel.place(2, 0, 1, 1, 0, 0, 1e-6).radius);
	}

	@Test
	void raysThatMiss() {
		SpatialPanel flat = SpatialPanel.place(2, 0, 1, 1, 0, 0, 0);
		assertNull(flat.hit(1, 0, 0, false), "parallel to the panel");
		assertNull(flat.hit(1, 0, 0, true), "parallel, even when extending");
		assertNull(flat.hit(0, 0, 1, true), "pointing away");
		assertNull(flat.hit(1, 0, -1, false), "past the edge");
		SpatialPanel bent = SpatialPanel.place(2, 0, 1, 1, 0, 0, 0.5);
		assertNull(bent.hit(0, 0, 1, false));
		assertNull(bent.hit(0, 5, -1, false));
	}

	@Test
	void extendKeepsDragsGoingPastTheEdge() {
		SpatialPanel flat = SpatialPanel.place(2, 0, 1, 1, 0, 0, 0);
		assertArrayEquals(new double[] {2, 0}, flat.hit(1, 0, -1, true), EPS);
		SpatialPanel bent = SpatialPanel.place(2, 0, 1, 1, 0, 0, 0.5);
		double[] past = bent.hit(0.8, 0, -1, true);
		assertNotNull(past);
		assertTrue(past[0] > 1, "still to the right: " + past[0]);
		double[] left = bent.hit(-0.8, 0, -1, true);
		assertTrue(left[0] < -1, "still to the left: " + left[0]);
	}

	@Test
	void guiConversionsAreInverses() {
		assertArrayEquals(new double[] {-1, 1}, SpatialPanel.guiToPanel(0, 0, 320, 240), EPS);
		assertArrayEquals(new double[] {1, -1}, SpatialPanel.guiToPanel(320, 240, 320, 240), EPS);
		assertArrayEquals(new double[] {0, 0}, SpatialPanel.guiToPanel(160, 120, 320, 240), EPS);
		double[] uv = SpatialPanel.guiToPanel(37, 201, 320, 240);
		assertArrayEquals(new double[] {37, 201}, SpatialPanel.panelToGui(uv[0], uv[1], 320, 240), EPS);
	}

	@Test
	void besideCenterKeepsGapsAndEdgeWins() {
		assertEquals(0.4, SpatialPanel.besideCenter(0, 0.3, 1), EPS);
		assertEquals(-0.4, SpatialPanel.besideCenter(0, 0.3, -1), EPS);
		// Not enough room: the panel stays 0.03 inside the screen edge.
		assertEquals(1 - 0.03 - 0.3, SpatialPanel.besideCenter(0.8, 0.3, 1), EPS);
		assertEquals(-1 + 0.03 + 0.3, SpatialPanel.besideCenter(-0.8, 0.3, -1), EPS);
	}

	@Test
	void rotateFollowsTheRightHandRule() {
		double[] r = SpatialPanel.rotate(new double[] {1, 0, 0}, new double[] {0, 1, 0}, Math.PI / 2);
		assertArrayEquals(new double[] {0, 0, -1}, r, EPS);
		assertArrayEquals(new double[] {0, 0, 1}, SpatialPanel.cross(new double[] {1, 0, 0}, new double[] {0, 1, 0}), EPS);
		assertEquals(0, SpatialPanel.dot(new double[] {1, 2, 3}, new double[] {3, 0, -1}), EPS);
	}
}
