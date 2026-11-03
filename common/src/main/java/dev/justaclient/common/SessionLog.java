package dev.justaclient.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Past sessions, newest first: where you played, for how long, what you did and what you carried when you left. */
public final class SessionLog {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Session {
		public String world = "";
		/** ISO local date-time the session started. */
		public String started = "";
		public long seconds;
		public int kills;
		public int deaths;
		public int blocks;
		public double distance;
		/** Item id to how many you picked up. */
		public Map<String, Integer> pickedUp = new LinkedHashMap<>();
		/** What was in your inventory when you left, biggest stacks first. */
		public List<Carried> carried = new ArrayList<>();
	}

	public static final class Carried {
		public String item = "";
		public int count;

		public Carried() {
		}

		public Carried(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	private SessionLog() {
	}

	public static List<Session> load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				List<Session> list = GSON.fromJson(reader, new TypeToken<List<Session>>() {
				}.getType());
				if (list != null) {
					list.removeIf(s -> s == null || s.world == null || s.started == null);
					for (Session s : list) {
						if (s.pickedUp == null) {
							s.pickedUp = new LinkedHashMap<>();
						}
						if (s.carried == null) {
							s.carried = new ArrayList<>();
						}
					}
					return list;
				}
			} catch (IOException | JsonParseException e) {
				// Start a fresh log; the broken one is left on disk.
			}
		}
		return new ArrayList<>();
	}

	/** Adds {@code session} at the front, keeps at most {@code keep} and writes the file. */
	public static void add(Path file, List<Session> list, Session session, int keep) throws IOException {
		list.addFirst(session);
		while (list.size() > Math.max(1, keep)) {
			list.removeLast();
		}
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(list, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
