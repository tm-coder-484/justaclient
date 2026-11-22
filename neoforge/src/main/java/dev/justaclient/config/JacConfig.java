package dev.justaclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Colors;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import dev.justaclient.platform.PlatformLoader;

/** Client settings, stored as JSON in {@code config/justaclient.json}. */
public final class JacConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Path FILE = PlatformLoader.getInstance().getConfigDir().resolve("justaclient.json");
	private static JacConfig instance = new JacConfig();

	public int version = 1;
	public StyleTokens style = new StyleTokens();
	/** Name of the active feature profile in {@code config/justaclient/profiles/}. */
	public String profile = "Default";
	/** Recent searches in the menu, newest first. */
	public java.util.List<String> searchHistory = new java.util.ArrayList<>();
	/** The HUD editor's top bar was folded away (H or its button brings it back). */
	public boolean hudEditorBarHidden;

	public static JacConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				JacConfig loaded = GSON.fromJson(reader, JacConfig.class);
				if (loaded != null) {
					if (loaded.style == null) {
						loaded.style = new StyleTokens();
					}
					loaded.style.sanitize();
					if (loaded.profile == null || loaded.profile.isBlank()) {
						loaded.profile = "Default";
					}
					if (loaded.searchHistory == null) {
						loaded.searchHistory = new java.util.ArrayList<>();
					}
					loaded.searchHistory.removeIf(q -> q == null || q.isBlank());
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				Justaclient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
				// Kept aside, since the save below would otherwise write the defaults over it.
				try {
					Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + "." + System.currentTimeMillis() + ".broken"));
				} catch (IOException moveFailed) {
					Justaclient.LOGGER.warn("Could not set aside {}", FILE, moveFailed);
				}
			}
		}
		save();
	}

	/** Puts a search at the front of the history, keeping the newest six. */
	public static void rememberSearch(String query) {
		String q = query.strip();
		if (q.length() < 2) {
			return;
		}
		instance.searchHistory.removeIf(old -> old.equalsIgnoreCase(q));
		instance.searchHistory.addFirst(q);
		while (instance.searchHistory.size() > 6) {
			instance.searchHistory.removeLast();
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			// Written beside it and moved over it, so a crash mid-write cannot leave half a file.
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	/** Resets every style token to the dark glass defaults. */
	public static void resetStyle() {
		instance.style = new StyleTokens();
	}

	// Resolved colours, so widgets never parse hex strings themselves.

	public static int accent() {
		StyleTokens s = instance.style;
		return s.rainbowAccent ? Colors.rainbow(0f, 0.6f, 1f) : Colors.parse(s.accent, 0xFF4C8DFF);
	}

	/** Accent at a position along the gradient (0 = start, 1 = end). Solid accents ignore {@code t}. */
	public static int accentAt(float t) {
		StyleTokens s = instance.style;
		if (s.rainbowAccent) {
			return Colors.rainbow(t * 0.25f, 0.6f, 1f);
		}
		int start = Colors.parse(s.accent, 0xFF4C8DFF);
		return s.accentEnd == null ? start : Colors.lerp(start, Colors.parse(s.accentEnd, start), t);
	}

	/** Panel surface colour with the opacity token applied. */
	public static int surface() {
		StyleTokens s = instance.style;
		return Colors.withAlpha(Colors.parse(s.surface, 0xFF16181E), Math.round(s.surfaceOpacity * 255));
	}
}
