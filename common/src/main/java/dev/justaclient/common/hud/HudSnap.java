package dev.justaclient.common.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a HUD widget being dragged snaps to along one axis: the screen edges (inside a margin), the screen centre, and the
 * edges, centres and gap-spaced sides of the other widgets. Plain maths shared by both versions' HUD editors.
 */
public final class HudSnap {
	/** Another widget along this axis: where it starts and how long it is. */
	public record Span(float start, float size) {
	}

	private HudSnap() {
	}

	/**
	 * The snapped start position for a widget of {@code size} dragged to {@code pos}, and the guide line to draw there
	 * (NaN for none), as {@code {position, guide}}. Only targets closer than {@code range} count; the nearest wins, and the
	 * first listed wins a tie.
	 */
	public static float[] snap(float pos, float size, float screen, float margin, float gap, boolean centres, List<Span> others, float range) {
		List<float[]> candidates = new ArrayList<>();
		// {position for the widget's start, guide line}
		candidates.add(new float[] {margin, margin});
		candidates.add(new float[] {screen - margin - size, screen - margin});
		if (centres) {
			candidates.add(new float[] {(screen - size) / 2f, screen / 2f});
		}
		for (Span other : others) {
			float o = other.start();
			float os = other.size();
			candidates.add(new float[] {o, o});
			candidates.add(new float[] {o + os - size, o + os});
			candidates.add(new float[] {o + os + gap, o + os + gap / 2f});
			candidates.add(new float[] {o - size - gap, o - gap / 2f});
			if (centres) {
				candidates.add(new float[] {o + os / 2f - size / 2f, o + os / 2f});
			}
		}
		float best = Float.MAX_VALUE;
		float result = pos;
		float guide = Float.NaN;
		for (float[] c : candidates) {
			float d = Math.abs(pos - c[0]);
			if (d < range && d < best) {
				best = d;
				result = c[0];
				guide = c[1];
			}
		}
		return new float[] {result, guide};
	}
}
