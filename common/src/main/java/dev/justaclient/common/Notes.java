package dev.justaclient.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The notepad and todo list, saved as one JSON file. */
public final class Notes {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Todo {
		public String text = "";
		public boolean done;
		public boolean pinned;

		public Todo() {
		}

		public Todo(String text) {
			this.text = text;
		}
	}

	public String notepad = "";
	public List<Todo> todos = new ArrayList<>();

	public static Notes load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Notes notes = GSON.fromJson(reader, Notes.class);
				if (notes != null) {
					if (notes.notepad == null) {
						notes.notepad = "";
					}
					if (notes.todos == null) {
						notes.todos = new ArrayList<>();
					}
					notes.todos.removeIf(t -> t == null || t.text == null);
					return notes;
				}
			} catch (IOException | JsonParseException e) {
				// A broken file starts a fresh notepad rather than crashing; the old file is kept beside it.
				try {
					Files.copy(file, file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".broken"));
				} catch (IOException ignored) {
					// Nothing more to do.
				}
			}
		}
		return new Notes();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		// Saved about once a second while typing; written beside it and moved over it so a crash cannot cut it short.
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}
}
