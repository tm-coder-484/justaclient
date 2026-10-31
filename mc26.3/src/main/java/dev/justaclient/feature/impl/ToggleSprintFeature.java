package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Icons;

/**
 * Sprints whenever you walk forward, as if the sprint key were held. Bind the toggle key to switch it on and off in game.
 * Vanilla's sprint rules still apply (hunger, blindness, using items).
 */
public final class ToggleSprintFeature extends Feature {
	public ToggleSprintFeature() {
		super("toggle_sprint", "Toggle sprint", "Always sprint while walking forward", Category.UTILITY, Icons.FOOTPRINTS, false);
	}

	public static boolean active() {
		return Features.get(ToggleSprintFeature.class).isEnabled();
	}
}
