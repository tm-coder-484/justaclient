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
import java.nio.file.StandardCopyOption;
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
		Path file = file(active());
		JsonObject root = null;
		boolean preserved = true;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed.isJsonObject() && valid(parsed.getAsJsonObject())) {
					root = parsed.getAsJsonObject();
				} else {
					throw new JsonParseException("not a profile");
				}
			} catch (IOException | RuntimeException e) {
				Justaclient.LOGGER.warn("Could not read profile {}, using defaults", file, e);
				preserved = keepBroken(file);
			}
		}
		JsonObject loaded = root;
		if (!applySafely(loaded == null ? new JsonObject() : loaded, true)) {
			preserved = keepBroken(file);
			applySafely(new JsonObject(), true);
		}
		if (!preserved) {
			// The unreadable file could not be moved aside: never write over it. The defaults go to a new profile instead.
			String recovered = uniqueName(active() + " (recovered)");
			Justaclient.LOGGER.warn("Leaving {} untouched; using the new profile {}", file.getFileName(), recovered);
			JacConfig.get().profile = recovered;
			JacConfig.save();
		}
		save();
	}

	private static String uniqueName(String base) {
		String name = base;
		int n = 2;
		while (Files.exists(file(name))) {
			name = base + " " + n++;
		}
		return name;
	}

	/**
	 * Moves a profile that could not be read aside as {@code name.json.<time>.broken} (with a number added if that name is
	 * taken), so saving defaults does not destroy it. Returns false if the file is still there.
	 */
	/** Profile files set aside as unreadable since the game started, for the self-test. */
	private static final List<String> KEPT_ASIDE = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static List<String> keptAside() {
		return List.copyOf(KEPT_ASIDE);
	}

	static boolean keepBroken(Path file) {
		if (!Files.exists(file)) {
			return true;
		}
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		for (int n = 0; n < 100; n++) {
			Path aside = file.resolveSibling(file.getFileName() + "." + stamp + (n == 0 ? "" : "-" + n) + ".broken");
			if (Files.exists(aside)) {
				continue;
			}
			try {
				Files.move(file, aside);
				Justaclient.LOGGER.warn("Kept the unreadable profile as {}", aside.getFileName());
				KEPT_ASIDE.add(file.getFileName().toString());
				return true;
			} catch (java.nio.file.FileAlreadyExistsException e) {
				// Taken in between; try the next name.
			} catch (IOException e) {
				Justaclient.LOGGER.warn("Could not move the unreadable profile {} aside", file, e);
				return false;
			}
		}
		return false;
	}

	public static void save() {
		Path file = file(active());
		try {
			Files.createDirectories(DIR);
			// Written beside it and moved over it, so a crash mid-write cannot leave half a profile.
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(snapshot(), writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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

	/** Where {@link #backup} keeps copies, next to the profiles folder. */
	public static final Path BACKUPS = DIR.resolveSibling("backups");

	/**
	 * Writes a timestamped copy of the active profile (with the theme) to {@link #BACKUPS} and returns its file name, or
	 * null if it could not be written. Only the newest 20 backups are kept.
	 */
	public static @Nullable String backup(String reason) {
		String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
		String name = file(active()).getFileName().toString().replace(".json", "") + "_" + reason + "_" + stamp + ".json";
		try {
			Files.createDirectories(BACKUPS);
			JsonObject root = snapshot();
			root.add("style", GSON.toJsonTree(JacConfig.get().style));
			try (Writer writer = Files.newBufferedWriter(BACKUPS.resolve(name))) {
				GSON.toJson(root, writer);
			}
			// A reset's backup is its only undo, so launch and timed backups do not push it out; it is pruned on its own.
			for (boolean resets : new boolean[] {false, true}) {
				try (Stream<Path> files = Files.list(BACKUPS)) {
					List<Path> old = files.filter(f -> f.toString().endsWith(".json") && f.getFileName().toString().contains("_before-reset_") == resets)
						.sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified())).skip(keepBackups()).toList();
					for (Path f : old) {
						Files.deleteIfExists(f);
					}
				}
			}
			return name;
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not back up profile {}", active(), e);
			return null;
		}
	}

	private static int keepBackups() {
		dev.justaclient.feature.impl.ProfileBackupFeature f = Features.get(dev.justaclient.feature.impl.ProfileBackupFeature.class);
		return f == null ? 20 : f.keep();
	}

	/** Backs up the active profile, then puts every feature, setting, key and widget back to its default. */
	public static @Nullable String resetAll() {
		String saved = backup("before-reset");
		if (saved == null) {
			// Without a backup a reset could not be undone, so nothing changes.
			return null;
		}
		Features.all().forEach(Feature::resetToDefaults);
		save();
		return saved;
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
		JsonObject root = parseProfile(Minecraft.getInstance().keyboardHandler.getClipboard());
		return root == null ? null : importJson(root, "Imported", true);
	}

	/** The JSON of a profile, or null if the text is not one. */
	public static @Nullable JsonObject parseProfile(String text) {
		try {
			JsonElement parsed = JsonParser.parseString(text);
			if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("features") || !valid(parsed.getAsJsonObject())) {
				return null;
			}
			return parsed.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			return null;
		}
	}

	/**
	 * Makes a new profile from {@code root}: the current setup with whatever the JSON holds laid over it, so a file that
	 * only has a HUD layout changes only the layout. Switches to it and returns its name.
	 */
	public static @Nullable String importJson(JsonObject root, String fallbackName, boolean withTheme) {
		if (!valid(root)) {
			return null;
		}
		save();
		String base = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString().trim() : fallbackName;
		// Names become file names, so clean the name first and check the file, or "A/B" would overwrite "A_B".
		base = safeName(base);
		base = base.isBlank() ? fallbackName : base.substring(0, Math.min(32, base.length())).trim();
		String name = base;
		for (int n = 2; Files.exists(file(name)) || containsIgnoreCase(list(), name); n++) {
			name = base + " " + n;
		}
		StyleTokens oldStyle = JacConfig.get().style;
		if (withTheme && root.has("style") && root.get("style").isJsonObject()) {
			try {
				StyleTokens style = GSON.fromJson(root.get("style"), StyleTokens.class);
				if (style != null) {
					style.sanitize();
					JacConfig.get().style = style;
				}
			} catch (JsonParseException e) {
				Justaclient.LOGGER.warn("Ignoring the theme in an imported profile", e);
			}
		}
		if (!applySafely(root, false)) {
			JacConfig.get().style = oldStyle;
			return null;
		}
		JacConfig.get().profile = name;
		JacConfig.save();
		save();
		return name;
	}

	private static boolean containsIgnoreCase(List<String> names, String name) {
		return names.stream().anyMatch(n -> n.equalsIgnoreCase(name));
	}

	/**
	 * True if {@code root} has the shape of a profile all the way down: {@code features} is an object of objects whose
	 * {@code enabled} is a boolean, {@code settings} an object and {@code position} an object of numbers. Checked before
	 * anything live changes, so a bad file cannot half apply.
	 */
	public static boolean valid(JsonObject root) {
		JsonElement features = root.get("features");
		if (features == null) {
			return true;
		}
		if (!features.isJsonObject()) {
			return false;
		}
		for (var entry : features.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				return false;
			}
			JsonObject f = entry.getValue().getAsJsonObject();
			JsonElement enabled = f.get("enabled");
			if (enabled != null && !(enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean())) {
				return false;
			}
			JsonElement settings = f.get("settings");
			if (settings != null && !settings.isJsonObject()) {
				return false;
			}
			JsonElement pos = f.get("position");
			if (pos != null) {
				if (!pos.isJsonObject()) {
					return false;
				}
				for (var p : pos.getAsJsonObject().entrySet()) {
					if (!p.getValue().isJsonPrimitive() || !p.getValue().getAsJsonPrimitive().isNumber()) {
						return false;
					}
				}
			}
		}
		JsonElement style = root.get("style");
		return style == null || style.isJsonObject();
	}

	/**
	 * Applies {@code root}; if anything in it still fails part way (a setting with a value of the wrong type), puts back
	 * the state from before and returns false. {@code fromDefaults} starts from defaults rather than the current setup.
	 */
	private static boolean applySafely(JsonObject root, boolean fromDefaults) {
		if (!valid(root)) {
			return false;
		}
		JsonObject before = snapshot();
		try {
			apply(root, fromDefaults);
			return true;
		} catch (RuntimeException e) {
			Justaclient.LOGGER.warn("Could not apply a profile, keeping the previous setup", e);
			try {
				apply(before, false);
			} catch (RuntimeException again) {
				Justaclient.LOGGER.error("Could not restore the previous setup", again);
			}
			return false;
		}
	}

	/** Which parts of a profile an export holds. */
	public record Parts(boolean toggles, boolean settings, boolean keys, boolean hud, boolean theme) {
		public boolean any() {
			return toggles || settings || keys || hud || theme;
		}
	}

	/** Where exported profile files go. */
	public static final Path EXPORTS = DIR.resolveSibling("exports");

	/** Writes the chosen parts of the active profile to {@link #EXPORTS} and returns the file. */
	public static Path exportToFile(Parts parts) throws IOException {
		save();
		JsonObject full = snapshot();
		JsonObject features = new JsonObject();
		for (var entry : full.getAsJsonObject("features").entrySet()) {
			JsonObject f = entry.getValue().getAsJsonObject();
			JsonObject out = new JsonObject();
			if (parts.toggles() && f.has("enabled")) {
				out.add("enabled", f.get("enabled"));
			}
			if (parts.keys() && f.has("toggle_key")) {
				out.add("toggle_key", f.get("toggle_key"));
			}
			if (parts.settings() && f.has("settings")) {
				out.add("settings", f.get("settings"));
			}
			if (parts.hud() && f.has("position")) {
				// The scale lives with the settings, but a layout without it would lose every widget's size.
				out.add("position", f.get("position"));
			}
			if (!out.isEmpty()) {
				features.add(entry.getKey(), out);
			}
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("name", active());
		JsonObject holds = new JsonObject();
		holds.addProperty("toggles", parts.toggles());
		holds.addProperty("settings", parts.settings());
		holds.addProperty("keys", parts.keys());
		holds.addProperty("hud", parts.hud());
		holds.addProperty("theme", parts.theme());
		root.add("holds", holds);
		root.add("features", features);
		if (parts.theme()) {
			root.add("style", GSON.toJsonTree(JacConfig.get().style));
		}
		Files.createDirectories(EXPORTS);
		String stamp = exportStamp(java.time.LocalDateTime.now());
		String base = exportBase(stamp);
		// Two exports in the same minute get " (2)", " (3)"...: an export never writes over a file already there.
		for (int n = 1; ; n++) {
			Path file = EXPORTS.resolve(base + (n == 1 ? "" : " (" + n + ")") + ".json");
			try (Writer writer = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
				GSON.toJson(root, writer);
				return file;
			} catch (java.nio.file.FileAlreadyExistsException taken) {
				if (n >= 999) {
					throw taken;
				}
			}
		}
	}

	/** The file name (without ".json") an export of the active profile made at this minute stamp starts from. */
	public static String exportBase(String stamp) {
		return file(active()).getFileName().toString().replace(".json", "") + "_" + stamp;
	}

	/** The minute stamp exports are named with. */
	public static String exportStamp(java.time.LocalDateTime when) {
		return when.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
	}

	/** A file that can be imported, with where it was found. */
	public record ImportFile(Path path, String source) {
	}

	/** Profile files in the exports and backups folders and the Downloads folder, newest first. */
	public static List<ImportFile> importable() {
		List<ImportFile> out = new ArrayList<>();
		Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
		Object[][] places = {{EXPORTS, "Exports"}, {BACKUPS, "Backups"}, {downloads, "Downloads"}};
		for (Object[] place : places) {
			Path dir = (Path) place[0];
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(f -> f.toString().endsWith(".json") && Files.isRegularFile(f))
					.filter(f -> dir != downloads || f.toFile().length() < 2_000_000 && looksLikeProfile(f))
					.forEach(f -> out.add(new ImportFile(f, (String) place[1])));
			} catch (IOException e) {
				Justaclient.LOGGER.warn("Could not list {}", dir, e);
			}
		}
		out.sort((a, b) -> Long.compare(b.path().toFile().lastModified(), a.path().toFile().lastModified()));
		return out.size() > 40 ? out.subList(0, 40) : out;
	}

	private static boolean looksLikeProfile(Path f) {
		try {
			return parseProfile(Files.readString(f)) != null;
		} catch (IOException e) {
			return false;
		}
	}

	/** What importing a file would change, for the preview before you import. */
	public record Preview(String name, int features, int toggled, int settingsChanged, boolean theme, boolean hud, boolean keys,
		List<String> changedNames, @Nullable JsonObject root) {
	}

	public static Preview preview(Path path) {
		JsonObject root;
		try {
			root = parseProfile(Files.readString(path));
		} catch (IOException e) {
			root = null;
		}
		if (root == null) {
			return new Preview(path.getFileName().toString(), 0, 0, 0, false, false, false, List.of(), null);
		}
		save();
		JsonObject now = snapshot().getAsJsonObject("features");
		JsonObject incoming = root.getAsJsonObject("features");
		int toggled = 0;
		int changed = 0;
		boolean hud = false;
		boolean keys = false;
		List<String> names = new ArrayList<>();
		for (Feature feature : Features.all()) {
			JsonElement e = incoming.get(feature.id);
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			JsonObject f = e.getAsJsonObject();
			JsonObject current = now.getAsJsonObject(feature.id);
			boolean differs = false;
			if (f.has("enabled") && !same(f.get("enabled"), current.get("enabled"))) {
				toggled++;
				differs = true;
			}
			if (f.has("toggle_key")) {
				keys = true;
				differs |= !same(f.get("toggle_key"), current.get("toggle_key"));
			}
			if (f.has("position")) {
				hud = true;
				differs |= !same(f.get("position"), current.get("position"));
			}
			JsonObject settings = f.getAsJsonObject("settings");
			JsonObject currentSettings = current.getAsJsonObject("settings");
			if (settings != null && currentSettings != null) {
				for (var s : settings.entrySet()) {
					if (currentSettings.has(s.getKey()) && !same(s.getValue(), currentSettings.get(s.getKey()))) {
						changed++;
						differs = true;
					}
				}
			}
			if (differs) {
				names.add(feature.name);
			}
		}
		String name = root.has("name") && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : path.getFileName().toString();
		return new Preview(name, incoming.size(), toggled, changed, root.has("style"), hud, keys, names, root);
	}

	/** JSON equality that treats numbers within a hair of each other as the same, since floats round-trip loosely. */
	private static boolean same(@Nullable JsonElement a, @Nullable JsonElement b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a.isJsonPrimitive() && b.isJsonPrimitive() && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
			return Math.abs(a.getAsDouble() - b.getAsDouble()) < 1e-4;
		}
		if (a.isJsonObject() && b.isJsonObject()) {
			var ao = a.getAsJsonObject();
			var bo = b.getAsJsonObject();
			if (!ao.keySet().equals(bo.keySet())) {
				return false;
			}
			for (String key : ao.keySet()) {
				if (!same(ao.get(key), bo.get(key))) {
					return false;
				}
			}
			return true;
		}
		return a.equals(b);
	}

	/** A short fingerprint of the active profile, so the auto backup can skip a copy when nothing changed. */
	public static int fingerprint() {
		return snapshot().toString().hashCode() * 31 + GSON.toJson(JacConfig.get().style).hashCode();
	}

	private static Path file(String name) {
		return DIR.resolve(safeName(name) + ".json");
	}

	private static String safeName(String name) {
		String s = name.replaceAll("[^A-Za-z0-9 _.-]", "_");
		// Windows treats CON, NUL, COM1 and the like as devices, even with an extension, so such a profile could never be saved.
		if (java.io.File.separatorChar == '\\' && s.split("\\.", 2)[0].strip().matches("(?i)con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
			s = "_" + s;
		}
		return s;
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
				pos.addProperty("scale", hud.scale.get());
				// Widgets resized by their edges keep that size in a HUD-only layout too.
				if (hud.freeWidth() != null) {
					pos.addProperty("w", hud.freeWidth().get());
				}
				if (hud.freeHeight() != null && hud.freeHeight() != hud.freeWidth()) {
					pos.addProperty("h", hud.freeHeight().get());
				}
				if (!Float.isNaN(hud.anchorX)) {
					pos.addProperty("ax", hud.anchorX);
					pos.addProperty("ay", hud.anchorY);
				}
				f.add("position", pos);
			}
			features.add(feature.id, f);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("features", features);
		return root;
	}

	/**
	 * Applies a validated profile. Each feature is turned off with its old settings, given its new settings, then turned
	 * on again if it should be, so on and off side effects always see the settings they belong to.
	 */
	private static void apply(JsonObject root, boolean fromDefaults) {
		JsonObject features = root.has("features") ? root.getAsJsonObject("features") : new JsonObject();
		for (Feature feature : Features.all()) {
			JsonElement element = features.get(feature.id);
			JsonObject f = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
			if (f == null && !fromDefaults) {
				continue;
			}
			boolean wanted = f != null && f.has("enabled") ? f.get("enabled").getAsBoolean() : fromDefaults ? feature.enabledByDefault() : feature.isEnabled();
			feature.replaceState(wanted, () -> {
				if (fromDefaults) {
					feature.resetSettings();
					if (feature instanceof HudFeature hud) {
						hud.resetPosition();
					}
				}
				if (f == null) {
					return;
				}
				if (f.has(feature.toggleKey.id)) {
					feature.toggleKey.load(f.get(feature.toggleKey.id));
				}
				JsonObject settings = f.has("settings") ? f.getAsJsonObject("settings") : null;
				if (settings != null) {
					for (Setting<?> setting : feature.settings()) {
						if (settings.has(setting.id)) {
							setting.load(settings.get(setting.id));
						}
					}
				}
				JsonObject pos = f.has("position") ? f.getAsJsonObject("position") : null;
				if (feature instanceof HudFeature hud && pos != null) {
					hud.relX = clamp01(pos.get("x"));
					hud.relY = clamp01(pos.get("y"));
					hud.anchorX = pos.has("ax") ? clamp01(pos.get("ax")) : Float.NaN;
					hud.anchorY = pos.has("ay") ? clamp01(pos.get("ay")) : Float.NaN;
					if (pos.has("scale")) {
						hud.scale.load(pos.get("scale"));
					}
					if (pos.has("w") && hud.freeWidth() != null) {
						hud.freeWidth().load(pos.get("w"));
					}
					if (pos.has("h") && hud.freeHeight() != null) {
						hud.freeHeight().load(pos.get("h"));
					}
				}
			});
		}
	}

	private static float clamp01(JsonElement e) {
		return e == null ? 0f : Math.max(0f, Math.min(1f, e.getAsFloat()));
	}
}
