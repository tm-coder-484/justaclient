package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/**
 * Tints the light itself: torches and lava (block light) and the sun and moon (sky light). It only changes the colour,
 * never how bright anything is, so it can't be used to see in the dark.
 */
public final class LightColorFeature extends Feature {
	private final BoolSetting block = bool("block", "Tint block light", true);
	private final ColorSetting blockColor = color("block_color", "Block light colour", "#FFB066").visibleWhen(block::on);
	private final NumberSetting blockStrength = number("block_strength", "Block light strength", 0.6, 0.05, 1.0, 0.05, NumberSetting.percent())
		.visibleWhen(block::on);
	private final BoolSetting sky = bool("sky", "Tint sky light", false);
	private final ColorSetting skyColor = color("sky_color", "Sky light colour", "#9DB4FF").visibleWhen(sky::on);
	private final NumberSetting skyStrength = number("sky_strength", "Sky light strength", 0.4, 0.05, 1.0, 0.05, NumberSetting.percent())
		.visibleWhen(sky::on);

	public LightColorFeature() {
		super("light_color", "Light colour", "Colour of torch light and daylight", Category.WORLD, Icons.LIGHTBULB, false);
	}

	public static LightColorFeature get() {
		return Features.get(LightColorFeature.class);
	}

	/** Block tint as r, g, b, strength; strength 0 when off. */
	public float[] blockTint() {
		return tint(isEnabled() && block.on(), blockColor.argb(), blockStrength.f());
	}

	public float[] skyTint() {
		return tint(isEnabled() && sky.on(), skyColor.argb(), skyStrength.f());
	}

	private static float[] tint(boolean on, int argb, float strength) {
		return new float[] {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, on ? strength : 0f};
	}
}
