package dev.justaclient.common;

import java.util.Locale;

/** Turns a world key such as {@code mp:play.example.net:25565} into a safe folder name. */
public final class WorldKeys {
	private WorldKeys() {
	}

	public static String folder(String key) {
		String safe = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("_+", "_");
		if (safe.length() > 80) {
			safe = safe.substring(0, 80) + "_" + Integer.toHexString(key.hashCode());
		}
		return safe.isEmpty() ? "world" : safe;
	}
}
