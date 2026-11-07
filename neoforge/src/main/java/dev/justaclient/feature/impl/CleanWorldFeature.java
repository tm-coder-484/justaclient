package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;

/**
 * Hides clutter: falling sand and gravel, the fire drawn on burning mobs and players, rain and snow, explosion particles
 * or every particle. "Also hide the client's effects" takes out the client's own sparks, motes and trails too.
 */
public final class CleanWorldFeature extends Feature {
	private final BoolSetting fallingBlocks = bool("falling_blocks", "Hide falling blocks", true);
	private final BoolSetting entityFire = bool("entity_fire", "Hide fire on burning entities", true);
	private final BoolSetting weather = bool("weather", "Hide rain and snow", false);
	private final BoolSetting explosions = bool("explosions", "Hide explosion particles", false);
	private final BoolSetting particles = bool("particles", "No particles", false);
	private final BoolSetting clientParticles = bool("client_particles", "Also hide the client's effects", false).visibleWhen(particles::on);

	public CleanWorldFeature() {
		super("clean_world", "Clean world", "Hide falling blocks, fire, weather and particles", Category.WORLD, Icons.CLOUD_RAIN, false);
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

	public static boolean hidesExplosions() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && (f.explosions.on() || f.particles.on());
	}

	public static boolean hidesAllParticles() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && f.particles.on();
	}

	/** The client's own effect particles (hit sparks, ambient motes, trails). */
	public static boolean hidesClientParticles() {
		CleanWorldFeature f = get();
		return f != null && f.isEnabled() && f.particles.on() && f.clientParticles.on();
	}
}
