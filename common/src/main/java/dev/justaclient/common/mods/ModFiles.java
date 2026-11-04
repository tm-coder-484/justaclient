package dev.justaclient.common.mods;

import java.nio.file.Path;
import java.util.Locale;

/** Mod jar names: a mod is switched off by renaming {@code x.jar} to {@code x.jar.disabled}. */
public final class ModFiles {
	public static final String DISABLED_SUFFIX = ".disabled";

	private ModFiles() {
	}

	public static boolean isJar(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
	}

	public static boolean isDisabledJar(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar" + DISABLED_SUFFIX);
	}

	public static Path disabledPath(Path jar) {
		return jar.resolveSibling(jar.getFileName() + DISABLED_SUFFIX);
	}

	public static Path enabledPath(Path disabledJar) {
		String name = disabledJar.getFileName().toString();
		return disabledJar.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
	}

	public static String enabledName(Path path) {
		return normalize(isDisabledJar(path) ? enabledPath(path) : path).toString();
	}

	public static Path normalize(Path path) {
		return path.toAbsolutePath().normalize();
	}
}
