package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.render.Icons;

/** After a crash, the next launch explains it in plain words and can build a diagnostic zip. Everything stays local. */
public final class CrashHelperFeature extends Feature {
	public CrashHelperFeature() {
		super("crash_helper", "Crash helper", "After a crash, explains it on the next launch and builds a diagnostic zip", Category.TOOLS, Icons.INFO,
			true);
	}
}
