package dev.justaclient.hud;

import dev.justaclient.common.ui.Fit;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HudEditorFeature;
import net.minecraft.client.Minecraft;

/**
 * Shrinks the whole HUD a little when the GUI is small, so widgets laid out on a normal screen do not pile into each
 * other at GUI scale 4 or in a small window. At 600 by 338 GUI units or more (GUI scale 3 on 1080p) nothing changes; at
 * GUI scale 4 on 1080p (480 by 270) widgets draw at about 80%, and never below 60%.
 */
public final class HudFit {
	private HudFit() {
	}

	/** The factor for the current window. */
	public static float factor() {
		HudEditorFeature editor = Features.get(HudEditorFeature.class);
		if (editor != null && !editor.fitsSmallScreens()) {
			return 1f;
		}
		var window = Minecraft.getInstance().getWindow();
		return factor(window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}

	/** The factor for a GUI of {@code w} by {@code h} units. */
	public static float factor(float w, float h) {
		return Fit.hudScale(w, h);
	}
}
