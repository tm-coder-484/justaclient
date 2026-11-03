package dev.justaclient.notes;

import dev.justaclient.Justaclient;
import dev.justaclient.common.Notes;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The one notepad and todo list, kept in {@code config/justaclient/notes.json}. */
public final class NotesStore {
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("notes.json");
	private static Notes notes;

	private NotesStore() {
	}

	public static Notes get() {
		if (notes == null) {
			notes = Notes.load(FILE);
		}
		return notes;
	}

	public static void save() {
		try {
			get().save(FILE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save notes", e);
		}
	}
}
