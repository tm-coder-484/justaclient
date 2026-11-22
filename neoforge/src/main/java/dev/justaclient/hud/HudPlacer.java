package dev.justaclient.hud;

import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HudEditorFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Finds the nearest spot where a widget covers no other widget. */
public final class HudPlacer {
	private static final float STEP = 4f;

	private HudPlacer() {
	}

	/** Moves a widget that was just switched on, if it would overlap another one. */
	public static void placeNew(HudFeature hud) {
		HudEditorFeature editor = Features.get(HudEditorFeature.class);
		if (editor == null || !editor.placesNew()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) {
			return;
		}
		place(hud, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), editor.gap());
	}

	private static void size(HudFeature hud) {
		hud.layout(false);
		if (!hud.hasContent()) {
			hud.layout(true);
		}
	}

	public static void place(HudFeature hud, float sw, float sh, float gap) {
		size(hud);
		List<float[]> taken = new ArrayList<>();
		for (HudFeature other : Features.hud()) {
			if (other == hud || !other.isEnabled()) {
				continue;
			}
			size(other);
			taken.add(new float[] {other.screenX(sw) - gap, other.screenY(sh) - gap, other.scaledWidth() + gap * 2, other.scaledHeight() + gap * 2});
		}
		float w = hud.scaledWidth();
		float h = hud.scaledHeight();
		float x0 = hud.screenX(sw);
		float y0 = hud.screenY(sh);
		if (free(x0, y0, w, h, taken)) {
			return;
		}
		float bestD = Float.MAX_VALUE;
		float bx = x0;
		float by = y0;
		for (float y = HudFeature.MARGIN; y <= sh - HudFeature.MARGIN - h; y += STEP) {
			for (float x = HudFeature.MARGIN; x <= sw - HudFeature.MARGIN - w; x += STEP) {
				float d = (x - x0) * (x - x0) + (y - y0) * (y - y0);
				if (d < bestD && free(x, y, w, h, taken)) {
					bestD = d;
					bx = x;
					by = y;
				}
			}
		}
		// Try the exact screen edges too, so a widget can sit flush against one.
		float[][] edges = {{HudFeature.MARGIN, by}, {sw - HudFeature.MARGIN - w, by}, {bx, HudFeature.MARGIN}, {bx, sh - HudFeature.MARGIN - h}};
		for (float[] e : edges) {
			float d = (e[0] - x0) * (e[0] - x0) + (e[1] - y0) * (e[1] - y0);
			if (d < bestD && free(e[0], e[1], w, h, taken)) {
				bestD = d;
				bx = e[0];
				by = e[1];
			}
		}
		if (bestD < Float.MAX_VALUE) {
			hud.moveTo(bx, by, sw, sh);
		}
	}

	private static boolean free(float x, float y, float w, float h, List<float[]> taken) {
		for (float[] r : taken) {
			if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) {
				return false;
			}
		}
		return true;
	}
}
