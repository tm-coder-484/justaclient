package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/** Eases vanilla and modded scrolling lists towards where the wheel sends them instead of jumping there. */
public final class SmoothScrollFeature extends Feature {
	private final NumberSetting speed = number("speed", "Glide speed", 14, 4, 30, 1, v -> v <= 8 ? "Slow" : v >= 22 ? "Snappy" : "Normal");
	private final NumberSetting distance = number("distance", "Distance per notch", 1, 0.5, 3, 0.25, v -> String.format("%.2gx", v));

	public SmoothScrollFeature() {
		super("smooth_scroll", "Smooth scrolling", "Lists glide when you scroll, in vanilla and modded screens", Category.SCREENS, Icons.SLIDERS,
			true);
	}

	/** The feature if it is on, else null. */
	public static SmoothScrollFeature active() {
		SmoothScrollFeature f = Features.get(SmoothScrollFeature.class);
		return f != null && f.isEnabled() ? f : null;
	}

	/** Easing rate per second. */
	public double rate() {
		return speed.get();
	}

	public double distance() {
		return distance.get();
	}
}
