package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;
import net.minecraft.resources.Identifier;

/**
 * Hides screen overlays that only get in the way: the carved pumpkin mask, the powder snow frost and the nausea swirl.
 * Blindness and darkness are left alone on purpose, since hiding them would be an advantage.
 */
public final class OverlaysFeature extends Feature {
	private static final Identifier POWDER_SNOW = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");

	private final BoolSetting pumpkin = bool("pumpkin", "Hide pumpkin mask", true);
	private final BoolSetting powderSnow = bool("powder_snow", "Hide powder snow frost", true);
	private final BoolSetting nausea = bool("nausea", "Hide nausea swirl", true);

	public OverlaysFeature() {
		super("overlays", "Screen overlays", "Hide the pumpkin mask, powder snow frost and nausea swirl", Category.FIRST_PERSON, Icons.EYE, false);
	}

	/** True if a full-screen texture overlay (pumpkin, frost) should not be drawn. */
	public boolean hides(Identifier texture) {
		if (!isEnabled()) {
			return false;
		}
		if (texture.equals(POWDER_SNOW)) {
			return powderSnow.on();
		}
		return pumpkin.on() && texture.getPath().contains("pumpkinblur");
	}

	public boolean hidesNausea() {
		return isEnabled() && nausea.on();
	}
}
