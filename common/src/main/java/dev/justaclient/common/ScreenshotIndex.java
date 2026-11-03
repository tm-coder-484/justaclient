package dev.justaclient.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the client knows about each screenshot, by file name: where and when it was taken (when auto-tagging is on) and the
 * player's tags and favourites. The pictures themselves stay in the game's screenshots folder.
 */
public final class ScreenshotIndex {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Shot {
		public List<String> tags = new ArrayList<>();
		public boolean favourite;
		public @Nullable String world;
		public @Nullable String worldKey;
		public @Nullable String dimension;
		public @Nullable String biome;
		public @Nullable String weather;
		public @Nullable String time;
		public @Nullable Integer x;
		public @Nullable Integer y;
		public @Nullable Integer z;
		public long taken;

		/** Every word a search can match, lower case. */
		public String searchText() {
			StringBuilder b = new StringBuilder();
			for (String part : new String[] {world, dimension, biome, weather, time}) {
				if (part != null) {
					b.append(part).append(' ');
				}
			}
			for (String t : tags) {
				b.append(t).append(' ');
			}
			return b.toString().toLowerCase(Locale.ROOT);
		}
	}

	public Map<String, Shot> shots = new LinkedHashMap<>();

	public static ScreenshotIndex load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				ScreenshotIndex data = GSON.fromJson(reader, ScreenshotIndex.class);
				if (data != null) {
					if (data.shots == null) {
						data.shots = new LinkedHashMap<>();
					}
					data.shots.values().removeIf(s -> s == null);
					for (Shot s : data.shots.values()) {
						if (s.tags == null) {
							s.tags = new ArrayList<>();
						}
						s.tags.removeIf(t -> t == null || t.isBlank());
					}
					return data;
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				try {
					Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// Nothing more to do.
				}
			}
		}
		return new ScreenshotIndex();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public Shot get(String fileName) {
		return shots.computeIfAbsent(fileName, k -> new Shot());
	}

	public @Nullable Shot peek(String fileName) {
		return shots.get(fileName);
	}

	/** Adds a tag (trimmed, lower case, no duplicates). Returns false if it was blank or already there. */
	public static boolean addTag(Shot shot, String tag) {
		String t = tag.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty() || shot.tags.contains(t)) {
			return false;
		}
		shot.tags.add(t);
		return true;
	}

	/** Forgets entries for files that are gone. */
	public void prune(java.util.Set<String> existing) {
		shots.keySet().removeIf(name -> !existing.contains(name));
	}
}
