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
 * The client's extra data for the multiplayer list: folders, favourites and when each server was last joined, keyed by
 * address. The server list itself stays in the game's {@code servers.dat}, so the vanilla screen still shows everything.
 */
public final class ServerFolders {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Meta {
		public @Nullable String folder;
		public boolean favourite;
		public long lastJoined;
	}

	public List<String> folders = new ArrayList<>();
	public Map<String, Meta> servers = new LinkedHashMap<>();

	public static ServerFolders load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				ServerFolders data = GSON.fromJson(reader, ServerFolders.class);
				if (data != null) {
					if (data.folders == null) {
						data.folders = new ArrayList<>();
					}
					data.folders.removeIf(f -> f == null || f.isBlank());
					if (data.servers == null) {
						data.servers = new LinkedHashMap<>();
					}
					data.servers.values().removeIf(m -> m == null);
					for (Meta m : data.servers.values()) {
						if (m.folder != null && !data.folders.contains(m.folder)) {
							m.folder = null;
						}
					}
					return data;
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				try {
					Files.move(file, file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".broken"));
				} catch (IOException ignored) {
					// Nothing more to do.
				}
			}
		}
		return new ServerFolders();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static String key(String address) {
		return address.trim().toLowerCase(Locale.ROOT);
	}

	public Meta meta(String address) {
		return servers.computeIfAbsent(key(address), k -> new Meta());
	}

	public @Nullable Meta peek(String address) {
		return servers.get(key(address));
	}

	/** Adds a folder; returns false if the name is blank or taken. */
	public boolean addFolder(String name) {
		String n = name.trim();
		if (n.isEmpty() || folders.stream().anyMatch(f -> f.equalsIgnoreCase(n))) {
			return false;
		}
		folders.add(n);
		return true;
	}

	public boolean renameFolder(String from, String to) {
		String n = to.trim();
		int i = folders.indexOf(from);
		if (i < 0 || n.isEmpty() || folders.stream().anyMatch(f -> f.equalsIgnoreCase(n) && !f.equals(from))) {
			return false;
		}
		folders.set(i, n);
		for (Meta m : servers.values()) {
			if (from.equals(m.folder)) {
				m.folder = n;
			}
		}
		return true;
	}

	/** Removes a folder; its servers go back to no folder. */
	public void removeFolder(String name) {
		folders.remove(name);
		for (Meta m : servers.values()) {
			if (name.equals(m.folder)) {
				m.folder = null;
			}
		}
	}

	/** Moves a folder up or down by {@code by} places. */
	public void moveFolder(String name, int by) {
		int i = folders.indexOf(name);
		int j = i + by;
		if (i < 0 || j < 0 || j >= folders.size()) {
			return;
		}
		folders.remove(i);
		folders.add(j, name);
	}
}
