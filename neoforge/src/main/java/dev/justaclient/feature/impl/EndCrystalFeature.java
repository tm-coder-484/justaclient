package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/** End crystal look: size, spin speed, the bobbing, and a soft glow ring on the ground under each crystal. */
public final class EndCrystalFeature extends Feature {
	private final NumberSetting size = number("size", "Size", 0.8, 0.4, 1.2, 0.05, NumberSetting.percent());
	private final NumberSetting spin = number("spin", "Spin speed", 0.6, 0.0, 2.0, 0.05, NumberSetting.percent());
	private final BoolSetting bob = bool("bob", "Bob up and down", false);
	private final BoolSetting ring = bool("ring", "Glow ring underneath", true);
	private final ColorSetting color = color("color", "Ring colour", ColorSetting.ACCENT).visibleWhen(ring::on);

	public EndCrystalFeature() {
		super("end_crystal", "End crystals", "Size, spin and a glow ring for end crystals", Category.COMBAT, Icons.GEM, false);
	}

	public static void init() {
		EndCrystalFeature f = Features.get(EndCrystalFeature.class);
		WorldFx.layer(f, frame -> {
			if (!f.ring.on() || Minecraft.getInstance().level == null) {
				return;
			}
			int c = f.color.argb();
			double t = System.currentTimeMillis() / 1000.0;
			for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
				if (!(e instanceof EndCrystal) || e.distanceToSqr(frame.camera) > 64 * 64) {
					continue;
				}
				double x = e.xOld + (e.getX() - e.xOld) * frame.partialTick;
				double y = e.yOld + (e.getY() - e.yOld) * frame.partialTick;
				double z = e.zOld + (e.getZ() - e.zOld) * frame.partialTick;
				float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.5 + e.getId());
				frame.flatRing(x, y + 0.02, z, 0.9f * f.size.f() * pulse, 0.22f, Colors.withAlpha(c, 0xB0), 48, 1f, 0f);
				frame.flatRing(x, y + 0.03, z, 1.3f * f.size.f(), 0.06f, Colors.withAlpha(c, 0x70), 48, 0.6f, (float) (t * 0.15));
			}
		});
	}

	private static EndCrystalFeature on() {
		EndCrystalFeature f = Features.get(EndCrystalFeature.class);
		return f != null && f.isEnabled() ? f : null;
	}

	/** Multiplier for the model's scale. */
	public static float scale() {
		EndCrystalFeature f = on();
		return f == null ? 1f : f.size.f();
	}

	/** Slows or speeds up the spin and bob by scaling the crystal's age. */
	public static float age(float age) {
		EndCrystalFeature f = on();
		return f == null ? age : age * f.spin.f();
	}

	/** The bob height, or a fixed middle height with bobbing off. */
	public static float bobY(float vanilla) {
		EndCrystalFeature f = on();
		return f == null || f.bob.on() ? vanilla : -1.0f;
	}
}
