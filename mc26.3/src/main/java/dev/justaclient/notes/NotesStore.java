package dev.justaclient.notes;

import dev.justaclient.Justaclient;
import dev.justaclient.common.Notes;
import dev.justaclient.world.WorldStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Two notepads and todo lists: one for every world in {@code config/justaclient/notes.json}, and one per world in that
 * world's folder of the {@link WorldStore}. The Notes page switches between them while you are in a world.
 */
public final class NotesStore {
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("notes.json");
	private static @Nullable Notes global;
	private static @Nullable Notes world;
	private static @Nullable Path worldFile;
	/** True when the Notes page shows the current world's notes. */
	private static boolean perWorld;

	private NotesStore() {
	}

	public static Notes global() {
		if (global == null) {
			global = Notes.load(FILE);
		}
		return global;
	}

	/** The current world's notes, or null in menus. Reloaded when the world changes. */
	public static @Nullable Notes world() {
		Path folder = WorldStore.folder();
		Path file = folder == null ? null : folder.resolve("notes.json");
		if (!Objects.equals(file, worldFile)) {
			if (world != null && worldFile != null) {
				save(world, worldFile);
			}
			worldFile = file;
			world = file == null ? null : Notes.load(file);
		}
		return world;
	}

	public static boolean perWorld() {
		return perWorld && world() != null;
	}

	public static void setPerWorld(boolean on) {
		perWorld = on;
	}

	/** The notes the Notes page is showing. */
	public static Notes get() {
		Notes w = perWorld ? world() : null;
		return w != null ? w : global();
	}

	/** Pinned todos from both lists, for the HUD. */
	public static List<Notes.Todo> pinned() {
		List<Notes.Todo> out = new ArrayList<>();
		for (Notes.Todo t : global().todos) {
			if (t.pinned) {
				out.add(t);
			}
		}
		Notes w = world();
		if (w != null) {
			for (Notes.Todo t : w.todos) {
				if (t.pinned) {
					out.add(t);
				}
			}
		}
		return out;
	}

	/** Saves the notes the page is showing. */
	public static void save() {
		Notes w = perWorld ? world() : null;
		if (w != null && worldFile != null) {
			save(w, worldFile);
		} else {
			save(global(), FILE);
		}
	}

	/** Saves both lists, e.g. when the game closes. */
	public static void saveAll() {
		if (global != null) {
			save(global, FILE);
		}
		if (world != null && worldFile != null) {
			save(world, worldFile);
		}
	}

	private static void save(Notes notes, Path file) {
		try {
			java.nio.file.Files.createDirectories(file.getParent());
			notes.save(file);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save notes to {}", file, e);
		}
	}
}
