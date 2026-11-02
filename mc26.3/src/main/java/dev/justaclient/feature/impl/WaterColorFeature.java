package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;

/**
 * Recolours water. Each biome keeps a hint of its own colour, so swamps and warm oceans still look different, mixed
 * towards the preset by the strength setting.
 */
public final class WaterColorFeature extends Feature {
	public enum Preset implements ChoiceSetting.Named {
		TROPICAL("Tropical", 0xFF1FC8D8), DEEP("Deep blue", 0xFF1E4FD8), CLEAR("Clear", 0xFF8FD3FF), EMERALD("Emerald", 0xFF22B07D), CUSTOM("Custom", 0);

		private final String label;
		final int color;

		Preset(String label, int color) {
			this.label = label;
			this.color = color;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Preset> preset = choice("preset", "Look", Preset.TROPICAL);
	private final ColorSetting custom = color("custom", "Colour", "#3B82F6").visibleWhen(() -> preset.get() == Preset.CUSTOM);
	private final NumberSetting strength = number("strength", "Strength", 0.7, 0.1, 1.0, 0.05, NumberSetting.percent());
	private int applied;

	public WaterColorFeature() {
		super("water_color", "Water colour", "Recolour water, keeping a hint of each biome", Category.WORLD, Icons.DROPLETS, false);
	}

	public static int recolor(int vanilla) {
		WaterColorFeature f = Features.get(WaterColorFeature.class);
		if (f == null || !f.isEnabled()) {
			return vanilla;
		}
		int target = f.preset.get() == Preset.CUSTOM ? f.custom.argb() : f.preset.get().color;
		return Colors.lerp(vanilla | 0xFF000000, target | 0xFF000000, f.strength.f()) & 0xFFFFFF | vanilla & 0xFF000000;
	}

	/** Water colours are baked into chunk meshes, so rebuild them when the look changes. */
	@Override
	public void tick() {
		int now = state(true);
		if (now != applied) {
			applied = now;
			rebuild();
		}
	}

	@Override
	protected void onEnable() {
		applied = state(true);
		rebuild();
	}

	@Override
	protected void onDisable() {
		applied = 0;
		rebuild();
	}

	private int state(boolean on) {
		return on ? (preset.get().ordinal() * 31 + custom.argb()) * 31 + Float.floatToIntBits(strength.f()) | 1 : 0;
	}

	private static void rebuild() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			mc.levelExtractor.allChanged();
		}
	}
}
