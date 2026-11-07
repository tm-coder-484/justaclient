package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** A burst of glowing sparks on the entity when one of the player's hits lands. */
public final class HitParticlesFeature extends Feature {
	private final NumberSetting amount = number("amount", "Amount", 14, 4, 40, 1, NumberSetting.unit(""));
	private final NumberSetting size = number("size", "Size", 0.2, 0.05, 0.4, 0.01, v -> String.format("%.2f", v));
	private final NumberSetting speed = number("speed", "Speed", 3.0, 0.5, 8.0, 0.1, NumberSetting.decimal(""));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting mixWhite = bool("mix_white", "Mix in white sparks", true);
	private final BoolSetting physics = bool("physics", "Gravity", true);
	private final RandomSource random = RandomSource.create();

	public HitParticlesFeature() {
		super("hit_particles", "Hit particles", "Sparks burst from whatever you hit", Category.COMBAT, Icons.SPARKLES, true);
	}

	public void onHit(LivingEntity entity) {
		if (!isEnabled()) {
			return;
		}
		AABB box = entity.getBoundingBox();
		double cx = box.getCenter().x;
		double cy = box.minY + box.getYsize() * 0.6;
		double cz = box.getCenter().z;
		int base = color.argb();
		for (int i = 0; i < amount.i(); i++) {
			// Directions spread over a sphere, biased upwards so sparks arc out rather than drop straight down.
			double theta = random.nextDouble() * Math.PI * 2;
			double y = random.nextDouble() * 1.4 - 0.4;
			double r = Math.sqrt(Math.max(0, 1 - Math.min(1, y * y)));
			double v = speed.get() * (0.5 + random.nextDouble() * 0.7);
			int c = mixWhite.on() && random.nextFloat() < 0.3f ? Colors.lerp(base, 0xFFFFFFFF, 0.7f) : base;
			WorldFx.particle(
				cx + (random.nextDouble() - 0.5) * box.getXsize() * 0.6, cy + (random.nextDouble() - 0.5) * box.getYsize() * 0.4,
				cz + (random.nextDouble() - 0.5) * box.getZsize() * 0.6,
				Math.cos(theta) * r * v, y * v, Math.sin(theta) * r * v,
				size.f() * (0.7f + random.nextFloat() * 0.6f), c, 0.45f + random.nextFloat() * 0.35f, physics.on() ? 6f : 0f, 2.5f
			);
		}
	}
}
