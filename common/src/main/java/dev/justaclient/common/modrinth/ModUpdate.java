package dev.justaclient.common.modrinth;

import dev.justaclient.common.mods.ManagedMod;
import dev.justaclient.common.mods.ModFiles;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.common.mods.ToggleResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * After the browser installs a new version of a mod, the old jar has to stop loading, or both versions load. The old
 * one is switched off the same way the Mods panel does it; if that fails, the new jar is set aside (renamed to
 * .disabled) instead, so the game never starts with both.
 */
public final class ModUpdate {
	/**
	 * {@code oldOff}: the old version will not load. {@code newFile}: where the new jar is now. {@code newActive}: the new
	 * jar will load. {@code message}: what to tell the player, appended to the install line.
	 */
	public record Result(boolean oldOff, Path newFile, boolean newActive, String message) {
	}

	private ModUpdate() {
	}

	/** Switches {@code old} off now that {@code newJar} is installed; {@code old} null means there was nothing to switch off. */
	public static Result retireOld(@Nullable ManagedMod old, Path newJar) {
		if (old == null) {
			return new Result(true, newJar, true, "");
		}
		ToggleResult r = ModManager.toggle(old);
		switch (r.outcome()) {
			case RESTART_REQUIRED, DEFERRED, UNDONE, RENAMED -> {
				return new Result(true, newJar, true, "; the old version is turned off");
			}
			default -> {
			}
		}
		String why = r.error() == null || r.error().isBlank() ? "it could not be renamed" : r.error();
		Path aside = ModFiles.disabledPath(newJar);
		try {
			Files.move(newJar, aside);
			return new Result(false, aside, false, ". The old version could not be turned off (" + why
				+ "), so the new one is left off to keep two versions from loading. Sort it out in the Mods panel");
		} catch (IOException e) {
			return new Result(false, newJar, true, ". The old version could not be turned off (" + why
				+ ") and the new one could not be set aside, so both would load. Turn one off in the Mods panel");
		}
	}
}
