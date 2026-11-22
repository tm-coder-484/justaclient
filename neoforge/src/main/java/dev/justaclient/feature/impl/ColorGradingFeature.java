package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/**
 * Colour grading and a vignette over the world, like a photo filter. Menus and the HUD are not graded. Presets fill in
 * the sliders; touching a slider switches to Custom.
 */
public final class ColorGradingFeature extends Feature {
	public enum Preset implements ChoiceSetting.Named {
		CUSTOM("Custom", 0, 1, 1, 0, 0, 0),
		VIVID("Vivid", 0.05, 1.08, 1.18, 0.25, 0, 0),
		CINEMATIC("Cinematic", -0.05, 1.12, 0.9, 0, 0.25, -0.05),
		WARM("Warm", 0.05, 1.03, 1.05, 0.1, 0.6, 0.05),
		COOL("Cool", 0, 1.04, 0.98, 0.05, -0.6, 0),
		MONO("Monochrome", 0, 1.1, 0, 0, 0, 0);

		private final String label;
		final double exposure, contrast, saturation, vibrance, temperature, tint;

		Preset(String label, double exposure, double contrast, double saturation, double vibrance, double temperature, double tint) {
			this.label = label;
			this.exposure = exposure;
			this.contrast = contrast;
			this.saturation = saturation;
			this.vibrance = vibrance;
			this.temperature = temperature;
			this.tint = tint;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Preset> preset = choice("preset", "Preset", Preset.VIVID);
	private final NumberSetting exposure = number("exposure", "Brightness", 0.05, -1, 1, 0.05, v -> String.format("%+.2f", v));
	private final NumberSetting contrast = number("contrast", "Contrast", 1.08, 0.5, 1.5, 0.01, NumberSetting.percent());
	private final NumberSetting saturation = number("saturation", "Saturation", 1.18, 0, 2, 0.01, NumberSetting.percent());
	private final NumberSetting vibrance = number("vibrance", "Vibrance", 0.25, -1, 1, 0.05, NumberSetting.percent());
	private final NumberSetting temperature = number("temperature", "Temperature", 0, -1, 1, 0.05, v -> String.format("%+.2f", v));
	private final NumberSetting tint = number("tint", "Tint", 0, -1, 1, 0.05, v -> String.format("%+.2f", v));
	private final NumberSetting vignette = number("vignette", "Vignette", 0.35, 0, 1, 0.05, NumberSetting.percent());
	private final NumberSetting vignetteSize = number("vignette_size", "Vignette size", 0.55, 0.1, 1, 0.05, NumberSetting.percent())
		.visibleWhen(() -> vignette.get() > 0);
	private final ColorSetting vignetteColor = color("vignette_color", "Vignette colour", "#000000");
	private Preset applied;

	public ColorGradingFeature() {
		super("color_grading", "Color grading", "Contrast, saturation, colour balance and a vignette", Category.WORLD, Icons.SLIDERS, false);
		applied = preset.get();
	}

	@Override
	public void tick() {
		Preset p = preset.get();
		if (p != applied) {
			applied = p;
			if (p != Preset.CUSTOM) {
				exposure.set(p.exposure);
				contrast.set(p.contrast);
				saturation.set(p.saturation);
				vibrance.set(p.vibrance);
				temperature.set(p.temperature);
				tint.set(p.tint);
			}
		} else if (p != Preset.CUSTOM && differs(p)) {
			// A slider was moved away from the preset, so the look is now the player's own.
			preset.set(Preset.CUSTOM);
			applied = Preset.CUSTOM;
		}
	}

	private boolean differs(Preset p) {
		return Math.abs(exposure.get() - p.exposure) > 1e-3 || Math.abs(contrast.get() - p.contrast) > 1e-3
			|| Math.abs(saturation.get() - p.saturation) > 1e-3 || Math.abs(vibrance.get() - p.vibrance) > 1e-3
			|| Math.abs(temperature.get() - p.temperature) > 1e-3 || Math.abs(tint.get() - p.tint) > 1e-3;
	}

	public float exposure() {
		return exposure.f();
	}

	public float contrast() {
		return contrast.f();
	}

	public float saturation() {
		return saturation.f();
	}

	public float vibrance() {
		return vibrance.f();
	}

	public float temperature() {
		return temperature.f();
	}

	public float tint() {
		return tint.f();
	}

	public float vignetteStrength() {
		return vignette.f();
	}

	public float vignetteSize() {
		return 1f - vignetteSize.f();
	}

	public int vignetteColor() {
		return vignetteColor.argb();
	}
}
