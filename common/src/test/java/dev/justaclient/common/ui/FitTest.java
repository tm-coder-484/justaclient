package dev.justaclient.common.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FitTest {
	@Test
	void bigWindowsAreNotScaled() {
		assertEquals(1f, Fit.screenScale(960, 540, 440, 260));
		assertEquals(1f, Fit.screenScale(480, 270, 440, 260), "GUI scale 4 on 1080p fits the default canvas");
	}

	@Test
	void smallWindowsScaleToTheTighterSide() {
		// 1280x960 at GUI scale 4.
		assertEquals(320f / 440f, Fit.screenScale(320, 240, 440, 260), 1e-6);
		// A short window: height decides.
		assertEquals(200f / 260f, Fit.screenScale(600, 200, 440, 260), 1e-6);
	}

	@Test
	void scaleHasAFloor() {
		assertEquals(Fit.MIN_SCREEN_SCALE, Fit.screenScale(100, 100, 1000, 1000));
		assertEquals(1f, Fit.screenScale(0, 0, 440, 260), "an unknown size is left alone");
	}

	@Test
	void canvasFillsTheWindow() {
		float s = Fit.screenScale(320, 240, 440, 260);
		int w = Fit.canvas(320, s);
		int h = Fit.canvas(240, s);
		assertEquals(440, w);
		assertTrue(h >= 260, "the canvas is at least as tall as needed: " + h);
		assertTrue(w * s <= 320.001f && h * s <= 240.001f, "and drawn scaled it stays inside the window");
		assertEquals(480, Fit.canvas(480, 1f));
	}

	@Test
	void hudShrinksOnlyOnSmallGuis() {
		assertEquals(1f, Fit.hudScale(960, 540));
		assertEquals(1f, Fit.hudScale(640, 360));
		float scale4 = Fit.hudScale(480, 270);
		assertTrue(scale4 > 0.75f && scale4 < 0.82f, "GUI scale 4 on 1080p: " + scale4);
		assertEquals(Fit.MIN_HUD_SCALE, Fit.hudScale(200, 150));
	}

	@Test
	void sidePanelsShrinkToTheRoomBesideTheContainer() {
		assertEquals(1f, Fit.sidePanelScale(120, 80, 150, 200, 0.6f));
		assertEquals(0.75f, Fit.sidePanelScale(120, 80, 90, 200, 0.6f), 1e-6);
		assertEquals(0.5f / 0.5f * 0.6f, Fit.sidePanelScale(120, 80, 30, 200, 0.6f), 1e-6);
		assertEquals(0.75f, Fit.sidePanelScale(100, 200, 500, 150, 0.6f), 1e-6, "too tall for the space below");
	}
}
