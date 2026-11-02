package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;

/**
 * Trades a little polish for frame time and memory on slower machines: lighter or no blur, fewer effect particles,
 * smaller background pictures, screen shaders paused, and GPU buffers freed when nothing uses them.
 */
public final class PerformanceModeFeature extends Feature {
	private final BoolSetting hudBlur = bool("hud_blur", "No blur behind HUD widgets", true);
	private final BoolSetting lightBlur = bool("light_blur", "Lighter menu blur", true);
	private final BoolSetting particles = bool("particles", "Fewer effect particles", true);
	private final BoolSetting smallImages = bool("small_images", "Smaller background pictures", true);
	private final BoolSetting shaders = bool("shaders", "Pause screen shaders", false);
	private final BoolSetting freeMemory = bool("free_memory", "Free unused GPU buffers", true);

	public PerformanceModeFeature() {
		super("performance_mode", "Performance mode", "Lighter blur, fewer particles and less texture memory", Category.UTILITY, Icons.GAUGE, false);
	}

	private static PerformanceModeFeature get() {
		return Features.get(PerformanceModeFeature.class);
	}

	private static boolean on(BoolSetting setting) {
		PerformanceModeFeature f = get();
		return f != null && f.isEnabled() && setting.on();
	}

	/** False when HUD widgets should skip the blur even if the theme asks for it. */
	public static boolean allowHudBlur() {
		PerformanceModeFeature f = get();
		return f == null || !on(f.hudBlur);
	}

	/** The most blur passes to run; each pass halves the resolution again. */
	public static int maxBlurPasses() {
		PerformanceModeFeature f = get();
		return f != null && on(f.lightBlur) ? 2 : Integer.MAX_VALUE;
	}

	public static int particleCap(int normal) {
		PerformanceModeFeature f = get();
		return f != null && on(f.particles) ? normal / 4 : normal;
	}

	/** Longest side, in pixels, that background pictures are decoded at. */
	public static int imageSize() {
		PerformanceModeFeature f = get();
		return f != null && on(f.smallImages) ? 960 : 1920;
	}

	public static boolean pauseShaders() {
		PerformanceModeFeature f = get();
		return f != null && on(f.shaders);
	}

	public static boolean freeMemory() {
		PerformanceModeFeature f = get();
		return f != null && on(f.freeMemory);
	}
}
