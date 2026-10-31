package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An effect where something you were fighting dies. */
public final class KillEffectsFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		BURST("Ring burst"), SOUL("Rising soul"), SHATTER("Shatter");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.BURST);
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting sound = bool("sound", "Play a chime", false);
	private final RandomSource random = RandomSource.create();

	public KillEffectsFeature() {
		super("kill_effects", "Kill effects", "A burst where something you fought dies", Category.COMBAT, Icons.SKULL, true);
	}

	public void onKill(LivingEntity entity) {
		if (!isEnabled()) {
			return;
		}
		AABB box = entity.getBoundingBox();
		Vec3 feet = new Vec3(box.getCenter().x, box.minY + 0.05, box.getCenter().z);
		float height = (float) box.getYsize();
		int c = color.argb();
		switch (style.get()) {
			case BURST -> {
				WorldFx.ring(feet, 0.2f, 2.4f, 0.22f, c, 0.8f);
				WorldFx.ring(feet.add(0, 0.02, 0), 0.1f, 1.5f, 0.12f, Colors.lerp(c, 0xFFFFFFFF, 0.6f), 0.6f);
				for (int i = 0; i < 36; i++) {
					double a = i / 36.0 * Math.PI * 2;
					double v = 2.5 + random.nextDouble() * 1.5;
					WorldFx.particle(feet.x, feet.y + 0.2, feet.z, Math.cos(a) * v, 2.0 + random.nextDouble() * 2.5, Math.sin(a) * v,
						0.16f, i % 3 == 0 ? Colors.lerp(c, 0xFFFFFFFF, 0.7f) : c, 0.9f, 7f, 2f);
				}
			}
			case SOUL -> {
				// A slow spiral of light rising out of the body.
				for (int i = 0; i < 40; i++) {
					double a = i * 0.7;
					double r = 0.35 + random.nextDouble() * 0.15;
					WorldFx.particle(feet.x + Math.cos(a) * r, feet.y + height * (i / 40.0), feet.z + Math.sin(a) * r,
						-Math.sin(a) * 0.6, 1.2 + random.nextDouble() * 0.6, Math.cos(a) * 0.6,
						0.22f, Colors.lerp(c, 0xFFFFFFFF, 0.35f), 1.4f + random.nextFloat() * 0.4f, -0.4f, 0.6f);
				}
				WorldFx.ring(feet, 0.1f, 1.0f, 0.15f, c, 1.0f);
			}
			case SHATTER -> {
				for (int i = 0; i < 60; i++) {
					double x = feet.x + (random.nextDouble() - 0.5) * box.getXsize();
					double y = feet.y + random.nextDouble() * height;
					double z = feet.z + (random.nextDouble() - 0.5) * box.getZsize();
					double dx = x - feet.x, dz = z - feet.z;
					WorldFx.particle(x, y, z, dx * 6 + (random.nextDouble() - 0.5) * 2, 1.5 + random.nextDouble() * 3, dz * 6 + (random.nextDouble() - 0.5) * 2,
						0.1f + random.nextFloat() * 0.1f, random.nextBoolean() ? c : Colors.lerp(c, 0xFFFFFFFF, 0.5f), 0.9f + random.nextFloat() * 0.5f, 12f, 1f);
				}
			}
		}
		if (sound.on()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.8f));
		}
	}
}
