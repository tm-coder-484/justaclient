package dev.justaclient.common.modrinth;

import dev.justaclient.common.mods.ManagedMod;
import dev.justaclient.common.mods.ModFiles;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.common.mods.ToggleResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

	/**
	 * Keeps an index of what is installed honest after an install: the new entry only if the new file will actually
	 * load, otherwise whatever was there before (or nothing).
	 */
	public static <K, V> void index(java.util.Map<K, V> installed, K key, @Nullable V before, V now, boolean nowActive) {
		if (nowActive) {
			installed.put(key, now);
		} else if (before != null) {
			installed.put(key, before);
		} else {
			installed.remove(key);
		}
	}

	/** Switches {@code old} off now that {@code newJar} is installed; {@code old} null means there was nothing to switch off. */
	public static Result retireOld(@Nullable ManagedMod old, Path newJar) {
		if (old == null || !old.status().activeNextLaunch()) {
			// Nothing to switch off. Toggling a mod that is already off would switch it back on.
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
		return setAside(newJar, "The old version could not be turned off (" + why + ")");
	}

	/**
	 * The mod, other than {@code newJar} itself, that will also load on the next start under {@code id}. Fabric refuses to
	 * start with two mods of the same id, so this catches an old copy Modrinth did not recognise (renamed, from another
	 * site) or one loaded from outside the mods folder.
	 */
	public static @Nullable ManagedMod clash(String id, Path newJar, List<ManagedMod> all) {
		Path fresh = ModFiles.normalize(newJar);
		for (ManagedMod m : all) {
			if (!m.id().equals(id) || !m.status().activeNextLaunch()) {
				continue;
			}
			boolean same = (m.file() != null && ModFiles.normalize(m.file()).equals(fresh))
				|| (m.loadedJar() != null && ModFiles.normalize(m.loadedJar()).equals(fresh));
			if (!same) {
				return m;
			}
		}
		return null;
	}

	/** Renames the new jar to .disabled so it does not load beside {@code other}, which has the same id. */
	public static Result keepOne(ManagedMod other, Path newJar) {
		String where = other.fileName().isEmpty() ? other.name() : other.fileName();
		return setAside(newJar, "Another copy of " + other.name() + " (" + where + ") would also load");
	}

	private static Result setAside(Path newJar, String why) {
		Path aside = ModFiles.disabledPath(newJar);
		try {
			Files.move(newJar, aside);
			return new Result(false, aside, false, ". " + why + ", so the new one is left off to keep two versions from loading. Sort it out in the Mods panel");
		} catch (IOException e) {
			return new Result(false, newJar, true, ". " + why + " and the new one could not be set aside, so both would load. Turn one off in the Mods panel");
		}
	}
}
