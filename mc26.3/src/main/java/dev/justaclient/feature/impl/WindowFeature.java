package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.Window;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.render.Icons;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

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
			if (mc.options.fullscreen().get()) {
				mc.options.fullscreen().set(false);
			}
			try (MemoryStack stack = MemoryStack.stackPush()) {
				// The display the window is mostly on.
				SDL_Rect bounds = SDL_Rect.malloc(stack);
				if (!SDLVideo.SDL_GetDisplayBounds(SDLVideo.SDL_GetDisplayForWindow(handle), bounds)) {
					return;
				}
				IntBuffer wx = stack.mallocInt(1), wy = stack.mallocInt(1), ww = stack.mallocInt(1), wh = stack.mallocInt(1);
				SDLVideo.SDL_GetWindowPosition(handle, wx, wy);
				SDLVideo.SDL_GetWindowSize(handle, ww, wh);
				saved = new int[] {wx.get(0), wy.get(0), ww.get(0), wh.get(0)};
				SDLVideo.SDL_SetWindowBordered(handle, false);
				SDLVideo.SDL_SetWindowPosition(handle, bounds.x(), bounds.y());
				SDLVideo.SDL_SetWindowSize(handle, bounds.w(), bounds.h());
			}
			applied = true;
		} else {
			SDLVideo.SDL_SetWindowBordered(handle, true);
			if (saved != null) {
				SDLVideo.SDL_SetWindowPosition(handle, saved[0], saved[1]);
				SDLVideo.SDL_SetWindowSize(handle, saved[2], saved[3]);
			}
			applied = false;
		}
	}
}
