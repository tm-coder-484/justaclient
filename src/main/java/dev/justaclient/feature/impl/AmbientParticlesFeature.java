package dev.justaclient.feature.impl;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import dev.justaclient.render.Motes;
import net.minecraft.client.Minecraft;

/**
 * Drifting glowing motes in the air around you: fireflies, slow snow-like dots, or dots joined by faint lines. They live
 * only on your screen and only in open air, so they never hint at anything hidden.
 */
public final class AmbientParticlesFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		FIREFLIES("Fireflies"), DRIFT("Drift"), LINKED("Linked dots");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.FIREFLIES);
	private final NumberSetting count = number("count", "Amount", 60, 10, 200, 5, NumberSetting.unit(""));
	private final NumberSetting radius = number("radius", "Spread", 10, 4, 24, 1, NumberSetting.unit(" blocks"));
	private final NumberSetting size = number("size", "Size", 0.12, 0.04, 0.4, 0.01, v -> String.format("%.2f", v));
	private final ColorSetting color = color("color", "Colour", "#F59E0B");
	private final BoolSetting nightOnly = bool("night_only", "Only at night", false);
	private final Motes motes = new Motes();

	public AmbientParticlesFeature() {
		super("ambient_particles", "Ambient particles", "Fireflies and drifting motes around you", Category.WORLD, Icons.SPARKLES, false);
	}

	public static void init() {
		AmbientParticlesFeature feature = Features.get(AmbientParticlesFeature.class);
		WorldFx.layer(feature, feature::render);
	}

	@Override
	protected void onDisable() {
		motes.clear();
	}

	private void render(WorldFx.Frame f) {
		Minecraft mc = Minecraft.getInstance();
		if (CleanWorldFeature.hidesClientParticles() || mc.level == null) {
			motes.clear();
			return;
		}
		// Biome effects can give a biome its own motes, or none; then these step aside.
		if (BiomeEffectsFeature.get().replacesParticles()) {
			motes.draw(f, null);
			return;
		}
		boolean night = mc.level.getSkyDarken() > 4 || !mc.level.dimensionType().hasSkyLight();
		motes.draw(f, !nightOnly.on() || night ? look() : null);
	}

	/** This feature's motes as set. */
	public Motes.Look look() {
		boolean accent = ColorSetting.ACCENT.equals(color.get());
		return new Motes.Look(style.get(), count.i(), radius.get(), size.f(), accent ? JacConfig.accent() : color.argb(), accent);
	}

	public static AmbientParticlesFeature get() {
		return Features.get(AmbientParticlesFeature.class);
	}
}
