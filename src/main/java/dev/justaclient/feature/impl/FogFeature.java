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
 * Air fog colour per dimension and how close it starts, or no air fog at all. It never thins water, lava, powder snow,
 * blindness or darkness fog, since seeing further through those would be an advantage.
 */
public final class FogFeature extends Feature {
	private final BoolSetting noFog = bool("no_fog", "No fog", false);
	private final NumberSetting distance = number("distance", "Fog distance", 1.0, 0.2, 1.0, 0.05, NumberSetting.percent()).visibleWhen(() -> !noFog.on());
	private final BoolSetting recolor = bool("recolor", "Custom colours", true);
	private final ColorSetting overworld = color("overworld", "Overworld", "#B8C8E8").visibleWhen(recolor::on);
	private final ColorSetting nether = color("nether", "Nether", "#5A1E2E").visibleWhen(recolor::on);
	private final ColorSetting end = color("end", "End", "#2A1E44").visibleWhen(recolor::on);
	private final NumberSetting strength = number("strength", "Colour strength", 0.5, 0.05, 1.0, 0.05, NumberSetting.percent()).visibleWhen(recolor::on);

	public FogFeature() {
		super("fog", "Fog", "No fog, or fog colour per dimension and distance", Category.WORLD, Icons.CLOUD_FOG, false);
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
		if (noFog.on()) {
			// Push normal distance fog past anything that can be loaded. Water, lava, snow, blindness and darkness keep theirs.
			fog.environmentalStart = 1_000_000f;
			fog.environmentalEnd = 2_000_000f;
			fog.renderDistanceStart = 1_000_000f;
			fog.renderDistanceEnd = 2_000_000f;
			return;
		}
		float d = distance.f();
		fog.environmentalStart *= d;
		fog.environmentalEnd *= d;
		fog.renderDistanceStart *= d;
		fog.renderDistanceEnd *= d;
	}

	public Vector4f color(Camera camera, ClientLevel level, Vector4f color) {
		if (!applies(camera)) {
			return color;
		}
		boolean inOverworld = level.dimension() == Level.OVERWORLD;
		// Keep vanilla's brightness changes (night, storms, void) by scaling targets to the vanilla brightness.
		float lum = Math.max(color.x, Math.max(color.y, color.z));
		Vector4f out = color;
		if (isEnabled() && recolor.on()) {
			int target = level.dimension() == Level.NETHER ? nether.argb() : level.dimension() == Level.END ? end.argb() : overworld.argb();
			out = mix(out, (target >> 16 & 255) / 255f, (target >> 8 & 255) / 255f, (target & 255) / 255f, strength.f(), lum, inOverworld);
		}
		float[] biome = BiomeEffectsFeature.get().fogTint();
		if (biome[3] > 0f) {
			out = mix(out, biome[0], biome[1], biome[2], biome[3], lum, inOverworld);
		}
		return out;
	}

	/** {@code base} moved towards the colour by {@code s}; in the overworld the colour is scaled to {@code lum} first. */
	public static Vector4f mix(Vector4f base, float tr, float tg, float tb, float s, float lum, boolean scale) {
		float tl = Math.max(0.001f, Math.max(tr, Math.max(tg, tb)));
		float k = scale ? lum / tl : 1f;
		return new Vector4f(
			base.x + (Math.min(1f, tr * k) - base.x) * s,
			base.y + (Math.min(1f, tg * k) - base.y) * s,
			base.z + (Math.min(1f, tb * k) - base.z) * s,
			base.w
		);
	}
}
