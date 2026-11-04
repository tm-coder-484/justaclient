package dev.justaclient.common.ui;

/**
 * The maths for fitting the client's screens and HUD into small GUIs: a big GUI scale or a small window. At GUI scale 4
 * on a 1080p screen the GUI is 480 by 270 units; the smallest the game allows is 320 by 240.
 */
public final class Fit {
	/** Screens never shrink below this: past it text gets too small to read. */
	public static final float MIN_SCREEN_SCALE = 0.55f;
	/** The GUI size the HUD's default layout is made for (GUI scale 3 on 1080p is 640 by 360). */
	public static final float HUD_REF_W = 600f;
	public static final float HUD_REF_H = 338f;
	public static final float MIN_HUD_SCALE = 0.6f;

	private Fit() {
	}

	/** How much to scale a screen that needs {@code minW} by {@code minH} into a window of {@code w} by {@code h}: at most 1. */
	public static float screenScale(float w, float h, float minW, float minH) {
		if (w <= 0 || h <= 0 || minW <= 0 || minH <= 0) {
			return 1f;
		}
		float s = Math.min(1f, Math.min(w / minW, h / minH));
		return Math.max(MIN_SCREEN_SCALE, s);
	}

	/** The canvas size for a window side of {@code size} drawn at {@code scale}: the canvas fills the window exactly. */
	public static int canvas(int size, float scale) {
		return scale >= 1f ? size : (int) Math.floor(size / scale);
	}

	/** The factor every HUD widget is drawn at on a GUI of {@code w} by {@code h} units. */
	public static float hudScale(float w, float h) {
		if (w <= 0 || h <= 0) {
			return 1f;
		}
		return Math.max(MIN_HUD_SCALE, Math.min(1f, Math.min(w / HUD_REF_W, h / HUD_REF_H)));
	}

	/**
	 * The scale for a panel {@code w} by {@code h} beside a container, with {@code room} across and {@code roomDown} below
	 * its top: 1 when it fits, never below {@code min}.
	 */
	public static float sidePanelScale(float w, float h, float room, float roomDown, float min) {
		float s = 1f;
		if (w > room) {
			s = room / w;
		}
		if (h * s > roomDown) {
			s = roomDown / h;
		}
		return Math.max(min, Math.min(1f, s));
	}

	/**
	 * The width for a menu panel {@code h} tall in a space of {@code availW} by {@code availH}: as wide as the space's shape
	 * allows without shrinking the panel any further, kept between {@code minW} and {@code maxW}.
	 */
	public static float menuWidth(float availW, float availH, float h, float minW, float maxW) {
		if (availW <= 0 || availH <= 0 || h <= 0) {
			return minW;
		}
		return Math.max(minW, Math.min(maxW, availW / availH * h));
	}
}
