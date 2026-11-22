package dev.justaclient.ui;

import dev.justaclient.common.ui.Fit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import dev.justaclient.platform.input.MouseButtonEvent;

/**
 * Fitting the client's screens into small windows and big GUI scales. A screen made for at least {@code minW} by
 * {@code minH} GUI units gets a canvas that big and is drawn scaled down to the window, so its text and controls shrink a
 * little instead of overlapping or running off the edge. At GUI scale 4 on a 1080p screen the window is 480 by 270 units,
 * and the smallest the game allows is 320 by 240.
 */
public final class UiFit {
	private UiFit() {
	}

	/** How much to scale a screen that needs {@code minW} by {@code minH} into a window of {@code w} by {@code h}: at most 1. */
	public static float scale(float w, float h, float minW, float minH) {
		return Fit.screenScale(w, h, minW, minH);
	}

	/** The canvas size for a window side of {@code size} drawn at {@code scale}. */
	public static int canvas(int size, float scale) {
		return Fit.canvas(size, scale);
	}

	/**
	 * One screen's canvas. Call {@link #fit} first thing in the screen's {@code init()}: it swaps the screen's width and
	 * height for the canvas size. Wrap drawing in {@link #push}/{@link #pop} and pass mouse positions through {@link #in}.
	 */
	public static final class Canvas {
		private int windowW;
		private int windowH;
		private int canvasW = -1;
		private int canvasH = -1;
		private float scale = 1f;

		/** Fits {@code screen} for a layout that needs {@code minW} by {@code minH}; returns the scale. */
		public float fit(Screen screen, float minW, float minH) {
			// Screen.init(w, h) and resize(w, h) set the window's GUI size before init(); a plain rebuild leaves last
			// time's canvas size in place, so only a size that is not the canvas is a new window size.
			if (screen.width != canvasW || screen.height != canvasH) {
				windowW = screen.width;
				windowH = screen.height;
			}
			scale = UiFit.scale(windowW, windowH, minW, minH);
			screen.width = canvasW = canvas(windowW, scale);
			screen.height = canvasH = canvas(windowH, scale);
			return scale;
		}

		public float scale() {
			return scale;
		}

		/** A position in the window's GUI space, in canvas units. */
		public double in(double v) {
			return v / scale;
		}

		public int in(int v) {
			return (int) Math.floor(v / scale);
		}

		/** Starts drawing in canvas units; returns whether {@link #pop} must be called. */
		public boolean push(GuiGraphics g) {
			if (scale >= 1f) {
				return false;
			}
			g.pose().pushPose();
			g.pose().scale(scale, scale, 1);
			return true;
		}

		public void pop(GuiGraphics g, boolean pushed) {
			if (pushed) {
				g.pose().popPose();
			}
		}

		/** A mouse event moved into canvas units. */
		public MouseButtonEvent in(MouseButtonEvent event) {
			return scale >= 1f ? event : new MouseButtonEvent(in(event.x()), in(event.y()), event.buttonInfo());
		}
	}
}
