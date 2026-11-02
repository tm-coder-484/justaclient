package dev.justaclient.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.hud.HudFeature;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Feature profiles: which features are on, their settings, keys and HUD layout. Each profile is one JSON file in
 * {@code config/justaclient/profiles/}, so players can share them by copying files. The style tokens are not part of a
 * profile; they live in {@code justaclient.json}.
 */
public final class Profiles {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("profiles");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Profiles() {
	}

	public static String active() {
		return JacConfig.get().profile;
	}

	/** Profile names, sorted, always including the active one. */
	public static List<String> list() {
		List<String> names = new ArrayList<>();
		if (Files.isDirectory(DIR)) {
			try (Stream<Path> files = Files.list(DIR)) {
				files.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.forEach(names::add);
			} catch (IOException e) {
				Justaclient.LOGGER.warn("Could not list profiles in {}", DIR, e);
			}
		}
		if (!names.contains(active())) {
			names.add(active());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Loads the active profile into the features, falling back to defaults for anything missing. */
	public static void load() {
		Features.all().forEach(Feature::resetToDefaults);
		Path file = file(active());
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				apply(JsonParser.parseReader(reader).getAsJsonObject());
			} catch (IOException | JsonParseException | IllegalStateException e) {
				Justaclient.LOGGER.warn("Could not read profile {}, using defaults", file, e);
			}
		}
		save();
	}

	public static void save() {
		Path file = file(active());
		try {
			Files.createDirectories(DIR);
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(snapshot(), writer);
			}
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write profile {}", file, e);
		}
	}

	public static void switchTo(String name) {
		if (name.equals(active())) {
			return;
		}
		save();
		JacConfig.get().profile = name;
		JacConfig.save();
		load();
	}

	/** Copies the current setup into a new profile and makes it active. Returns its name. */
	public static String duplicate() {
		save();
		List<String> names = list();
		int n = 2;
		while (names.contains("Profile " + n)) {
			n++;
		}
		String name = "Profile " + n;
		JacConfig.get().profile = name;
		JacConfig.save();
		save();
		return name;
	}

	/** Deletes the active profile and switches to another. The last profile cannot be deleted. */
	public static void deleteActive() {
		List<String> names = list();
		if (names.size() <= 1) {
			return;
		}
		String doomed = active();
		try {
			Files.deleteIfExists(file(doomed));
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not delete profile {}", doomed, e);
			return;
		}
		names.remove(doomed);
		JacConfig.get().profile = names.getFirst();
		JacConfig.save();
		load();
	}

	/** Copies the active profile, with the current theme, to the clipboard as JSON so it can be pasted to a friend. */
	public static void exportToClipboard() {
		save();
		JsonObject root = snapshot();
		root.addProperty("name", active());
		root.add("style", GSON.toJsonTree(JacConfig.get().style));
		Minecraft.getInstance().keyboardHandler.setClipboard(GSON.toJson(root));
	}

	/**
	 * Makes a new profile from JSON on the clipboard (as written by {@link #exportToClipboard}) and switches to it. A theme
	 * in the JSON replaces the current one. Returns the new profile's name, or null if the clipboard held no profile.
	 */
	public static @Nullable String importFromClipboard() {
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(Minecraft.getInstance().keyboardHandler.getClipboard());
			if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("features")) {
				return null;
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			return null;
		}
		save();
		String base = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString().trim() : "Imported";
		base = base.isEmpty() ? "Imported" : base.substring(0, Math.min(32, base.length()));
		List<String> names = list();
		String name = base;
		for (int n = 2; names.contains(name); n++) {
			name = base + " " + n;
		}
		if (root.has("style") && root.get("style").isJsonObject()) {
			try {
				StyleTokens style = GSON.fromJson(root.get("style"), StyleTokens.class);
				if (style != null) {
					style.sanitize();
					JacConfig.get().style = style;
				}
			} catch (JsonParseException e) {
				Justaclient.LOGGER.warn("Ignoring the theme in a pasted profile", e);
			}
		}
		Features.all().forEach(Feature::resetToDefaults);
		apply(root);
		JacConfig.get().profile = name;
		JacConfig.save();
		save();
		return name;
	}

	private static Path file(String name) {
		String safe = name.replaceAll("[^A-Za-z0-9 _.-]", "_");
		return DIR.resolve(safe + ".json");
	}

	private static JsonObject snapshot() {
		JsonObject features = new JsonObject();
		for (Feature feature : Features.all()) {
			JsonObject f = new JsonObject();
			f.addProperty("enabled", feature.isEnabled());
			f.add(feature.toggleKey.id, feature.toggleKey.save());
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : feature.settings()) {
				settings.add(setting.id, setting.save());
			}
			f.add("settings", settings);
			if (feature instanceof HudFeature hud) {
				JsonObject pos = new JsonObject();
				pos.addProperty("x", hud.relX);
				pos.addProperty("y", hud.relY);
				f.add("position", pos);
			}
			features.add(feature.id, f);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("features", features);
		return root;
	}

	private static void apply(JsonObject root) {
		JsonObject features = root.getAsJsonObject("features");
		if (features == null) {
			return;
		}
		for (Feature feature : Features.all()) {
			JsonElement element = features.get(feature.id);
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject f = element.getAsJsonObject();
			if (f.has("enabled")) {
				feature.setEnabled(f.get("enabled").getAsBoolean());
			}
			feature.toggleKey.load(f.get(feature.toggleKey.id));
			JsonObject settings = f.getAsJsonObject("settings");
			if (settings != null) {
				for (Setting<?> setting : feature.settings()) {
					setting.load(settings.get(setting.id));
				}
			}
			JsonObject pos = f.getAsJsonObject("position");
			if (feature instanceof HudFeature hud && pos != null) {
				hud.relX = clamp01(pos.get("x"));
				hud.relY = clamp01(pos.get("y"));
			}
		}
	}

	private static float clamp01(JsonElement e) {
		return e == null ? 0f : Math.max(0f, Math.min(1f, e.getAsFloat()));
	}
}
