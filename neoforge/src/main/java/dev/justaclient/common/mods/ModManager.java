package dev.justaclient.common.mods;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import dev.justaclient.platform.PlatformLoader;
import dev.justaclient.platform.PlatformLoader.ModContainer;
import dev.justaclient.platform.PlatformLoader.ContactInformation;
import dev.justaclient.platform.PlatformLoader.ModDependency;
import dev.justaclient.platform.PlatformLoader.ModMetadata;
import dev.justaclient.platform.PlatformLoader.ModOrigin;
import dev.justaclient.platform.PlatformLoader.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists the installed mods, loaded or switched off, and switches them by renaming their jars. Changes apply on the next
 * start. Adapted from Tom's server-switcher mod (CC0).
 */
public final class ModManager {
	private static final Logger LOG = LoggerFactory.getLogger("justaclient");
	public static final String SELF = "justaclient";
	/** Mods whose own screens do the same job; the client steps aside when one is installed. */
	public static final String SERVER_SWITCHER = "server-switcher";

	private ModManager() {
	}

	public static boolean serverSwitcherInstalled() {
		return PlatformLoader.getInstance().isModLoaded(SERVER_SWITCHER);
	}

	public static Path modsFolder() {
		String override = System.getProperty("justaclient.modsFolder");
		Path folder = override != null && !override.isBlank() ? Path.of(override) : PlatformLoader.getInstance().getGameDir().resolve("mods");
		return ModFiles.normalize(folder);
	}

	public static List<ManagedMod> scan() {
		PlatformLoader loader = PlatformLoader.getInstance();
		List<ManagedMod> result = new ArrayList<>();
		Map<String, Path> loadedJars = new HashMap<>();
		Path modsFolder = modsFolder();
		Set<Path> folders = new LinkedHashSet<>();
		folders.add(modsFolder);
		for (ModContainer container : loader.getAllMods()) {
			ModMetadata meta = container.getMetadata();
			if (container.getContainingMod().isPresent() || meta.getType().equals("builtin")) {
				continue;
			}
			Set<String> provided = new LinkedHashSet<>();
			Set<String> required = new LinkedHashSet<>();
			collectIds(container, provided, required);
			required.removeAll(provided);
			Path jar = singleJar(container.getOrigin());
			if (jar == null || !jar.startsWith(modsFolder)) {
				result.add(locked(meta, "Not a jar in the mods folder", provided, required));
				continue;
			}
			loadedJars.put(jar.toString(), jar);
			folders.add(jar.getParent());
			if (meta.getId().equals(SELF)) {
				result.add(locked(meta, "This is the client itself", provided, required));
				continue;
			}
			Path disabled = ModFiles.disabledPath(jar);
			ModStatus status;
			Path file;
			if (DeferredRenames.isPending(jar)) {
				status = ModStatus.DISABLE_PENDING;
				file = jar;
			} else if (Files.exists(jar)) {
				status = ModStatus.ENABLED;
				file = jar;
			} else if (Files.exists(disabled)) {
				status = ModStatus.DISABLE_PENDING;
				file = disabled;
			} else {
				result.add(locked(meta, "The file " + jar.getFileName() + " is gone", provided, required));
				continue;
			}
			result.add(fromLoader(meta, status, file, jar, null, provided, required));
		}
		for (Path folder : folders) {
			for (Path path : list(folder)) {
				boolean disabled = ModFiles.isDisabledJar(path);
				if ((!disabled && !ModFiles.isJar(path)) || loadedJars.containsKey(ModFiles.enabledName(path))) {
					continue;
				}
				JarMetadata meta = JarMetadata.read(path);
				if (meta != null) {
					ModStatus status = disabled ? ModStatus.DISABLED : ModStatus.ENABLE_PENDING;
					result.add(new ManagedMod(meta.id(), meta.name(), meta.version(), meta.description(), List.of(), Map.of(), meta.icon(), status, path, null,
						null, meta.providedIds(), meta.requiredIds(), false, false));
				}
			}
		}
		result.sort(Comparator.<ManagedMod, Boolean>comparing(mod -> mod.status() == ModStatus.LOCKED)
			.thenComparing(mod -> mod.name().toLowerCase(Locale.ROOT)).thenComparing(ManagedMod::fileName));
		return result;
	}

	/** Names of the mods that would stop working if {@code mod} were switched off. */
	public static List<String> dependentsOf(ManagedMod mod, List<ManagedMod> all) {
		Set<String> stillProvided = providedNextLaunch(all, mod);
		List<String> names = new ArrayList<>();
		for (ManagedMod other : all) {
			if (other == mod || !other.status().activeNextLaunch()) {
				continue;
			}
			for (String id : other.requiredIds()) {
				if (mod.providedIds().contains(id) && !stillProvided.contains(id)) {
					names.add(other.name());
					break;
				}
			}
		}
		return names;
	}

	/** Ids {@code mod} needs that no mod will provide on the next start. */
	public static List<String> missingDependencies(ManagedMod mod, List<ManagedMod> all) {
		Set<String> provided = providedNextLaunch(all, mod);
		List<String> missing = new ArrayList<>();
		for (String id : mod.requiredIds()) {
			if (!provided.contains(id)) {
				missing.add(id);
			}
		}
		return missing;
	}

