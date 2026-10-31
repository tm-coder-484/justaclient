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
import net.fabricmc.loader.api.FabricLoader;

/** Client settings, stored as JSON in {@code config/justaclient.json}. */
public final class JacConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient.json");
	private static JacConfig instance = new JacConfig();

	public int version = 1;
	public StyleTokens style = new StyleTokens();

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
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				Justaclient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(instance, writer);
			}
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
