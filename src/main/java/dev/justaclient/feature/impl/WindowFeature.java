package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.Window;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Borderless fullscreen: the window covers the monitor it is on with no frame, so alt-tabbing is instant. Turning it off
 * puts the window back where it was.
 */
public final class WindowFeature extends Feature {
	private int[] saved;
	private boolean applied;

	public WindowFeature() {
		super("borderless", "Borderless fullscreen", "Fullscreen without the alt-tab delay", Category.UTILITY, Icons.MAXIMIZE, false);
	}

	@Override
	public void tick() {
		// Applied from the tick, not onEnable, because profiles load before the window exists.
		if (!applied) {
			apply(true);
		}
	}

	@Override
	protected void onDisable() {
		if (applied) {
			apply(false);
		}
	}

	private void apply(boolean on) {
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		long handle = window.handle();
		if (on) {
			if (window.isFullscreen()) {
				window.toggleFullScreen();
			}
			long monitor = monitorOf(handle);
			GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
			if (mode == null) {
				return;
			}
			int[] mx = new int[1], my = new int[1], wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
			GLFW.glfwGetMonitorPos(monitor, mx, my);
			GLFW.glfwGetWindowPos(handle, wx, wy);
			GLFW.glfwGetWindowSize(handle, ww, wh);
			saved = new int[] {wx[0], wy[0], ww[0], wh[0]};
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
			GLFW.glfwSetWindowPos(handle, mx[0], my[0]);
			GLFW.glfwSetWindowSize(handle, mode.width(), mode.height());
			applied = true;
		} else {
			GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
			if (saved != null) {
				GLFW.glfwSetWindowPos(handle, saved[0], saved[1]);
				GLFW.glfwSetWindowSize(handle, saved[2], saved[3]);
			}
			applied = false;
		}
	}

	/** The monitor the window's centre is on, or the primary one. */
	private static long monitorOf(long handle) {
		int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
		GLFW.glfwGetWindowPos(handle, wx, wy);
		GLFW.glfwGetWindowSize(handle, ww, wh);
		int cx = wx[0] + ww[0] / 2, cy = wy[0] + wh[0] / 2;
		var monitors = GLFW.glfwGetMonitors();
		if (monitors != null) {
			for (int i = 0; i < monitors.limit(); i++) {
				long m = monitors.get(i);
				GLFWVidMode mode = GLFW.glfwGetVideoMode(m);
				int[] mx = new int[1], my = new int[1];
				GLFW.glfwGetMonitorPos(m, mx, my);
				if (mode != null && cx >= mx[0] && cx < mx[0] + mode.width() && cy >= my[0] && cy < my[0] + mode.height()) {
					return m;
				}
			}
		}
		return GLFW.glfwGetPrimaryMonitor();
	}
}
