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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Saved sets of key binds (binding name to key, as the game writes them in options.txt), and which set each server uses.
 * {@code restore} holds the binds from before a server's set was applied, so they come back after leaving, even after a
 * crash.
 */
public final class KeybindProfiles {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
	public Map<String, String> servers = new LinkedHashMap<>();
	public @Nullable Map<String, String> restore;

	public static KeybindProfiles load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				KeybindProfiles data = GSON.fromJson(reader, KeybindProfiles.class);
				if (data != null) {
					if (data.profiles == null) {
						data.profiles = new LinkedHashMap<>();
					}
					data.profiles.values().removeIf(m -> m == null);
					data.profiles.keySet().removeIf(k -> k == null || k.isBlank());
					if (data.servers == null) {
						data.servers = new LinkedHashMap<>();
					}
					data.servers.values().removeIf(p -> p == null || !data.profiles.containsKey(p));
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
		return new KeybindProfiles();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp)) {
			GSON.toJson(this, writer);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public static String serverKey(String address) {
		return address.trim().toLowerCase(Locale.ROOT);
	}

	public @Nullable String profileFor(String address) {
		return servers.get(serverKey(address));
	}

	public void setProfileFor(String address, @Nullable String profile) {
		if (profile == null) {
			servers.remove(serverKey(address));
		} else {
			servers.put(serverKey(address), profile);
		}
	}

	public void removeProfile(String name) {
		profiles.remove(name);
		servers.values().removeIf(name::equals);
	}
}
