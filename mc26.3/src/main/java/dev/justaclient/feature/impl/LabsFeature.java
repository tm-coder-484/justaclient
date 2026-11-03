package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.render.Icons;

/** The Labs tile: experimental features in one list, and the client's self-test. */
public final class LabsFeature extends Feature {
	public LabsFeature() {
		super("labs", "Labs", "Experimental features and a self-test you can run", Category.UTILITY, Icons.FLASK, true);
	}
}
