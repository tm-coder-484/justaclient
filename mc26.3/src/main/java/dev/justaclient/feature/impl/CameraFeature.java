package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/**
 * Aspect ratio stretch (the "stretched resolution" look, without changing the window) and the field of view of your
 * own hand. The world FOV stays the vanilla setting.
 */
public final class CameraFeature extends Feature {
	public enum Aspect implements ChoiceSetting.Named {
		WINDOW("Window", 0), FOUR_THREE("4:3", 4f / 3f), FIVE_FOUR("5:4", 5f / 4f), SIXTEEN_TEN("16:10", 16f / 10f), CUSTOM("Custom", 0);

		private final String label;
		final float ratio;

		Aspect(String label, float ratio) {
			this.label = label;
			this.ratio = ratio;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Aspect> aspect = choice("aspect", "Aspect ratio", Aspect.WINDOW);
	private final NumberSetting custom = number("custom", "Custom ratio", 1.5, 1, 2.4, 0.01, v -> String.format("%.2f", v))
		.visibleWhen(() -> aspect.get() == Aspect.CUSTOM);
	private final NumberSetting handFov = number("hand_fov", "Hand FOV", 70, 40, 110, 1, NumberSetting.unit("°"));

	public CameraFeature() {
		super("camera", "Camera", "Stretched aspect ratio and hand field of view", Category.CAMERA, Icons.VIDEO, false);
	}

	/** The aspect ratio the world is projected with; the picture is then stretched to fill the window. */
	public float aspect(float window) {
		if (!isEnabled()) {
			return window;
		}
		return switch (aspect.get()) {
			case WINDOW -> window;
			case CUSTOM -> custom.f();
			default -> aspect.get().ratio;
		};
	}

	public float handFov(float vanilla) {
		return isEnabled() ? handFov.f() : vanilla;
	}
}
