package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Icons;

/** Lights every block fully, day or night, above ground or in caves. Works through the lightmap, so shader packs keep it. */
public final class FullbrightFeature extends Feature {
	public FullbrightFeature() {
		super("fullbright", "Fullbright", "Everything fully lit, day or night", Category.WORLD, Icons.SUN, false);
	}

	/** 1 when on, 0 when off. */
	public static float amount() {
		FullbrightFeature f = Features.get(FullbrightFeature.class);
		return f != null && f.isEnabled() ? 1f : 0f;
	}
}
