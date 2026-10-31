package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** Bubbles float up and pop from whatever your hits land on. */
public final class HitBubblesFeature extends Feature {
	private final NumberSetting amount = number("amount", "Amount", 6, 2, 16, 1, NumberSetting.unit(""));
	private final NumberSetting size = number("size", "Size", 0.28, 0.1, 0.6, 0.02, v -> String.format("%.2f", v));
	private final NumberSetting life = number("life", "Time before popping", 1.2, 0.4, 3.0, 0.1, NumberSetting.decimal(" s"));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final RandomSource random = RandomSource.create();

	public HitBubblesFeature() {
		super("hit_bubbles", "Hit bubbles", "Bubbles rise and pop from what you hit", Category.COMBAT, Icons.CIRCLE_DOT, false);
	}

	public void onHit(LivingEntity entity) {
		if (!isEnabled()) {
			return;
		}
		AABB box = entity.getBoundingBox();
		int base = color.argb();
		for (int i = 0; i < amount.i(); i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double out = 0.6 + random.nextDouble() * 0.8;
			int c = Colors.lerp(base, 0xFFFFFFFF, random.nextFloat() * 0.35f);
			WorldFx.bubble(box.getCenter().x + Math.cos(angle) * box.getXsize() * 0.4, box.minY + box.getYsize() * (0.4 + random.nextDouble() * 0.4),
				box.getCenter().z + Math.sin(angle) * box.getZsize() * 0.4, Math.cos(angle) * out, 0.6 + random.nextDouble() * 0.8,
				Math.sin(angle) * out, size.f() * (0.6f + random.nextFloat() * 0.8f), c, life.f() * (0.7f + random.nextFloat() * 0.6f));
		}
	}
}