	private static Set<String> providedNextLaunch(List<ManagedMod> all, ManagedMod excluding) {
		Set<String> ids = new HashSet<>();
		for (ModContainer container : PlatformLoader.getInstance().getAllMods()) {
			if (container.getMetadata().getType().equals("builtin")) {
				ids.add(container.getMetadata().getId());
				ids.addAll(container.getMetadata().getProvides());
			}
		}
		for (ManagedMod other : all) {
			if (other != excluding && other.status().activeNextLaunch()) {
				ids.addAll(other.providedIds());
			}
		}
		return ids;
	}

	public static ToggleResult toggle(ManagedMod mod) {
		Path file = mod.file();
		if (mod.status() == ModStatus.LOCKED || file == null) {
			return ToggleResult.failed(mod.lockReason() != null ? mod.lockReason() : "This mod can't be switched");
		}
		return switch (mod.status()) {
			case ENABLED -> {
				Path target = ModFiles.disabledPath(file);
				ToggleResult result = rename(file, target);
				if (result.outcome() == ToggleResult.Outcome.FAILED && Files.exists(file) && !Files.exists(target)) {
					// Windows: the jar is in use. Rename it after the game closes.
					DeferredRenames.add(file, target);
					yield ToggleResult.deferred();
				}
				yield result.outcome() == ToggleResult.Outcome.RENAMED ? ToggleResult.restartRequired() : result;
			}
			case DISABLE_PENDING -> {
				if (DeferredRenames.isPending(file)) {
					DeferredRenames.remove(file);
					yield ToggleResult.undone();
				}
				ToggleResult result = rename(file, ModFiles.enabledPath(file));
				yield result.outcome() == ToggleResult.Outcome.RENAMED ? ToggleResult.undone() : result;
			}
			case DISABLED -> {
				ToggleResult result = rename(file, ModFiles.enabledPath(file));
				yield result.outcome() == ToggleResult.Outcome.RENAMED ? ToggleResult.restartRequired() : result;
			}
			case ENABLE_PENDING -> {
				ToggleResult result = rename(file, ModFiles.disabledPath(file));
				yield result.outcome() == ToggleResult.Outcome.RENAMED ? ToggleResult.undone() : result;
			}
			case LOCKED -> ToggleResult.failed("This mod can't be switched");
		};
	}

	private static ToggleResult rename(Path from, Path to) {
		if (!Files.exists(from)) {
			return ToggleResult.failed("The file " + from.getFileName() + " is gone");
		}
		if (Files.exists(to)) {
			return ToggleResult.failed(to.getFileName() + " already exists");
		}
		try {
			Files.move(from, to);
			LOG.info("Renamed {} to {}", from, to.getFileName());
			return ToggleResult.renamed();
		} catch (IOException e) {
			LOG.warn("Couldn't rename {} to {}", from, to.getFileName(), e);
			return ToggleResult.failed("Couldn't rename: " + e.getClass().getSimpleName());
		}
	}

	private static ManagedMod fromLoader(ModMetadata meta, ModStatus status, Path file, Path jar, String lockReason, Set<String> provided,
		Set<String> required) {
		List<String> authors = new ArrayList<>();
		for (Person person : meta.getAuthors()) {
			authors.add(person.getName());
		}
		Map<String, String> links = new LinkedHashMap<>();
		ContactInformation contact = meta.getContact();
		contact.get("homepage").ifPresent(v -> links.put("Website", v));
		contact.get("sources").ifPresent(v -> links.put("Source", v));
		contact.get("issues").ifPresent(v -> links.put("Issues", v));
		boolean library = hasLibraryBadge(meta);
		return new ManagedMod(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), meta.getDescription(), authors, links,
			meta.getIconPath(64).orElse(null), status, file, jar, lockReason, provided, required, true, library);
	}

	private static boolean hasLibraryBadge(ModMetadata meta) {
        return meta.library();
    }

	private static ManagedMod locked(ModMetadata meta, String reason, Set<String> provided, Set<String> required) {
		return fromLoader(meta, ModStatus.LOCKED, null, null, reason, provided, required);
	}

	private static void collectIds(ModContainer container, Set<String> provided, Set<String> required) {
		ModMetadata meta = container.getMetadata();
        Path file=singleJar(container.getOrigin());
        if(file!=null) {
            JarMetadata metadata=JarMetadata.read(file);
            if(metadata!=null) { provided.addAll(metadata.providedIds());required.addAll(metadata.requiredIds()); }
        }
		provided.add(meta.getId());
		provided.addAll(meta.getProvides());
		for (ModDependency dependency : meta.getDependencies()) {
			if (dependency.getKind() == ModDependency.Kind.DEPENDS) {
				required.add(dependency.getModId());
			}
		}
		for (ModContainer nested : container.getContainedMods()) {
			collectIds(nested, provided, required);
		}
	}

	private static Path singleJar(ModOrigin origin) {
		if (origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) {
			return null;
		}
		Path path = ModFiles.normalize(origin.getPaths().getFirst());
		return ModFiles.isJar(path) && !Files.isDirectory(path) ? path : null;
	}

	private static List<Path> list(Path folder) {
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(folder)) {
			return stream.filter(Files::isRegularFile).map(ModFiles::normalize).toList();
		} catch (IOException e) {
			LOG.warn("Couldn't list {}", folder, e);
			return List.of();
		}
	}
}
