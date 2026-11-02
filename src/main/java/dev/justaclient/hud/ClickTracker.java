package dev.justaclient.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import org.lwjgl.glfw.GLFW;

/** Counts mouse clicks in the last second for the CPS readout. Fed by the mouse handler mixin. */
public final class ClickTracker {
	private static final long WINDOW = 1_000_000_000L;
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private ClickTracker() {
	}

	public static void onPress(int button) {
		long now = System.nanoTime();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			LEFT.addLast(now);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			RIGHT.addLast(now);
		}
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.nanoTime() - WINDOW;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
			clicks.removeFirst();
		}
		return clicks.size();
	}
}
