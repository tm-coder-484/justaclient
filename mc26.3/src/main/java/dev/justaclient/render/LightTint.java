package dev.justaclient.render;

import dev.justaclient.feature.impl.LightColorFeature;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Light colour on 26.3. The lightmap here already takes a block light tint and a sky light colour from the world's
 * environment, so the tint is mixed into those values after vanilla works them out; no extra shader is needed.
 */
public final class LightTint {
	private LightTint() {
	}

	/** Called after the lightmap state was extracted. Only touches frames where vanilla refreshed it. */
	public static void apply(LightmapRenderState state) {
		if (!state.needsUpdate) {
			return;
		}
		LightColorFeature f = LightColorFeature.get();
		float[] block = f.blockTint();
		float[] sky = f.skyTint();
		if (block[3] > 0f) {
			state.blockLightTint = tint(state.blockLightTint, block);
		}
		if (sky[3] > 0f) {
			state.skyLightColor = tint(state.skyLightColor, sky);
		}
	}

	/** Recolours without brightening: the tint's hue at the original's peak channel, mixed in by the strength. */
	private static Vector3fc tint(Vector3fc original, float[] tint) {
		float peak = Math.max(original.x(), Math.max(original.y(), original.z()));
		float tintPeak = Math.max(0.001f, Math.max(tint[0], Math.max(tint[1], tint[2])));
		float a = tint[3];
		return new Vector3f(
			original.x() + (tint[0] / tintPeak * peak - original.x()) * a,
			original.y() + (tint[1] / tintPeak * peak - original.y()) * a,
			original.z() + (tint[2] / tintPeak * peak - original.z()) * a);
	}
}
