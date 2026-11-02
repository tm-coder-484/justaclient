package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;

/**
 * Air fog colour per dimension and how close it starts. It only ever brings fog closer: it never thins water, lava,
 * powder snow, blindness or darkness fog, since seeing further through those would be an advantage.
 */
public final class FogFeature extends Feature {
	private final NumberSetting distance = number("distance", "Fog distance", 1.0, 0.2, 1.0, 0.05, NumberSetting.percent());
	private final BoolSetting recolor = bool("recolor", "Custom colours", true);
	private final ColorSetting overworld = color("overworld", "Overworld", "#B8C8E8").visibleWhen(recolor::on);
	private final ColorSetting nether = color("nether", "Nether", "#5A1E2E").visibleWhen(recolor::on);
	private final ColorSetting end = color("end", "End", "#2A1E44").visibleWhen(recolor::on);
	private final NumberSetting strength = number("strength", "Colour strength", 0.5, 0.05, 1.0, 0.05, NumberSetting.percent()).visibleWhen(recolor::on);

	public FogFeature() {
		super("fog", "Fog", "Fog colour per dimension and distance", Category.WORLD, Icons.CLOUD_FOG, false);
	}

	private static boolean applies(Camera camera) {
		if (camera.getFluidInCamera() != FogType.NONE) {
			return false;
		}
		return !(camera.entity() instanceof LivingEntity living) || !living.hasEffect(MobEffects.BLINDNESS) && !living.hasEffect(MobEffects.DARKNESS);
	}

	public void adjust(Camera camera, FogData fog) {
		if (!isEnabled() || !applies(camera)) {
			return;
		}
		float d = distance.f();
		fog.environmentalStart *= d;
		fog.environmentalEnd *= d;
		fog.renderDistanceStart *= d;
		fog.renderDistanceEnd *= d;
	}

	public Vector4f color(Camera camera, ClientLevel level, Vector4f color) {
		if (!isEnabled() || !recolor.on() || !applies(camera)) {
			return color;
		}
		int target = level.dimension() == Level.NETHER ? nether.argb() : level.dimension() == Level.END ? end.argb() : overworld.argb();
		float s = strength.f();
		// Keep vanilla's brightness changes (night, storms, void) by scaling the target to the vanilla brightness.
		float lum = Math.max(color.x, Math.max(color.y, color.z));
		float tr = (target >> 16 & 255) / 255f, tg = (target >> 8 & 255) / 255f, tb = (target & 255) / 255f;
		float tl = Math.max(0.001f, Math.max(tr, Math.max(tg, tb)));
		float k = level.dimension() == Level.OVERWORLD ? lum / tl : 1f;
		return new Vector4f(
			color.x + (Math.min(1f, tr * k) - color.x) * s,
			color.y + (Math.min(1f, tg * k) - color.y) * s,
			color.z + (Math.min(1f, tb * k) - color.z) * s,
			color.w
		);
	}
}
