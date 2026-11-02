package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;

/** Hides clutter: falling sand and gravel, the fire drawn on burning mobs and players, and rain and snow. */
public final class CleanWorldFeature extends Feature {
	private final BoolSetting fallingBlocks = bool("falling_blocks", "Hide falling blocks", true);
	private final BoolSetting entityFire = bool("entity_fire", "Hide fire on burning entities", true);
	private final BoolSetting weather = bool("weather", "Hide rain and snow", false);

	public CleanWorldFeature() {
		super("clean_world", "Clean world", "Hide falling blocks, fire on entities and weather", Category.WORLD, Icons.CLOUD_RAIN, false);
	}

	private static CleanWorldFeature get() {
		return Features.get(CleanWorldFeature.class);
	}

	public static boolean hidesFallingBlocks() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && f.fallingBlocks.on();
	}

	public static boolean hidesEntityFire() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && f.entityFire.on();
	}

	public static boolean hidesWeather() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && f.weather.on();
	}
}
