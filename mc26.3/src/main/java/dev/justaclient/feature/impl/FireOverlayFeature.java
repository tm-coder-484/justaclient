package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/** Lowers the burning overlay so it covers less of the screen, or hides it. The fire icon and damage are unchanged. */
public final class FireOverlayFeature extends Feature {
	public enum Mode implements ChoiceSetting.Named {
		LOWER("Lower"), HIDE("Hide");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Mode> mode = choice("mode", "Mode", Mode.LOWER);
	private final NumberSetting height = number("height", "Lower by", 0.3, 0.05, 0.5, 0.05, NumberSetting.percent())
		.visibleWhen(() -> mode.get() == Mode.LOWER);

	public FireOverlayFeature() {
		super("fire_overlay", "Fire overlay", "Lower or hide the flames on screen", Category.FIRST_PERSON, Icons.FLAME, true);
	}

	public boolean hidden() {
		return mode.get() == Mode.HIDE;
	}

	public float offset() {
		return height.f();
	}
}
