package dev.justaclient.common.cosmetics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.justaclient.common.cosmetics.CapeLayout.Texture;
import org.junit.jupiter.api.Test;

class CapeLayoutTest {
	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;

	/** Left half red, right half blue. */
	private static int[] halves(int w, int h) {
		int[] pic = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				pic[y * w + x] = x < w / 2 ? RED : BLUE;
			}
		}
		return pic;
	}

	@Test
	void capeTexturesAreUsedAsTheyAre() {
		int[] tex = new int[128 * 64];
		tex[5] = 0x80123456;
		Texture t = CapeLayout.build(tex, 128, 64);
		assertFalse(t.fitted());
		assertEquals(128, t.width());
		assertEquals(64, t.height());
		assertArrayEquals(tex, t.argb());
		assertNotSame(tex, t.argb(), "a copy, so later edits to the source do not leak in");
	}

	@Test
	void optifineCapesArePadded() {
		for (int s = 1; s <= 2; s++) {
			int[] of = new int[46 * s * 22 * s];
			of[of.length - 1] = 0xFF00FF00;
			Texture t = CapeLayout.build(of, 46 * s, 22 * s);
			assertFalse(t.fitted());
			assertEquals(64 * s, t.width());
			assertEquals(32 * s, t.height());
			assertEquals(0xFF00FF00, t.at(46 * s - 1, 22 * s - 1));
			assertEquals(0, t.at(46 * s, 0));
			assertEquals(0, t.at(0, 22 * s));
		}
	}

	@Test
	void smallOrOddPicturesAreFitted() {
		assertTrue(CapeLayout.build(halves(32, 16), 32, 16).fitted(), "too small to be a cape texture");
		assertTrue(CapeLayout.build(halves(46, 23), 46, 23).fitted());
		Texture t = CapeLayout.build(halves(20, 32), 20, 32);
		assertTrue(t.fitted());
		assertEquals(128, t.width());
		assertEquals(64, t.height());
	}

	@Test
	void fittedScaleFollowsHeightAndIsCapped() {
		assertEquals(64, CapeLayout.build(halves(3, 3), 3, 3).width());
		assertEquals(64 * 4, CapeLayout.build(halves(40, 64), 40, 64).width());
		assertEquals(64 * CapeLayout.MAX_SCALE, CapeLayout.build(halves(10, 2000), 10, 2000).width());
	}

	@Test
	void fittedFacesFrontMirrorAndEdges() {
		Texture t = CapeLayout.build(halves(20, 32), 20, 32);
		int s = 2;
		// Outside face of the cape, from (1, 1) to (11, 17).
		assertEquals(RED, t.at(s, s));
		assertEquals(BLUE, t.at(11 * s - 1, 17 * s - 1));
		// Inside: mirrored and darker.
		assertEquals(0xFFA50000, t.at(22 * s - 1, s));
		assertEquals(0xFF0000A5, t.at(12 * s, s));
		// Sides take the nearest edge column, top and bottom the edge rows.
		assertEquals(RED, t.at(0, 5 * s));
		assertEquals(BLUE, t.at(11 * s, 5 * s));
		assertEquals(RED, t.at(s, 0));
		assertEquals(BLUE, t.at(21 * s - 1, 0));
		// The elytra wing at (22, 0), 2 deep.
		assertEquals(RED, t.at(24 * s, 2 * s));
		assertEquals(BLUE, t.at(34 * s - 1, 2 * s));
	}

	@Test
	void fittedPixelsAreOpaque() {
		int[] pic = new int[16 * 16];
		Texture t = CapeLayout.build(pic, 16, 16);
		assertEquals(0xFF000000, t.at(1, 1));
		assertEquals(0, t.at(63, 31), "unused parts stay clear");
	}

	@Test
	void darkerKeepsAlpha() {
		assertEquals(0x80A5A5A5, CapeLayout.darker(0x80FFFFFF));
		assertEquals(0, CapeLayout.darker(0));
	}

	@Test
	void coverCropsEvenly() {
		// A 4 x 1 strip covering 1 x 1 keeps the middle.
		int[] strip = {1, 2, 3, 4};
		assertArrayEquals(new int[] {3 | 0xFF000000}, CapeLayout.cover(strip, 4, 1, 1, 1));
		int[] up = CapeLayout.cover(new int[] {1, 2}, 2, 1, 4, 2);
		assertArrayEquals(new int[] {1, 1, 2, 2, 1, 1, 2, 2}, java.util.Arrays.stream(up).map(c -> c & 0xFFFFFF).toArray());
	}
}
