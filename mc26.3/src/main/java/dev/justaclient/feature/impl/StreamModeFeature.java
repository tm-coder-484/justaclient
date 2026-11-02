package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;

/**
 * One switch for recording or streaming: hides your name (and optionally friends' names), your coordinates and the
 * server address on your own screen.
 */
public final class StreamModeFeature extends Feature {
	private final BoolSetting names = bool("names", "Hide your name", true);
	private final BoolSetting friends = bool("friends", "Hide friends' names", true);
	private final BoolSetting coordinates = bool("coordinates", "Hide coordinates", true);
	private final BoolSetting server = bool("server", "Hide server address", true);

	public StreamModeFeature() {
		super("stream_mode", "Stream mode", "Hide your name, coordinates and server address on screen", Category.UTILITY, Icons.EYE, false);
	}

	private static StreamModeFeature get() {
		return Features.get(StreamModeFeature.class);
	}

	public static boolean hidesName() {
		return get().isEnabled() && get().names.on();
	}

	public static boolean hidesFriends() {
		return get().isEnabled() && get().friends.on();
	}

	public static boolean hidesCoordinates() {
		return get().isEnabled() && get().coordinates.on();
	}

	public static boolean hidesServer() {
		return get().isEnabled() && get().server.on();
	}
}
