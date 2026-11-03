package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.render.Icons;

/** The World Archive tile: waypoints, sessions, screenshots and the map of every world the client remembers. */
public final class WorldArchiveFeature extends Feature {
	public WorldArchiveFeature() {
		super("world_archive", "World Archive", "Waypoints, sessions and screenshots of every world, in one place", Category.UTILITY, Icons.ARCHIVE,
			true);
	}
}
