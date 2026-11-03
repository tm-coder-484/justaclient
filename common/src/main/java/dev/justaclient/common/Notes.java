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
					Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// Nothing more to do.
				}
			}
		}
		return new Notes();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(this, writer);
		}
	}
}
