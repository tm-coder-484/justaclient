package dev.justaclient.feature.impl;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/**
 * A soft glow around your first-person hand and held item, with optional colour styles. Turns itself off while an Iris
 * shader pack is running, since the pack composes the frame its own way.
 */
public final class HandGlowFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		NONE("Glow only"), GRADIENT("Accent gradient"), CHROMA("Chroma"), OUTLINE("Outline"), INVERT("Invert");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.NONE);
	private final NumberSetting amount = number("amount", "Style strength", 0.6, 0.1, 1, 0.05, NumberSetting.percent())
		.visibleWhen(() -> style.get() != Style.NONE);
	private final NumberSetting glow = number("glow", "Glow", 0.7, 0, 1, 0.05, NumberSetting.percent());
	private final NumberSetting radius = number("radius", "Glow size", 8, 2, 16, 1, NumberSetting.unit(" px"));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);

	public HandGlowFeature() {
		super("hand_glow", "Hand glow", "Glow, gradient, chroma or outline on your hand and held item", Category.FIRST_PERSON, Icons.SPARKLES, false);
	}

	public int colorStart() {
		return ColorSetting.ACCENT.equals(color.get()) ? JacConfig.accentAt(0f) : color.argb();
	}

	public int colorEnd() {
		return ColorSetting.ACCENT.equals(color.get()) ? JacConfig.accentAt(1f) : color.argb();
	}

	public float glowStrength() {
		return glow.f();
	}

	public float glowRadius() {
		return radius.f();
	}

	public float styleAmount() {
		return style.get() == Style.NONE ? 0f : amount.f();
	}

	public int styleIndex() {
		return style.get().ordinal();
	}
}
