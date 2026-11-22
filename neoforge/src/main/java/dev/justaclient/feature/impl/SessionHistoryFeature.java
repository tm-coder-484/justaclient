package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;

/** Keeps a history of your sessions for the Stats page. Stored on your computer only. */
public final class SessionHistoryFeature extends Feature {
	private final NumberSetting keep = number("keep", "Sessions kept", 50, 10, 200, 10, NumberSetting.unit(""));
	private final NumberSetting minimum = number("minimum", "Skip sessions shorter than", 1, 0, 10, 1, NumberSetting.unit(" min"));
	private final BoolSetting carried = bool("carried", "Record what you carried", true);

	public SessionHistoryFeature() {
		super("session_history", "Session history", "Saves each session (time, kills, blocks, pickups, inventory) for the Stats page", Category.TOOLS,
			Icons.CLOCK, true);
	}

	public int keep() {
		return keep.i();
	}

	public long minimumSeconds() {
		return minimum.i() * 60L;
	}

	public boolean recordsCarried() {
		return carried.on();
	}
}
