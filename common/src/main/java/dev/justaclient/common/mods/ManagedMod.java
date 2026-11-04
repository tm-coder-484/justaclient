package dev.justaclient.common.mods;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A mod the Mods panel can show and maybe switch. {@code file} is the file to rename (null when locked), {@code loadedJar}
 * the jar the loader loaded this launch, and {@code lockReason} why it cannot be switched. {@code loaded} is true when it
 * runs in this launch, so its loader entry (icon, config screen) can be looked up by id.
 */
public record ManagedMod(String id, String name, String version, String description, List<String> authors, Map<String, String> links,
	@Nullable String icon, ModStatus status, @Nullable Path file, @Nullable Path loadedJar, @Nullable String lockReason, Set<String> providedIds,
	Set<String> requiredIds, boolean loaded, boolean library) {
	public String key() {
		Path path = loadedJar != null ? loadedJar : file;
		return path != null ? ModFiles.enabledName(path) : "id:" + id;
	}

	public String fileName() {
		return file != null ? file.getFileName().toString() : "";
	}

	public ManagedMod withStatus(ModStatus newStatus, @Nullable Path newFile) {
		return new ManagedMod(id, name, version, description, authors, links, icon, newStatus, newFile, loadedJar, lockReason, providedIds,
			requiredIds, loaded, library);
	}
}
