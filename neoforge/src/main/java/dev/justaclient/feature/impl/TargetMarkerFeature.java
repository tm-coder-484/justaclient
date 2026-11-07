package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Marks the entity you are fighting with a ring at its feet or lights orbiting it. It is drawn in the world with depth
 * testing, so it is hidden behind blocks exactly like the entity, and it never shows for invisible entities.
 */
public final class TargetMarkerFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		RING("Ring"), ORBIT("Orbit"), BOTH("Both");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.RING);
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final ColorSetting hurtColor = color("hurt_color", "Hit flash colour", "#FFFFFF");
	private final NumberSetting spin = number("spin", "Spin speed", 1.0, 0, 3, 0.1, NumberSetting.decimal("x"));
	private @Nullable LivingEntity shown;
	private float visibility;
	private float angle;

	public TargetMarkerFeature() {
		super("target_marker", "Target marker", "A ring or orbit on the entity you are fighting", Category.COMBAT, Icons.TARGET, true);
	}

	public static void init() {
		TargetMarkerFeature feature = Features.get(TargetMarkerFeature.class);
		WorldFx.layer(feature, feature::render);
	}

	private void render(WorldFx.Frame f) {
		LivingEntity target = CombatTracker.target();
		Minecraft mc = Minecraft.getInstance();
		if (target != null && (target.isInvisibleTo(mc.player) || target.isDeadOrDying())) {
			target = null;
		}
		if (target != null) {
			shown = target;
		}
		visibility = target != null ? Math.min(1f, visibility + f.dt * 6f) : Math.max(0f, visibility - f.dt * 4f);
		if (shown == null || visibility <= 0f) {
			shown = null;
			return;
		}
		angle += f.dt * spin.f() * 0.25f;
		Vec3 pos = shown.getPosition(f.partialTick);
		AABB box = shown.getBoundingBox();
		float width = (float) Math.max(box.getXsize(), box.getZsize());
		float height = (float) box.getYsize();
		// White flash while the entity shows vanilla's red hurt tint.
		float hurt = shown.hurtTime > 0 ? shown.hurtTime / 10f : 0f;
		int c = Colors.fade(Colors.lerp(color.argb(), hurtColor.argb(), hurt * 0.8f), visibility);
		float pop = 1f + hurt * 0.15f;
		Style s = style.get();
		if (s == Style.RING || s == Style.BOTH) {
			float r = (width * 0.75f + 0.25f) * pop;
			f.flatRing(pos.x, pos.y + 0.03, pos.z, r, 0.12f, c, 72, 0.62f, angle);
			f.flatRing(pos.x, pos.y + 0.03, pos.z, r, 0.4f, Colors.fade(c, 0.18f), 48, 1f, 0f);
		}
		if (s == Style.ORBIT || s == Style.BOTH) {
			float r = width * 0.7f + 0.35f;
			for (int i = 0; i < 3; i++) {
				double a = (angle * 2.4 + i / 3.0) * Math.PI * 2;
				double y = pos.y + height * (0.35 + 0.25 * Math.sin(a * 0.5 + i));
				double x = pos.x + Math.cos(a) * r;
				double z = pos.z + Math.sin(a) * r;
				f.sprite(x, y, z, 0.32f * pop, c);
				// A short tail behind each light.
				for (int k = 1; k <= 4; k++) {
					double b = a - k * 0.12;
					f.sprite(pos.x + Math.cos(b) * r, pos.y + height * (0.35 + 0.25 * Math.sin(b * 0.5 + i)), pos.z + Math.sin(b) * r,
						0.3f * pop * (1f - k * 0.18f), Colors.fade(c, 0.6f - k * 0.12f));
				}
			}
		}
	}
}
