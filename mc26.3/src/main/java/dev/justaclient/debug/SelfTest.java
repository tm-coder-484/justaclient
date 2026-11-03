package dev.justaclient.debug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.AutoReconnectFeature;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Icons;
import dev.justaclient.common.chat.ChatText;
import dev.justaclient.common.inventory.AnvilMath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Runtime regression checks for the profile and feature-state code, run against the real classes in a running client.
 * Start the game with {@code -Djustaclient.selftest=true} (or run {@code /jselftest}); results go to the log and to
 * {@code config/justaclient/selftest.txt}. Every check puts the player's setup back the way it was.
 */
public final class SelfTest {
	/**
	 * A name prefix unique to this run, so the checks never touch a profile the player made, even one named like a test
	 * file. Every file the checks write starts with it.
	 */
	private static String NS = "SelfTest";
	private static final Path REPORT = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("selftest.txt");
	private static final List<String> lines = new ArrayList<>();
	private static int failed;

	private SelfTest() {
	}

	/** A feature that only counts its hooks. Never registered. */
	private static final class Probe extends Feature {
		int enables;
		int disables;
		boolean settingAtEnable;
		final dev.justaclient.feature.setting.BoolSetting flag = bool("flag", "Flag", false);

		Probe(boolean onByDefault) {
			super("selftest_probe", "Probe", "", Category.UTILITY, Icons.INFO, onByDefault);
		}

		@Override
		protected void onEnable() {
			enables++;
			settingAtEnable = flag.on();
		}

		@Override
		protected void onDisable() {
			disables++;
		}
	}

	public static List<String> run() {
		lines.clear();
		failed = 0;
		NS = "SelfTest-" + Long.toHexString(System.nanoTime() & 0xFFFFFFL);
		// A readable profile must never be set aside: on 26.3 a feature hook running before the game's Gui existed once
		// failed the load, and Default.json was moved aside as broken on every launch.
		List<String> setAside = Profiles.keptAside().stream().filter(n -> !n.startsWith("SelfTest-")).toList();
		check("no profile was set aside as unreadable since the game started", setAside.isEmpty(), String.join(", ", setAside));
		AutoReconnectFeature.Target target = AutoReconnectFeature.saveTarget();
		String profile = Profiles.active();
		Profiles.save();
		int before = Profiles.fingerprint();
		// A file at the name older self-tests used must come through untouched, like every other file already there.
		Path legacy = Profiles.EXPORTS.resolve("selftest-bad.json");
		boolean ownSentinel = false;
		try {
			if (!Files.exists(legacy)) {
				Files.createDirectories(legacy.getParent());
				Files.writeString(legacy, "user file " + NS);
				ownSentinel = true;
			}
		} catch (IOException e) {
			fail("could not write the sentinel export", e.toString());
		}
		// So are user exports named for this minute and the next: the self-test's own exports must pick other names.
		List<Path> minuteSentinels = new ArrayList<>();
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		for (java.time.LocalDateTime when : List.of(now, now.plusMinutes(1))) {
			Path p = Profiles.EXPORTS.resolve(Profiles.exportBase(Profiles.exportStamp(when)) + ".json");
			try {
				if (!Files.exists(p)) {
					Files.createDirectories(p.getParent());
					Files.writeString(p, "user export " + NS);
					minuteSentinels.add(p);
				}
			} catch (IOException e) {
				fail("could not write the minute sentinel", e.toString());
			}
		}
		java.util.Map<Path, String> userFiles = userFiles();
		try {
			lifecycle();
			filenameCollision(profile);
			malformed(profile);
			hudRoundTrip(profile);
			failedBackupBlocksReset();
			retryTarget();
			uniqueIds();
			modSwitching();
			renameConflicts();
			listsAndKeys();
			settingTypes();
			brewRoutes();
			streamWorldNames();
			storageIndex();
			waypointData();
			deathRecap();
			kits();
			anvilMath();
			storageOffers();
			chatText();
			biomeLooks();
			capeLayout();
			relayProtocol();
			relaySync();
			autoGgOrigin();
			spatialPanel();
			modrinth();
			modrinthInstaller();
		} catch (RuntimeException | IOException e) {
			fail("unexpected exception", e.toString());
		} finally {
			if (!Profiles.active().equals(profile)) {
				Profiles.switchTo(profile);
			}
			AutoReconnectFeature.restoreTarget(target);
		}
		check("setup unchanged after all checks", Profiles.fingerprint() == before, "fingerprint differs");
		java.util.Map<Path, String> after = userFiles();
		List<String> changed = new ArrayList<>();
		String activeFile = Profiles.active() + ".json";
		userFiles.forEach((path, hash) -> {
			if (!after.containsKey(path) || !path.getFileName().toString().equals(activeFile) && !after.get(path).equals(hash)) {
				changed.add(path.getFileName().toString());
			}
		});
		check("files that were already there are kept unchanged", changed.isEmpty(), "changed or gone: " + changed);
		if (ownSentinel) {
			try {
				Files.deleteIfExists(legacy);
			} catch (IOException e) {
				// Left behind; harmless.
			}
		}
		for (Path p : minuteSentinels) {
			try {
				Files.deleteIfExists(p);
			} catch (IOException e) {
				// Left behind; harmless.
			}
		}
		lines.add(failed == 0 ? "ALL PASSED" : failed + " FAILED");
		try {
			Files.createDirectories(REPORT.getParent());
			Files.write(REPORT, lines);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", REPORT, e);
		}
		lines.forEach(l -> Justaclient.LOGGER.info("[selftest] {}", l));
		return List.copyOf(lines);
	}

	/** Server worlds can be named by their address, so the name follows the "Hide server address" switch, not the coordinates one. */
	private static void streamWorldNames() {
		var stream = Features.get(dev.justaclient.feature.impl.StreamModeFeature.class);
		dev.justaclient.feature.setting.BoolSetting coords = null;
		dev.justaclient.feature.setting.BoolSetting server = null;
		for (var setting : stream.settings()) {
			if (setting instanceof dev.justaclient.feature.setting.BoolSetting b && b.id.equals("coordinates")) {
				coords = b;
			} else if (setting instanceof dev.justaclient.feature.setting.BoolSetting b && b.id.equals("server")) {
				server = b;
			}
		}
		if (coords == null || server == null) {
			fail("stream mode settings", "not found");
			return;
		}
		boolean wasOn = stream.isEnabled();
		boolean oldCoords = coords.get();
		boolean oldServer = server.get();
		String address = "mp:10.0.0.5";
		try {
			stream.setEnabled(true);
			coords.set(true);
			server.set(false);
			check("stream mode: hiding coordinates alone shows the server name",
				dev.justaclient.feature.impl.StreamModeFeature.worldName(true, address).equals(address), "hid it");
			coords.set(false);
			server.set(true);
			check("stream mode: hiding the server address hides a server world's name",
				dev.justaclient.feature.impl.StreamModeFeature.worldName(true, address).equals("Server"), "shown");
			check("stream mode: singleplayer world names stay",
				dev.justaclient.feature.impl.StreamModeFeature.worldName(false, "My world").equals("My world"), "hidden");
		} finally {
			coords.set(oldCoords);
			server.set(oldServer);
			stream.setEnabled(wasOn);
		}
	}

	private static void lifecycle() {
		Probe on = new Probe(true);
		on.flag.set(true);
		on.resetToDefaults();
		check("reset while on runs disable then enable", on.disables == 1 && on.enables == 1, on.disables + " disables, " + on.enables + " enables");
		check("enable after reset sees the reset settings", !on.settingAtEnable, "saw the old setting");
		on.setEnabled(false);
		on.flag.set(true);
		on.replaceState(true, () -> {
		});
		check("switching on through a profile runs enable", on.enables == 2 && on.isEnabled(), on.enables + " enables");
		on.replaceState(false, () -> on.flag.set(false));
		check("switching off through a profile runs disable", on.disables == 3 && !on.isEnabled(), on.disables + " disables");
		Probe off = new Probe(false);
		off.resetToDefaults();
		check("reset of an off feature runs no hooks", off.enables == 0 && off.disables == 0, off.enables + "/" + off.disables);
	}

	private static void filenameCollision(String profile) throws IOException {
		Path existing = Profiles.DIR.resolve(NS + " A_B.json");
		Files.writeString(existing, "{\"features\":{}}");
		String imported = null;
		try {
			JsonObject root = JsonParser.parseString("{\"name\":\"" + NS + " A/B\",\"features\":{}}").getAsJsonObject();
			imported = Profiles.importJson(root, "Imported", false);
			check("import named A/B does not reuse A_B.json", imported != null && !imported.equals(NS + " A_B"), "got " + imported);
			check("existing A_B.json untouched", Files.readString(existing).equals("{\"features\":{}}"), "file changed");
		} finally {
			Profiles.switchTo(profile);
			Files.deleteIfExists(existing);
			if (imported != null) {
				Files.deleteIfExists(Profiles.DIR.resolve(imported.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json"));
			}
		}
	}

	private static void malformed(String profile) throws IOException {
		String[] bad = {
			"{\"features\":[]}",
			"{\"features\":{\"fps\":{\"settings\":[]}}}",
			"{\"features\":{\"fps\":{\"enabled\":\"yes\"}}}",
			"{\"features\":{\"fps\":{\"position\":{\"x\":\"left\"}}}}",
			"{\"features\":{\"fps\":[]}}",
		};
		int print = Profiles.fingerprint();
		for (String text : bad) {
			check("parse rejects " + text, Profiles.parseProfile(text) == null, "accepted");
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			String name = Profiles.importJson(root, NS + " Bad", false);
			check("import rejects " + text + " without changes", name == null && Profiles.fingerprint() == print && Profiles.active().equals(profile),
				"imported as " + name);
		}
		Path file = Profiles.EXPORTS.resolve(NS + "-bad.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, bad[1]);
		try {
			Profiles.Preview preview = Profiles.preview(file);
			check("preview of a malformed file is empty", preview.root() == null, "had a root");
		} finally {
			Files.deleteIfExists(file);
		}
		// A malformed active profile on load is kept aside and the client starts from defaults.
		Path broken = Profiles.DIR.resolve(NS + " Broken.json");
		Files.writeString(broken, bad[0]);
		// Backups already taken this second and the next must survive: the new one gets another name.
		java.time.format.DateTimeFormatter stampFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		List<Path> earlier = List.of(Profiles.DIR.resolve(NS + " Broken.json." + now.format(stampFormat) + ".broken"),
			Profiles.DIR.resolve(NS + " Broken.json." + now.plusSeconds(1).format(stampFormat) + ".broken"));
		for (Path e : earlier) {
			Files.writeString(e, "earlier backup");
		}
		try {
			Profiles.switchTo(NS + " Broken");
			boolean kept;
			try (Stream<Path> files = Files.list(Profiles.DIR)) {
				kept = files.filter(p -> p.getFileName().toString().startsWith(NS + " Broken.json.") && p.toString().endsWith(".broken"))
					.anyMatch(p -> {
						try {
							return Files.readString(p).equals(bad[0]);
						} catch (IOException e) {
							return false;
						}
					});
			}
			check("unreadable profile kept as .broken on load", kept, "no .broken copy");
			boolean intact = true;
			for (Path e : earlier) {
				intact &= Files.readString(e).equals("earlier backup");
			}
			check("an earlier .broken backup with the same time is not overwritten", intact, "overwritten");
		} finally {
			Profiles.switchTo(profile);
			Files.deleteIfExists(broken);
			try (Stream<Path> files = Files.list(Profiles.DIR)) {
				for (Path p : files.filter(p -> p.getFileName().toString().startsWith(NS + " Broken")).toList()) {
					Files.deleteIfExists(p);
				}
			}
		}
	}

	private static void hudRoundTrip(String profile) throws IOException {
		HudFeature hud = null;
		for (Feature f : Features.all()) {
			if (f instanceof HudFeature h) {
				hud = h;
				break;
			}
		}
		if (hud == null) {
			fail("HUD round trip", "no HUD widgets");
			return;
		}
		double oldScale = hud.scale.get();
		float oldX = hud.relX;
		hud.scale.set(1.35);
		Path file = Profiles.exportToFile(new Profiles.Parts(false, false, false, true, false));
		String imported = null;
		try {
			hud.scale.set(1.0);
			JsonObject root = Profiles.parseProfile(Files.readString(file));
			imported = root == null ? null : Profiles.importJson(root, NS + " HUD", false);
			check("HUD-only export keeps widget scale", Math.abs(hud.scale.get() - 1.35) < 1e-6, "scale " + hud.scale.get());
		} finally {
			Profiles.switchTo(profile);
			hud.scale.set(oldScale);
			hud.relX = oldX;
			Profiles.save();
			Files.deleteIfExists(file);
			if (imported != null) {
				Files.deleteIfExists(Profiles.DIR.resolve(imported.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json"));
			}
		}
		// A widget resized by its edges keeps that size through a HUD-only layout.
		HudFeature sized = null;
		for (Feature f : Features.all()) {
			if (f instanceof HudFeature h && h.freeWidth() != null) {
				sized = h;
				break;
			}
		}
		if (sized == null) {
			fail("HUD-only export keeps edge size", "no resizable widget");
			return;
		}
		double oldWidth = sized.freeWidth().get();
		double wanted = Math.min(sized.freeWidth().max, Math.max(sized.freeWidth().min, oldWidth + 40));
		sized.freeWidth().set(wanted);
		file = Profiles.exportToFile(new Profiles.Parts(false, false, false, true, false));
		imported = null;
		try {
			sized.freeWidth().set(sized.freeWidth().min);
			JsonObject root = Profiles.parseProfile(Files.readString(file));
			imported = root == null ? null : Profiles.importJson(root, NS + " Size", false);
			check("HUD-only export keeps edge-resized size", Math.abs(sized.freeWidth().get() - wanted) < 1e-6,
				sized.id + " width " + sized.freeWidth().get() + ", wanted " + wanted);
		} finally {
			Profiles.switchTo(profile);
			sized.freeWidth().set(oldWidth);
			Profiles.save();
			Files.deleteIfExists(file);
			if (imported != null) {
				Files.deleteIfExists(Profiles.DIR.resolve(imported.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json"));
			}
		}
	}

	private static void failedBackupBlocksReset() throws IOException {
		Path backups = Profiles.BACKUPS;
		Path aside = backups.resolveSibling("backups-" + NS + "-aside");
		boolean had = Files.isDirectory(backups);
		if (had) {
			Files.move(backups, aside);
		}
		// A plain file where the backups folder should be makes the backup fail.
		Files.writeString(backups, "not a folder");
		int print = Profiles.fingerprint();
		try {
			String result = Profiles.resetAll();
			check("reset all refuses without a backup", result == null && Profiles.fingerprint() == print, "reset went ahead");
		} finally {
			Files.deleteIfExists(backups);
			if (had) {
				Files.move(aside, backups);
			}
		}
	}

	private static void uniqueIds() {
		java.util.Set<String> seen = new java.util.HashSet<>();
		List<String> dupes = new ArrayList<>();
		for (Feature f : Features.all()) {
			if (!seen.add(f.id)) {
				dupes.add(f.id);
			}
		}
		check("every feature id is unique", dupes.isEmpty(), "duplicates: " + dupes);
	}

	/** Mod on/off by renaming, in a scratch folder: never touches the real mods folder. */
	private static void modSwitching() throws IOException {
		Path dir = Files.createTempDirectory("jac-selftest-mods");
		try {
			Path jar = dir.resolve("helper-1.0.jar");
			writeJar(jar, "{\"id\":\"helper\",\"name\":\"Helper\",\"version\":\"1.0\",\"icon\":{\"16\":\"a.png\",\"64\":\"b.png\"},"
				+ "\"jars\":[{\"file\":\"META-INF/jars/inner.jar\"}]}", "{\"id\":\"inner\",\"provides\":[\"inner_api\"],\"depends\":{\"java\":\"*\"}}");
			dev.justaclient.common.mods.JarMetadata meta = dev.justaclient.common.mods.JarMetadata.read(jar);
			check("jar metadata reads nested mods", meta != null && meta.providedIds().containsAll(List.of("helper", "inner", "inner_api"))
				&& meta.requiredIds().equals(java.util.Set.of("java")), String.valueOf(meta));
			check("jar metadata picks the 64 icon", meta != null && "b.png".equals(meta.icon()), meta == null ? "null" : meta.icon());
			Path broken = dir.resolve("broken.jar");
			Files.writeString(broken, "not a zip");
			check("unreadable jar gives no metadata", dev.justaclient.common.mods.JarMetadata.read(broken) == null, "read something");
			dev.justaclient.common.mods.ManagedMod helper = mod("helper", java.util.Set.of("helper", "inner_api"), java.util.Set.of(),
				dev.justaclient.common.mods.ModStatus.ENABLED, jar);
			dev.justaclient.common.mods.ManagedMod addon = mod("addon", java.util.Set.of("addon"), java.util.Set.of("inner_api"),
				dev.justaclient.common.mods.ModStatus.ENABLED, dir.resolve("addon.jar"));
			List<dev.justaclient.common.mods.ManagedMod> all = List.of(helper, addon);
			check("turning off a needed mod names its dependents", dev.justaclient.common.mods.ModManager.dependentsOf(helper, all).equals(List.of("addon")),
				String.valueOf(dev.justaclient.common.mods.ModManager.dependentsOf(helper, all)));
			dev.justaclient.common.mods.ManagedMod helperOff = helper.withStatus(dev.justaclient.common.mods.ModStatus.DISABLED, jar);
			check("a mod whose dependency is off reports it missing",
				dev.justaclient.common.mods.ModManager.missingDependencies(addon, List.of(helperOff, addon)).equals(List.of("inner_api")), "wrong");
			dev.justaclient.common.mods.ToggleResult off = dev.justaclient.common.mods.ModManager.toggle(helper);
			Path disabled = dev.justaclient.common.mods.ModFiles.disabledPath(jar);
			check("turning a mod off renames it to .disabled", off.outcome() == dev.justaclient.common.mods.ToggleResult.Outcome.RESTART_REQUIRED
				&& Files.exists(disabled) && !Files.exists(jar), off.toString());
			dev.justaclient.common.mods.ToggleResult undo = dev.justaclient.common.mods.ModManager.toggle(
				helper.withStatus(dev.justaclient.common.mods.ModStatus.DISABLE_PENDING, disabled));
			check("switching it back before a restart undoes the rename", undo.outcome() == dev.justaclient.common.mods.ToggleResult.Outcome.UNDONE
				&& Files.exists(jar) && !Files.exists(disabled), undo.toString());
			Files.writeString(disabled, "taken");
			dev.justaclient.common.mods.ToggleResult clash = dev.justaclient.common.mods.ModManager.toggle(helper);
			check("a rename onto an existing file fails and keeps both", clash.outcome() == dev.justaclient.common.mods.ToggleResult.Outcome.FAILED
				&& Files.exists(jar) && Files.readString(disabled).equals("taken"), clash.toString());
			check("the client cannot switch itself off", dev.justaclient.common.mods.ModManager.toggle(mod("justaclient", java.util.Set.of(),
				java.util.Set.of(), dev.justaclient.common.mods.ModStatus.LOCKED, null)).outcome() == dev.justaclient.common.mods.ToggleResult.Outcome.FAILED,
				"switched");
			var modrinth = dev.justaclient.common.mods.Relauncher.buildDirectCommand("java", "com.modrinth.theseus.MinecraftLaunch "
				+ "net.fabricmc.loader.impl.launch.knot.KnotClient", List.of("-Xmx4G", "-javaagent:/x/theseus.jar", "-Dmodrinth.internal.a=b"),
				"cp", List.of("--username", "x"));
			check("restart command drops the Modrinth wrapper bits", modrinth.isPresent() && modrinth.get().equals(List.of("java", "-Xmx4G", "-cp", "cp",
				"net.fabricmc.loader.impl.launch.knot.KnotClient", "--username", "x")), String.valueOf(modrinth));
			check("no restart command for an unknown launcher", dev.justaclient.common.mods.Relauncher.buildDirectCommand("java", "some.Other", List.of(),
				"cp", List.of()).isEmpty(), "built one");
		} finally {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					Files.deleteIfExists(p);
				}
			}
			Files.deleteIfExists(dir);
		}
	}

	/** Every file under the profiles, exports and backups folders, with a hash of its contents. */
	private static java.util.Map<Path, String> userFiles() {
		java.util.Map<Path, String> out = new java.util.HashMap<>();
		for (Path dir : List.of(Profiles.DIR, Profiles.EXPORTS, Profiles.BACKUPS)) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(dir)) {
				for (Path p : files.filter(Files::isRegularFile).toList()) {
					out.put(p, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
				}
			} catch (IOException | java.security.NoSuchAlgorithmException | RuntimeException e) {
				fail("could not list " + dir, e.toString());
			}
		}
		return out;
	}

	/** Deferred renames never overwrite: a source and target that both exist stay pending, in a scratch folder. */
	private static void renameConflicts() throws IOException {
		Path dir = Files.createTempDirectory("jac-selftest-renames");
		try {
			Path both = dir.resolve("both.jar");
			Path bothOff = dir.resolve("both.jar.disabled");
			Files.writeString(both, "enabled copy");
			Files.writeString(bothOff, "different disabled copy");
			Path plain = dir.resolve("plain.jar");
			Files.writeString(plain, "plain");
			Path done = dir.resolve("done.jar.disabled");
			Files.writeString(done, "already renamed");
			Path pending = dir.resolve("pending-renames.txt");
			dev.justaclient.common.mods.RenameHelper.writePending(pending, List.of(
				new String[] {both.toString(), bothOff.toString()},
				new String[] {plain.toString(), dir.resolve("plain.jar.disabled").toString()},
				new String[] {dir.resolve("done.jar").toString(), done.toString()},
				new String[] {dir.resolve("gone.jar").toString(), dir.resolve("gone.jar.disabled").toString()}));
			check("rename onto an existing file is a conflict", dev.justaclient.common.mods.RenameHelper.tryOnce(both, bothOff)
				== dev.justaclient.common.mods.RenameHelper.Outcome.CONFLICT, "not a conflict");
			// Run the real helper; a pid that does not exist means it does not wait.
			java.io.PrintStream out = System.out;
			try {
				System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
				dev.justaclient.common.mods.RenameHelper.main(new String[] {String.valueOf(Long.MAX_VALUE), pending.toString()});
			} catch (Exception e) {
				fail("rename helper threw", e.toString());
			} finally {
				System.setOut(out);
			}
			List<String[]> left = dev.justaclient.common.mods.RenameHelper.readPending(pending);
			check("helper keeps a conflicting rename pending", left.size() == 1 && left.getFirst()[0].equals(both.toString()),
				left.size() + " left");
			check("helper leaves both conflicting files as they were", Files.readString(both).equals("enabled copy")
				&& Files.readString(bothOff).equals("different disabled copy"), "a file changed");
			check("helper still does the renames it can", Files.exists(dir.resolve("plain.jar.disabled")) && !Files.exists(plain), "plain not renamed");
		} finally {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					Files.deleteIfExists(p);
				}
			}
			Files.deleteIfExists(dir);
		}
	}

	private static dev.justaclient.common.mods.ManagedMod mod(String id, java.util.Set<String> provides, java.util.Set<String> needs,
		dev.justaclient.common.mods.ModStatus status, Path file) {
		return new dev.justaclient.common.mods.ManagedMod(id, id, "1", "", List.of(), java.util.Map.of(), null, status, file, file, null, provides,
			needs, false, false);
	}

	private static void writeJar(Path jar, String json, String innerJson) throws IOException {
		java.io.ByteArrayOutputStream inner = new java.io.ByteArrayOutputStream();
		try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(inner)) {
			z.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
			z.write(innerJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
			z.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
			z.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			z.putNextEntry(new java.util.zip.ZipEntry("META-INF/jars/inner.jar"));
			z.write(inner.toByteArray());
		}
	}

	/** Server folders, key sets and kept key binds, all on scratch files. */
	private static void listsAndKeys() throws IOException {
		Path dir = Files.createTempDirectory("jac-selftest-lists");
		try {
			Path file = dir.resolve("servers.json");
			Files.writeString(file, "{\"folders\": [\"PvP\"], \"servers\": {\"a.net\": {\"folder\": \"Gone\"}}}");
			dev.justaclient.common.ServerFolders f = dev.justaclient.common.ServerFolders.load(file);
			check("a server in a missing folder is unfiled", f.peek("A.NET") != null && f.peek("a.net").folder == null, "kept folder");
			f.meta("b.net").folder = "PvP";
			check("renaming a folder keeps its servers", f.renameFolder("PvP", "Duels") && "Duels".equals(f.peek("b.net").folder), "lost");
			check("a folder name cannot be taken twice", !f.addFolder("duels"), "added");
			f.removeFolder("Duels");
			check("deleting a folder unfiles its servers", f.peek("b.net").folder == null && f.folders.isEmpty(), "still filed");
			Files.writeString(file, "{ broken");
			check("a broken folders file starts empty and is kept aside",
				dev.justaclient.common.ServerFolders.load(file).folders.isEmpty() && Files.exists(dir.resolve("servers.json.broken")), "no backup");
			Path keys = dir.resolve("keybinds.json");
			Files.writeString(keys, "{\"profiles\": {\"PvP\": {\"key.jump\": \"key.keyboard.space\"}}, \"servers\": {\"x.net\": \"Gone\", \"y.net\": \"PvP\"}}");
			dev.justaclient.common.KeybindProfiles k = dev.justaclient.common.KeybindProfiles.load(keys);
			check("a server tied to a missing key set is dropped", k.profileFor("x.net") == null && "PvP".equals(k.profileFor("Y.NET")), k.servers.toString());
			k.removeProfile("PvP");
			check("deleting a key set unties its servers", k.servers.isEmpty(), k.servers.toString());
			Path options = dir.resolve("options.txt");
			Files.writeString(options, "key_key.jump:key.keyboard.space\nkey_key.othermod.dash:key.keyboard.r\nfov:70\n");
			java.util.Map<String, String> before = dev.justaclient.common.mods.KeybindKeeper.readKeybinds(options);
			Files.writeString(options, "key_key.jump:key.keyboard.space\nfov:70\n");
			dev.justaclient.common.mods.KeybindKeeper.restoreMissing(options, before, java.util.Set.of("key.jump"));
			String after = Files.readString(options);
			check("binds of a mod that is off are kept", after.contains("key_key.othermod.dash:key.keyboard.r")
				&& after.indexOf("key_key.jump") == after.lastIndexOf("key_key.jump"), after.replace("\n", " | "));
		} finally {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					Files.deleteIfExists(p);
				}
			}
			Files.deleteIfExists(dir);
		}
	}

	private static void retryTarget() {
		AutoReconnectFeature.connecting(new ServerData("A", "a.selftest.invalid", ServerData.Type.OTHER));
		AutoReconnectFeature.connecting(new ServerData("B", "b.selftest.invalid", ServerData.Type.OTHER));
		check("retry goes to the server being joined", "b.selftest.invalid".equals(AutoReconnectFeature.retryTarget()),
			"target " + AutoReconnectFeature.retryTarget());
	}

	private static void settingTypes() throws IOException {
		dev.justaclient.feature.setting.ListSetting list = new dev.justaclient.feature.setting.ListSetting("t", "T", List.of(), 3, 5, "");
		list.load(new com.google.gson.JsonPrimitive("Steve, alex,, STEVE ,toolongname,c,d"));
		check("list setting reads an old comma string, trimmed and de-duplicated", list.get().equals(List.of("Steve", "alex", "toolo")),
			list.get().toString());
		check("list setting stops at its size limit", !list.add("x") && list.get().size() == 3, list.get().toString());
		check("list setting removes ignoring case", list.remove("ALEX") && list.get().equals(List.of("Steve", "toolo")), list.get().toString());
		com.google.gson.JsonElement saved = list.save();
		list.set(List.of());
		list.load(saved);
		check("list setting round trips as an array", saved.isJsonArray() && list.get().equals(List.of("Steve", "toolo")), saved.toString());
		list.load(new com.google.gson.JsonObject());
		check("list setting ignores a malformed value", list.get().equals(List.of("Steve", "toolo")), list.get().toString());

		Path dir = Files.createTempDirectory(NS + "-files");
		try {
			Files.writeString(dir.resolve("b.png"), "x");
			Files.writeString(dir.resolve("A.jpg"), "x");
			Files.writeString(dir.resolve("notes.txt"), "x");
			Files.createDirectories(dir.resolve("folder.png"));
			dev.justaclient.feature.setting.FileSetting file = new dev.justaclient.feature.setting.FileSetting("f", "F", () -> dir,
				List.of(".png", ".jpg"), "");
			check("file setting lists only matching files, sorted", file.choices().equals(List.of("A.jpg", "b.png")), file.choices().toString());
			check("blank file setting means the first file", dir.resolve("A.jpg").equals(file.resolve()), String.valueOf(file.resolve()));
			file.set("b.png");
			check("file setting resolves the chosen file", dir.resolve("b.png").equals(file.resolve()), String.valueOf(file.resolve()));
			file.set("../b.png");
			check("file setting refuses names that leave the folder", file.get().isEmpty(), file.get());
			file.set("gone.png");
			check("a chosen file that is gone resolves to nothing", file.resolve() == null, String.valueOf(file.resolve()));
		} finally {
			try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
				for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(p);
				}
			}
		}
	}

	/** Kits: slot checks, names and the saved file. */
	private static void kits() {
		var want = new dev.justaclient.common.kits.Kit.Slot(0, "minecraft:golden_apple", 8, "Golden Apple", null);
		check("kits: slot check", dev.justaclient.common.kits.Kit.check(want, "minecraft:golden_apple", 8) == dev.justaclient.common.kits.Kit.State.RIGHT
			&& dev.justaclient.common.kits.Kit.check(want, "minecraft:golden_apple", 3) == dev.justaclient.common.kits.Kit.State.SHORT
			&& dev.justaclient.common.kits.Kit.check(want, "", 0) == dev.justaclient.common.kits.Kit.State.EMPTY
			&& dev.justaclient.common.kits.Kit.check(want, "minecraft:apple", 8) == dev.justaclient.common.kits.Kit.State.WRONG, "wrong state");
		check("kits: names are cleaned", "PvP".equals(dev.justaclient.common.kits.Kit.cleanName("  PvP\n "))
			&& dev.justaclient.common.kits.Kit.cleanName("   ") == null, String.valueOf(dev.justaclient.common.kits.Kit.cleanName("  PvP\n ")));
		var kit = new dev.justaclient.common.kits.Kit("PvP", 1L, List.of(want, new dev.justaclient.common.kits.Kit.Slot(40, "minecraft:shield", 1, "Shield",
			null)));
		var json = dev.justaclient.common.kits.Kit.toJson(List.of(kit, new dev.justaclient.common.kits.Kit("pvp", 2L, List.of())));
		var slots = json.getAsJsonArray("kits").get(0).getAsJsonObject().getAsJsonArray("slots");
		var bad = new com.google.gson.JsonObject();
		bad.addProperty("slot", 99);
		bad.addProperty("id", "minecraft:stone");
		bad.addProperty("count", 1);
		slots.add(bad);
		var back = dev.justaclient.common.kits.Kit.fromJson(json);
		check("kits: the saved file reads back (same name twice keeps the first, a bad slot is skipped)", back.size() == 1
			&& back.get(0).slots.size() == 2 && back.get(0).at(40) != null && back.get(0).created == 1L, back.size() + " kits");
	}

	/** Chat parsing, mentions, tabs, search filters, copy formats and the Auto GG gate. */
	private static void chatText() {
		check("chat: senders and kinds", "Steve".equals(ChatText.sender("<Steve> hi")) && "Steve".equals(ChatText.sender("[VIP] [MVP+] Steve: hi"))
			&& "Alex".equals(ChatText.sender("Alex whispers to you: psst")) && ChatText.kind("Alex whispers to you: psst") == ChatText.Kind.PRIVATE
			&& ChatText.kind("Steve joined the game") == ChatText.Kind.SYSTEM && ChatText.kind("<Steve> hi") == ChatText.Kind.PLAYER,
			ChatText.sender("[VIP] [MVP+] Steve: hi"));
		check("chat: mentions are whole words, not your own lines", ChatText.mentions("<Alex> hey Steve, come here", "Steve", List.of())
			&& !ChatText.mentions("<Alex> Steven is here", "Steve", List.of()) && !ChatText.mentions("<Steve> I am Steve", "Steve", List.of())
			&& !ChatText.mentions("Steve has made the advancement [Stone Age]", "Steve", List.of())
			&& ChatText.mentions("<Alex> selling diamonds", "Steve", List.of("diamonds")), "mention wrong");
		var spans = ChatText.spans("gg GG ggg gg", List.of("gg"));
		check("chat: highlight spans", spans.size() == 3 && spans.get(0)[0] == 0 && spans.get(1)[0] == 3 && spans.get(2)[0] == 10,
			spans.size() + " spans");
		var tab = ChatText.Tab.parse(" Trade : buy, sell ,wts ");
		var line = new ChatText.Entry(0L, "<Bob> wts 64 iron https://x.example", "Bob", ChatText.Kind.PLAYER, false);
		check("chat: tabs from \"Name: words\"", tab != null && tab.name().equals("Trade") && tab.words().size() == 3 && tab.matches(line)
			&& ChatText.Tab.parse("no colon") == null && ChatText.Tab.parse("Empty:") == null, String.valueOf(tab));
		check("chat: search filters", ChatText.Query.parse("from:bob has:link iron").matches(line) && !ChatText.Query.parse("from:alex").matches(line)
			&& !ChatText.Query.parse("is:system").matches(line) && !ChatText.Query.parse("is:mention").matches(line)
			&& ChatText.Query.parse("is:player WTS").matches(line), "filter wrong");
		check("chat: copy formats", ChatText.copy(line, ChatText.CopyFormat.PLAIN, ChatText.TimeStyle.SHORT).equals(line.text())
			&& ChatText.copy(line, ChatText.CopyFormat.WITH_TIME, ChatText.TimeStyle.SHORT).endsWith("] " + line.text())
			&& ChatText.copy(line, ChatText.CopyFormat.QUOTE, ChatText.TimeStyle.SHORT).startsWith("> " + line.text()), "copy wrong");
		var gate = new dev.justaclient.common.chat.GgGate();
		Object gameA = new Object();
		Object gameB = new Object();
		boolean first = gate.allow(1000, gameA, 60_000);
		gate.sent(1000, gameA);
		check("auto gg: one per game and a cooldown", first && !gate.allow(200_000, gameA, 60_000) && !gate.allow(30_000, gameB, 60_000)
			&& gate.allow(70_000, gameB, 60_000), "gate wrong");
		check("auto gg: triggers and servers", dev.justaclient.common.chat.GgGate.trigger("Steve WON THE GAME!", List.of("won the game")) != null
			&& dev.justaclient.common.chat.GgGate.trigger("hello", List.of("won the game", " ")) == null
			&& dev.justaclient.common.chat.GgGate.serverAllowed("mc.hypixel.net", List.of("hypixel"))
			&& !dev.justaclient.common.chat.GgGate.serverAllowed("play.other.net", List.of("hypixel"))
			&& dev.justaclient.common.chat.GgGate.serverAllowed(null, List.of()), "gg rules wrong");
	}

	/** A "this chest has it" offer only applies in the world and dimension it was made in (Sol #15). */
	private static void storageOffers() {
		check("storage offer: only good in the world and dimension it was made in",
			dev.justaclient.inventory.StorageFinder.isHere("a", "minecraft:overworld", "a", "minecraft:overworld")
				&& !dev.justaclient.inventory.StorageFinder.isHere("a", "minecraft:overworld", "b", "minecraft:overworld")
				&& !dev.justaclient.inventory.StorageFinder.isHere("a", "minecraft:overworld", "a", "minecraft:the_nether")
				&& !dev.justaclient.inventory.StorageFinder.isHere("a", "minecraft:overworld", null, null)
				&& !dev.justaclient.inventory.StorageFinder.isHere(null, null, null, null), "isHere wrong");
		var target = new dev.justaclient.waypoint.Locate.Target("test", dev.justaclient.render.Icons.PACKAGE_SEARCH, 0, 0, 0,
			"minecraft:overworld", 0xFFFFFFFF, 0, 0, 0);
		String world = dev.justaclient.waypoint.Waypoints.worldKey();
		dev.justaclient.inventory.StorageFinder.offerForTest(target, NS + "-other-world", dev.justaclient.waypoint.Waypoints.dimension());
		boolean otherWorld = dev.justaclient.inventory.StorageFinder.hasOffer() || dev.justaclient.inventory.StorageFinder.accept();
		check("storage offer: one made in another world is not shown or taken up", !otherWorld, "offer from another world was live");
		if (world != null) {
			dev.justaclient.inventory.StorageFinder.offerForTest(target, world, dev.justaclient.waypoint.Waypoints.dimension());
			boolean here = dev.justaclient.inventory.StorageFinder.hasOffer();
			dev.justaclient.inventory.StorageFinder.clear();
			check("storage offer: one made here is live, and clear() drops it", here && !dev.justaclient.inventory.StorageFinder.hasOffer(),
				"here=" + here);
		} else {
			lines.add("SKIP storage offer in this world (run /jselftest in a world)");
		}
		dev.justaclient.inventory.StorageFinder.clear();
	}

	/** The anvil's level rules the helper uses to decide a pair is worth listing. */
	private static void anvilMath() {
		check("anvil: equal levels go up by one, capped at the maximum",
			AnvilMath.merged(3, 3, 5) == 4 && AnvilMath.merged(5, 5, 5) == 5 && AnvilMath.merged(2, 4, 5) == 4 && AnvilMath.merged(0, 10, 5) == 5,
			AnvilMath.merged(3, 3, 5) + " " + AnvilMath.merged(5, 5, 5));
		check("anvil: a lower or maxed book is not listed", !AnvilMath.improves(4, 2, 5) && !AnvilMath.improves(5, 5, 5) && AnvilMath.improves(0, 1, 1)
			&& AnvilMath.improves(4, 4, 5), "improves wrong");
		check("anvil: past work adds up and 40 is too costly", AnvilMath.priorWork(31, 7) == 38 && !AnvilMath.tooExpensive(38)
			&& AnvilMath.tooExpensive(AnvilMath.priorWork(31, 15)), String.valueOf(AnvilMath.priorWork(31, 7)));
	}

	/** Death recap wording. */
	private static void deathRecap() {
		check("death recap: damage names", dev.justaclient.feature.impl.DeathRecapFeature.damageName("minecraft:mob_attack").equals("Mob attack")
			&& dev.justaclient.feature.impl.DeathRecapFeature.damageName("").equals("Damage"), dev.justaclient.feature.impl.DeathRecapFeature.damageName(
			"minecraft:mob_attack"));
		check("death recap: time alive", dev.justaclient.feature.impl.DeathRecapFeature.duration(45_000).equals("45 s")
			&& dev.justaclient.feature.impl.DeathRecapFeature.duration(754_000).equals("12:34")
			&& dev.justaclient.feature.impl.DeathRecapFeature.duration(3_723_000).equals("1:02:03"), dev.justaclient.feature.impl.DeathRecapFeature.duration(754_000));
	}

	/** Waypoints saved before the upgrade load as visited; structure names read well. */
	private static void waypointData() {
		var read = dev.justaclient.waypoint.Waypoints.parse(new java.io.StringReader(
			"{\"sp:old\":[{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"dimension\":\"minecraft:overworld\",\"color\":-10443270,\"visible\":true},"
				+ "{\"x\":0}],\"sp:broken\":null}"));
		var list = read == null ? null : read.get("sp:old");
		check("waypoints: an older save loads as visited, a nameless entry is dropped", list != null && list.size() == 1 && list.get(0).visited
			&& list.get(0).structure == null && !read.containsKey("sp:broken"), String.valueOf(read));
		check("waypoints: structure names", dev.justaclient.waypoint.Waypoints.structureName("minecraft:village_plains").equals("Village plains")
			&& dev.justaclient.waypoint.Waypoints.structureName("minecraft:ancient_city").equals("Ancient city"),
			dev.justaclient.waypoint.Waypoints.structureName("minecraft:village_plains"));
		check("waypoints: colour tags follow the palette", dev.justaclient.waypoint.Waypoints.tag(dev.justaclient.waypoint.Waypoints.PALETTE[6]) == 6
			&& dev.justaclient.waypoint.Waypoints.tag(0xFF123456) == -1, "wrong tag");
	}

	/** Storage memory search: amounts, shulker contents, stack maths and the saved file. */
	private static void storageIndex() {
		var Q = (java.util.function.Function<String, dev.justaclient.common.storage.StorageIndex.Query>)
			dev.justaclient.common.storage.StorageIndex.Query::parse;
		var q = Q.apply("Iron 32+");
		check("storage: \"iron 32+\" is iron, at least 32", q.text().equals("iron") && q.min() == 32 && q.max() == Integer.MAX_VALUE, q.toString());
		q = Q.apply("<5 torch");
		check("storage: \"<5 torch\" is at most 4", q.text().equals("torch") && q.min() == 0 && q.max() == 4, q.toString());
		q = Q.apply(">=10 oak log");
		check("storage: words and an amount mix", q.text().equals("oak log") && q.min() == 10, q.toString());
		var index = new dev.justaclient.common.storage.StorageIndex();
		var shulker = new dev.justaclient.common.storage.StorageIndex.Item("minecraft:shulker_box", 1, "Shulker Box",
			List.of(new dev.justaclient.common.storage.StorageIndex.Item("minecraft:iron_ingot", 40, "Iron Ingot")));
		var chest = new dev.justaclient.common.storage.StorageIndex.Container("block", "minecraft:overworld", 1, 64, 2, "minecraft:chest", 27, 2,
			List.of(new dev.justaclient.common.storage.StorageIndex.Item("minecraft:iron_ingot", 10, "Iron Ingot"), shulker), 1L);
		chest.other = new int[] {2, 64, 2};
		index.put(chest);
		index.put(new dev.justaclient.common.storage.StorageIndex.Container("block", "minecraft:overworld", 9, 64, 9, "minecraft:barrel", 27, 1,
			List.of(new dev.justaclient.common.storage.StorageIndex.Item("minecraft:iron_ingot", 3, "Iron Ingot")), 1L));
		var hits = index.search(Q.apply("iron 32+"));
		check("storage: shulker contents count toward the amount", hits.size() == 1 && hits.get(0).count() == 50 && hits.get(0).inShulkers() == 40,
			hits.toString());
		var totals = dev.justaclient.common.storage.StorageIndex.totals(index.search(Q.apply("iron ingot")));
		check("storage: totals add every container", totals.size() == 1 && totals.get(0).getValue() == 53, totals.toString());
		check("storage: stack maths", dev.justaclient.common.storage.StorageIndex.stacks(140, 64).equals("2 stacks + 12")
			&& dev.justaclient.common.storage.StorageIndex.stacks(64, 64).equals("1 stack") && dev.justaclient.common.storage.StorageIndex.stacks(5, 1)
			.equals("5"), dev.justaclient.common.storage.StorageIndex.stacks(140, 64));
		long now = 1_000_000_000L;
		check("storage: last checked wording", dev.justaclient.common.storage.StorageIndex.ago(now - 20_000, now).equals("just now")
			&& dev.justaclient.common.storage.StorageIndex.ago(now - 12 * 60_000, now).equals("12 minutes ago")
			&& dev.justaclient.common.storage.StorageIndex.ago(now - 3_600_000, now).equals("1 hour ago")
			&& dev.justaclient.common.storage.StorageIndex.ago(now - 2 * 86_400_000L, now).equals("2 days ago"),
			dev.justaclient.common.storage.StorageIndex.ago(now - 12 * 60_000, now));
		var json = index.toJson();
		json.getAsJsonArray("containers").add(new com.google.gson.JsonPrimitive("broken"));
		var back = dev.justaclient.common.storage.StorageIndex.fromJson(json);
		var again = back.get(chest.key());
		check("storage: the saved file reads back (a bad entry is skipped)", back.size() == 2 && again != null && again.other != null
			&& again.other[0] == 2 && again.count("minecraft:iron_ingot") == 50, back.size() + " containers");
	}

	private static void brewRoutes() {
		String w = dev.justaclient.common.potions.BrewGraph.POTION;
		String splash = dev.justaclient.common.potions.BrewGraph.SPLASH;
		java.util.function.BiFunction<String, String, dev.justaclient.common.potions.BrewGraph.State> st =
			dev.justaclient.common.potions.BrewGraph.State::new;
		var water = dev.justaclient.common.potions.BrewGraph.WATER;
		var awkward = st.apply(w, "minecraft:awkward");
		var swift = st.apply(w, "minecraft:swiftness");
		var longSwift = st.apply(w, "minecraft:long_swiftness");
		var splashSwift = st.apply(splash, "minecraft:swiftness");
		var graph = new dev.justaclient.common.potions.BrewGraph(List.of(
			new dev.justaclient.common.potions.BrewGraph.Mix(swift, "minecraft:redstone", longSwift),
			new dev.justaclient.common.potions.BrewGraph.Mix(water, "minecraft:nether_wart", awkward),
			new dev.justaclient.common.potions.BrewGraph.Mix(awkward, "minecraft:sugar", swift),
			new dev.justaclient.common.potions.BrewGraph.Mix(swift, "minecraft:gunpowder", splashSwift)));
		var route = graph.route(longSwift).orElse(List.of());
		check("brew route goes water, wart, sugar, redstone", route.stream().map(dev.justaclient.common.potions.BrewGraph.Step::reagent).toList()
			.equals(List.of("minecraft:nether_wart", "minecraft:sugar", "minecraft:redstone")), route.toString());
		check("next brew continues part way along", graph.next(awkward, longSwift).map(s -> s.reagent()).orElse("").equals("minecraft:sugar"),
			graph.next(awkward, longSwift).toString());
		check("no next brew from a bottle that can't get there", graph.next(splashSwift, longSwift).isEmpty(),
			graph.next(splashSwift, longSwift).toString());
		var splashWater = st.apply(splash, "minecraft:water");
		var splashAwkward = st.apply(splash, "minecraft:awkward");
		var both = new dev.justaclient.common.potions.BrewGraph(List.of(
			new dev.justaclient.common.potions.BrewGraph.Mix(water, "minecraft:gunpowder", splashWater),
			new dev.justaclient.common.potions.BrewGraph.Mix(splashWater, "minecraft:nether_wart", splashAwkward),
			new dev.justaclient.common.potions.BrewGraph.Mix(splashAwkward, "minecraft:sugar", splashSwift),
			new dev.justaclient.common.potions.BrewGraph.Mix(water, "minecraft:nether_wart", awkward),
			new dev.justaclient.common.potions.BrewGraph.Mix(awkward, "minecraft:sugar", swift),
			new dev.justaclient.common.potions.BrewGraph.Mix(swift, "minecraft:gunpowder", splashSwift)));
		var splashRoute = both.route(splashSwift).orElse(List.of()).stream().map(dev.justaclient.common.potions.BrewGraph.Step::reagent).toList();
		check("a splash route adds the gunpowder last", splashRoute.equals(List.of("minecraft:nether_wart", "minecraft:sugar", "minecraft:gunpowder")),
			splashRoute.toString());
		com.google.gson.JsonObject recipe = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:brewing\",\"input\":{\"item\":"
			+ "\"minecraft:potion\",\"potion_contents\":{\"potions\":\"minecraft:water\"}},\"output\":{\"components\":{\"minecraft:potion_contents\":"
			+ "{\"potion\":\"minecraft:awkward\"}},\"id\":\"minecraft:potion\"},\"reagent\":{\"item\":\"minecraft:nether_wart\"}}").getAsJsonObject();
		check("brewing recipe file is read", dev.justaclient.common.potions.BrewGraph.parseRecipe(recipe)
			.equals(java.util.Optional.of(new dev.justaclient.common.potions.BrewGraph.Mix(water, "minecraft:nether_wart", awkward))), recipe.toString());
		recipe.getAsJsonObject("reagent").addProperty("item", "#minecraft:wart_tag");
		check("a tag ingredient is skipped, not misread", dev.justaclient.common.potions.BrewGraph.parseRecipe(recipe).isEmpty(), "read it");
		// Item stacks can't be made this early at start-up, so the fuel checks run from /jselftest in a world.
		if (net.minecraft.client.Minecraft.getInstance().level == null) {
			lines.add("SKIP fuel times (run /jselftest in a world)");
		} else {
			int coal = dev.justaclient.inventory.InvCompat.burnTicks(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), false);
			int fast = dev.justaclient.inventory.InvCompat.burnTicks(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), true);
			check("coal burns 1600 ticks, 800 in a blast furnace", coal == 1600 && fast == 800, coal + " / " + fast);
			// An empty fuel slot is counted in coal, which lasts half as long in a blast furnace: 16 items at 100 ticks need 2 there.
			int regular = dev.justaclient.feature.impl.FurnaceInfoFeature.shortBy(16f, 0f, 100,
				dev.justaclient.feature.impl.FurnaceInfoFeature.coalTicks(false));
			int blast = dev.justaclient.feature.impl.FurnaceInfoFeature.shortBy(16f, 0f, 100,
				dev.justaclient.feature.impl.FurnaceInfoFeature.coalTicks(true));
			check("empty fuel slot: short by 1 coal in a furnace, 2 in a blast furnace", regular == 1 && blast == 2, regular + " / " + blast);
			check("stone is not fuel", dev.justaclient.inventory.InvCompat.burnTicks(new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.STONE), false) == 0, "it is");
		}
		var real = dev.justaclient.inventory.InvCompat.brewing();
		if (real.isEmpty()) {
			lines.add("SKIP the game's brewing rules (join a world on 1.21.11 to load them)");
		} else {
			var steps = real.route(swift).orElse(List.of());
			check("the game's rules brew Swiftness with nether wart then sugar", steps.stream().map(s -> s.reagent()).toList()
				.equals(List.of("minecraft:nether_wart", "minecraft:sugar")), steps.toString());
			check("the game's rules make splash potions with gunpowder", real.result(swift, "minecraft:gunpowder").equals(java.util.Optional.of(splashSwift)),
				real.result(swift, "minecraft:gunpowder").toString());
		}
	}

	private static void biomeLooks() {
		var feature = dev.justaclient.feature.impl.BiomeEffectsFeature.get();
		check("biome effects: swamp has a preset look", feature.existing("minecraft:swamp") != null
			&& feature.existing("minecraft:swamp").changesAnything(), "no swamp preset");
		var look = new dev.justaclient.feature.impl.BiomeEffectsFeature.Look();
		check("biome effects: a blank look changes nothing", !look.changesAnything(), "blank look changes something");
		look.fog.set(true);
		look.fogColor.set("#336699");
		look.ambience.set(dev.justaclient.feature.impl.BiomeEffectsFeature.Ambience.SOUL);
		var back = dev.justaclient.feature.impl.BiomeEffectsFeature.Look.load(look.save());
		check("biome effects: a look survives saving", back.fog.on() && back.fogColor.argb() == look.fogColor.argb()
			&& back.ambience.get() == look.ambience.get() && back.save().equals(look.save()), back.save().toString());
		var store = feature.settings().stream().filter(st -> st.id.equals("biomes")).findFirst().orElse(null);
		var saved = store == null ? null : store.save();
		if (store != null) {
			store.load(saved);
		}
		check("biome effects: the biome list loads back the same", store != null && store.save().equals(saved), "differs after a round trip");
		float[] now = {0.2f, 0.4f, 0.6f, 0.5f};
		dev.justaclient.feature.impl.BiomeEffectsFeature.blendTo(now, 0xFFFF0000, 0f, 0.5f);
		check("biome effects: fading out keeps the colour", now[0] == 0.2f && now[1] == 0.4f && Math.abs(now[3] - 0.25f) < 1e-4,
			java.util.Arrays.toString(now));
		dev.justaclient.feature.impl.BiomeEffectsFeature.blendTo(now, 0xFFFF0000, 1f, 1f);
		check("biome effects: a full blend reaches the target", Math.abs(now[0] - 1f) < 1e-4 && now[1] == 0f && Math.abs(now[3] - 1f) < 1e-4,
			java.util.Arrays.toString(now));
		var base = new org.joml.Vector4f(0.1f, 0.1f, 0.1f, 1f);
		var mixed = dev.justaclient.feature.impl.FogFeature.mix(base, 1f, 0f, 0f, 1f, 0.1f, true);
		check("biome effects: overworld fog colour keeps vanilla brightness", Math.abs(mixed.x - 0.1f) < 1e-4 && mixed.y == 0f,
			mixed.x + "," + mixed.y);
	}

	private static void capeLayout() {
		int[] tex = new int[64 * 32];
		tex[5] = 0xFF123456;
		var t = dev.justaclient.common.cosmetics.CapeLayout.build(tex, 64, 32);
		check("cape: a 64 x 32 cape texture is used as it is", !t.fitted() && t.width() == 64 && t.at(5, 0) == 0xFF123456, "changed");
		int[] of = new int[92 * 44];
		of[92 * 3 + 7] = 0xFF00FF00;
		t = dev.justaclient.common.cosmetics.CapeLayout.build(of, 92, 44);
		check("cape: an OptiFine 92 x 44 cape is padded to 128 x 64", !t.fitted() && t.width() == 128 && t.height() == 64 && t.at(7, 3) == 0xFF00FF00,
			t.width() + "x" + t.height());
		int[] pic = new int[20 * 32];
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 20; x++) {
				pic[y * 20 + x] = x < 10 ? 0xFFFF0000 : 0xFF0000FF;
			}
		}
		t = dev.justaclient.common.cosmetics.CapeLayout.build(pic, 20, 32);
		int s = t.width() / 64;
		check("cape: a picture is fitted at scale 2", t.fitted() && s == 2 && t.height() == 64, t.width() + "x" + t.height());
		check("cape: the outside shows the picture", t.at(1 * s, 1 * s) == 0xFFFF0000 && t.at(11 * s - 1, 1 * s) == 0xFF0000FF,
			Integer.toHexString(t.at(1 * s, 1 * s)));
		// The inside is mirrored, so the red half is on the right there, and darker.
		check("cape: the inside is a darker mirror", t.at(22 * s - 1, 1 * s) == 0xFFA50000 && t.at(12 * s, 1 * s) == 0xFF0000A5,
			Integer.toHexString(t.at(22 * s - 1, 1 * s)));
		check("cape: the elytra wing is painted too", t.at(24 * s, 2 * s) == 0xFFFF0000, Integer.toHexString(t.at(24 * s, 2 * s)));
		check("cape: the edge carries the picture's border", t.at(0, 1 * s) == 0xFFFF0000 && t.at(11 * s, 1 * s) == 0xFF0000FF, "edge differs");
	}

	/** Spatial menus: the panel maths that both the shader and the mouse use. */
	private static void spatialPanel() {
		dev.justaclient.common.spatial.SpatialPanel flat = dev.justaclient.common.spatial.SpatialPanel.place(2, 0, 1.6, 0.9, 0, 0, 0);
		double[] mid = flat.hit(0, 0, -1, false);
		check("spatial: a ray straight ahead hits the middle of a flat panel", mid != null && Math.abs(mid[0]) < 1e-9 && Math.abs(mid[1]) < 1e-9,
			java.util.Arrays.toString(mid));
		double[] corner = flat.hit(1.6, 0.9, -2, false);
		check("spatial: the top right corner maps to (1, 1)", corner != null && Math.abs(corner[0] - 1) < 1e-9 && Math.abs(corner[1] - 1) < 1e-9,
			java.util.Arrays.toString(corner));
		check("spatial: a ray past the edge misses unless extended", flat.hit(3, 0, -2, false) == null && flat.hit(3, 0, -2, true) != null, "");
		boolean roundTrip = true;
		for (dev.justaclient.common.spatial.SpatialPanel p : List.of(dev.justaclient.common.spatial.SpatialPanel.place(2.4, 0.1, 1.6, 0.9, 12, -20, 0),
			dev.justaclient.common.spatial.SpatialPanel.place(2.4, -0.2, 1.6, 0.9, 6, 15, 0.6))) {
			for (double u = -0.9; u <= 0.9; u += 0.3) {
				for (double v = -0.9; v <= 0.9; v += 0.45) {
					double[] at = p.point(u, v);
					double[] back = p.hit(at[0], at[1], at[2], false);
					roundTrip &= back != null && Math.abs(back[0] - u) < 1e-6 && Math.abs(back[1] - v) < 1e-6;
				}
			}
		}
		check("spatial: points on leaned, turned and curved panels map back to themselves", roundTrip, "a point came back elsewhere");
		double[] gui = dev.justaclient.common.spatial.SpatialPanel.panelToGui(1, 1, 400, 200);
		double[] uv = dev.justaclient.common.spatial.SpatialPanel.guiToPanel(100, 150, 400, 200);
		check("spatial: panel and GUI coordinates convert both ways", gui[0] == 400 && gui[1] == 0 && uv[0] == -0.5 && uv[1] == -0.5,
			java.util.Arrays.toString(gui) + " " + java.util.Arrays.toString(uv));
	}

	/** Modrinth browser: addresses, parsing, hash checks and safe file names, without the network. */
	/** Review fixes: pinned dependency versions, and an update whose old version cannot be switched off. */
	private static void modrinthInstaller() throws IOException {
		var deps = dev.justaclient.common.modrinth.Modrinth.parseVersions("[{\"id\":\"main\",\"project_id\":\"m\",\"version_type\":\"release\","
			+ "\"files\":[{\"url\":\"u\",\"filename\":\"m.jar\",\"primary\":true}],\"dependencies\":[{\"project_id\":null,"
			+ "\"version_id\":\"pinned\",\"dependency_type\":\"required\"}]}]").getFirst().dependencies().getFirst();
		var pinned = dev.justaclient.common.modrinth.Modrinth.parseVersion("{\"id\":\"pinned\",\"project_id\":\"lib\",\"version_number\":\"2.0\","
			+ "\"version_type\":\"release\",\"game_versions\":[\"1.21.11\"],\"loaders\":[\"fabric\"],"
			+ "\"files\":[{\"url\":\"u\",\"filename\":\"lib-2.0.jar\",\"primary\":true}]}");
		var newer = dev.justaclient.common.modrinth.Modrinth.parseVersion("{\"id\":\"newer\",\"project_id\":\"lib\",\"version_number\":\"3.0\","
			+ "\"version_type\":\"release\",\"game_versions\":[\"1.21.11\"],\"loaders\":[\"fabric\"],"
			+ "\"files\":[{\"url\":\"u\",\"filename\":\"lib-3.0.jar\",\"primary\":true}]}");
		var plan = dev.justaclient.common.modrinth.Modrinth.plan(deps, pinned, List.of(newer), null, "1.21.11", "fabric");
		check("modrinth: a dependency named only by its version id is resolved, not skipped", deps.projectId() == null && "pinned".equals(deps.versionId())
			&& plan.install() != null && plan.install().id().equals("pinned") && plan.problem() == null, plan.toString());
		var wrongGame = dev.justaclient.common.modrinth.Modrinth.plan(deps, pinned, List.of(), null, "26.3", "fabric");
		check("modrinth: a pinned version for another game version is reported", wrongGame.install() == null && wrongGame.problem() != null,
			wrongGame.toString());
		var missing = dev.justaclient.common.modrinth.Modrinth.plan(deps, null, List.of(), null, "1.21.11", "fabric");
		check("modrinth: a pinned version that cannot be found is reported", missing.install() == null && missing.problem() != null, missing.toString());
		var other = dev.justaclient.common.modrinth.Modrinth.plan(deps, pinned, List.of(), "older", "1.21.11", "fabric");
		var same = dev.justaclient.common.modrinth.Modrinth.plan(deps, pinned, List.of(), "pinned", "1.21.11", "fabric");
		check("modrinth: an installed dependency in another version is reported, the same version is left alone",
			other.install() == null && other.problem() != null && same.install() == null && same.problem() == null, other + " / " + same);
		var byProject = new dev.justaclient.common.modrinth.Modrinth.Dependency("lib", null, "required");
		var picked = dev.justaclient.common.modrinth.Modrinth.plan(byProject, null, List.of(pinned, newer), null, "1.21.11", "fabric");
		var none = dev.justaclient.common.modrinth.Modrinth.plan(byProject, null, List.of(), null, "1.21.11", "fabric");
		check("modrinth: a dependency named by project gets its best version, or a reason", picked.install() != null
			&& picked.install().id().equals("pinned") && none.install() == null && none.problem() != null, picked + " / " + none);

		Path dir = Files.createTempDirectory("jac-selftest-update");
		try {
			Path old = dir.resolve("demo-1.jar");
			Path oldOff = dir.resolve("demo-1.jar.disabled");
			Path fresh = dir.resolve("demo-2.jar");
			Files.writeString(old, "old");
			Files.writeString(oldOff, "older copy");
			Files.writeString(fresh, "new");
			var demo = mod("demo", java.util.Set.of("demo"), java.util.Set.of(), dev.justaclient.common.mods.ModStatus.ENABLED, old);
			var r = dev.justaclient.common.modrinth.ModUpdate.retireOld(demo, fresh);
			check("modrinth update: when the old version cannot be turned off, the new one is set aside and it says so", !r.oldOff() && !r.newActive()
				&& !Files.exists(fresh) && Files.exists(dir.resolve("demo-2.jar.disabled")) && r.message().contains("could not be turned off")
				&& Files.readString(old).equals("old") && Files.readString(oldOff).equals("older copy"), r.toString());
			Files.delete(oldOff);
			Files.writeString(fresh, "new");
			var ok = dev.justaclient.common.modrinth.ModUpdate.retireOld(demo, fresh);
			check("modrinth update: normally the old version is turned off and the new one stays on", ok.oldOff() && ok.newActive()
				&& Files.exists(fresh) && !Files.exists(old) && Files.exists(oldOff) && ok.message().contains("turned off"), ok.toString());
		} finally {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path f : files.toList()) {
					Files.deleteIfExists(f);
				}
			}
			Files.deleteIfExists(dir);
		}
	}

	private static void modrinth() {
		String url = dev.justaclient.common.modrinth.Modrinth.searchUrl("sodium extra", dev.justaclient.common.modrinth.Modrinth.Kind.MOD, "1.21.11",
			"fabric", dev.justaclient.common.modrinth.Modrinth.Sort.DOWNLOADS, 20, 20);
		String facets = java.net.URLDecoder.decode(url.substring(url.indexOf("facets=") + 7).split("&")[0], java.nio.charset.StandardCharsets.UTF_8);
		check("modrinth: mod search is limited to this version, Fabric and client-side mods",
			facets.contains("project_type:mod") && facets.contains("versions:1.21.11") && facets.contains("categories:fabric")
				&& facets.contains("client_side:required") && url.contains("query=sodium+extra") && url.contains("offset=20"), url);
		String shader = dev.justaclient.common.modrinth.Modrinth.versionsUrl("abc", dev.justaclient.common.modrinth.Modrinth.Kind.SHADER, "1.21.11", "fabric");
		check("modrinth: shader versions are asked for Iris", java.net.URLDecoder.decode(shader, java.nio.charset.StandardCharsets.UTF_8)
			.contains("loaders=[\"iris\"]"), shader);
		var page = dev.justaclient.common.modrinth.Modrinth.parseSearch("{\"hits\":[{\"project_id\":\"AANobbMI\",\"slug\":\"sodium\",\"title\":\"Sodium\","
			+ "\"author\":\"jelly\",\"downloads\":258929242,\"categories\":[\"fabric\",\"optimization\"],\"project_type\":\"mod\"}],"
			+ "\"offset\":0,\"total_hits\":44}");
		check("modrinth: search results parse", page.total() == 44 && page.hits().size() == 1 && page.hits().getFirst().slug().equals("sodium")
			&& page.hits().getFirst().url().equals("https://modrinth.com/mod/sodium"), page.toString());
		var versions = dev.justaclient.common.modrinth.Modrinth.parseVersions("[{\"id\":\"b\",\"project_id\":\"p\",\"version_type\":\"beta\","
			+ "\"date_published\":\"2026-09-20T21:25:58Z\",\"files\":[{\"url\":\"https://cdn.modrinth.com/b.jar\",\"filename\":\"b.jar\","
			+ "\"primary\":true,\"hashes\":{\"sha1\":\"AB\",\"sha512\":\"CD\"}}],\"dependencies\":[{\"project_id\":\"P1\","
			+ "\"dependency_type\":\"required\"},{\"project_id\":\"P2\",\"dependency_type\":\"optional\"}]},"
			+ "{\"id\":\"r\",\"project_id\":\"p\",\"version_type\":\"release\",\"files\":[{\"url\":\"u\",\"filename\":\"r.jar\"}]}]");
		var best = dev.justaclient.common.modrinth.Modrinth.best(versions);
		check("modrinth: the newest release is offered over a newer beta", best != null && best.id().equals("r"), String.valueOf(best));
		var beta = versions.getFirst();
		check("modrinth: files, hashes (lower case) and required dependencies parse", beta.primaryFile() != null
			&& beta.primaryFile().sha512().equals("cd") && beta.date().equals("2026-09-20")
			&& beta.dependencies().stream().filter(dev.justaclient.common.modrinth.Modrinth.Dependency::required).count() == 1, beta.toString());
		byte[] data = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		String sha512 = "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043";
		var good = new dev.justaclient.common.modrinth.Modrinth.FileRef("u", "f.jar", "", sha512, 5, true);
		var bad = new dev.justaclient.common.modrinth.Modrinth.FileRef("u", "f.jar", "", sha512.replace('9', '8'), 5, true);
		check("modrinth: a download is kept only if its SHA-512 matches", dev.justaclient.common.modrinth.Modrinth.matches(data, good)
			&& !dev.justaclient.common.modrinth.Modrinth.matches(data, bad), "");
		var kind = dev.justaclient.common.modrinth.Modrinth.Kind.MOD;
		check("modrinth: file names cannot leave the folder or change type", "evil.jar".equals(dev.justaclient.common.modrinth.Modrinth.safeFileName(
			"../../evil.jar", kind)) && dev.justaclient.common.modrinth.Modrinth.safeFileName("pack.zip", kind) == null
			&& "a_b.zip".equals(dev.justaclient.common.modrinth.Modrinth.safeFileName("a?b.zip", dev.justaclient.common.modrinth.Modrinth.Kind.RESOURCE_PACK))
			&& dev.justaclient.common.modrinth.Modrinth.safeFileName("..", kind) == null, "");
		var found = dev.justaclient.common.modrinth.Modrinth.parseVersionFiles("{\"ABC\":{\"id\":\"v\",\"project_id\":\"p\"}}");
		check("modrinth: installed files are matched back to projects", found.containsKey("abc") && found.get("abc").projectId().equals("p"), found.toString());
		check("modrinth: counts read short", dev.justaclient.common.modrinth.Modrinth.shortCount(258929242).equals("258.9M")
			&& dev.justaclient.common.modrinth.Modrinth.shortCount(1000).equals("1k") && dev.justaclient.common.modrinth.Modrinth.shortCount(812).equals("812"),
			dev.justaclient.common.modrinth.Modrinth.shortCount(258929242));
	}

	/** Review fixes: the friends list is synced on every connection (even empty) and writes never wait on the network. */
	private static void relaySync() {
		var m = new dev.justaclient.feature.impl.MessagingFeature();
		String none = dev.justaclient.common.relay.RelayProtocol.friends(List.of());
		List<String> sent = new ArrayList<>();
		m.resetSync();
		m.syncState(sent::add, List.of(), "", "", "");
		check("messages: a new connection with no friends still sends an empty friends list", sent.contains(none), sent.toString());
		sent.clear();
		m.syncState(sent::add, List.of(), "", "", "");
		check("messages: nothing is sent again while nothing changed", sent.isEmpty(), sent.toString());
		m.resetSync();
		m.syncState(sent::add, List.of(), "", "", "");
		check("messages: a reconnect sends the empty friends list again", sent.contains(none), sent.toString());
		sent.clear();
		m.syncState(sent::add, List.of("Bob"), "", "", "");
		m.syncState(sent::add, List.of(), "", "", "");
		check("messages: removing every friend tells the relay", !sent.isEmpty() && sent.getLast().equals(none), sent.toString());
		m.resetSync();
		m.syncState(frame -> false, List.of(), "", "", "");
		sent.clear();
		m.syncState(sent::add, List.of(), "", "", "");
		check("messages: a frame that could not be sent is tried again", sent.contains(none), sent.toString());

		List<String> texts = new ArrayList<>();
		List<java.util.concurrent.CompletableFuture<java.net.http.WebSocket>> writes = new ArrayList<>();
		boolean[] aborted = {false};
		java.net.http.WebSocket fake = new java.net.http.WebSocket() {
			@Override
			public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendText(CharSequence data, boolean last) {
				texts.add(data.toString());
				var f = new java.util.concurrent.CompletableFuture<java.net.http.WebSocket>();
				writes.add(f);
				return f;
			}

			@Override
			public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendBinary(java.nio.ByteBuffer data, boolean last) {
				return java.util.concurrent.CompletableFuture.completedFuture(this);
			}

			@Override
			public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendPing(java.nio.ByteBuffer message) {
				return java.util.concurrent.CompletableFuture.completedFuture(this);
			}

			@Override
			public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendPong(java.nio.ByteBuffer message) {
				return java.util.concurrent.CompletableFuture.completedFuture(this);
			}

			@Override
			public java.util.concurrent.CompletableFuture<java.net.http.WebSocket> sendClose(int statusCode, String reason) {
				return java.util.concurrent.CompletableFuture.completedFuture(this);
			}

			@Override
			public void request(long n) {
			}

			@Override
			public String getSubprotocol() {
				return "";
			}

			@Override
			public boolean isOutputClosed() {
				return false;
			}

			@Override
			public boolean isInputClosed() {
				return false;
			}

			@Override
			public void abort() {
				aborted[0] = true;
			}
		};
		var relay = new dev.justaclient.feedback.RelayClient();
		relay.testAttach(fake);
		long start = System.nanoTime();
		boolean one = relay.send("one");
		boolean two = relay.send("two");
		long ms = (System.nanoTime() - start) / 1_000_000;
		check("relay: sending returns at once while a write is still going out", one && two && ms < 100, ms + " ms");
		check("relay: frames go out one at a time, in order", texts.equals(List.of("one")) && relay.pendingWrites() == 2,
			texts + ", " + relay.pendingWrites() + " waiting");
		writes.getFirst().complete(fake);
		check("relay: the next frame goes out once the previous one is written", texts.equals(List.of("one", "two")), texts.toString());
		writes.get(1).completeExceptionally(new IOException("broken pipe"));
		check("relay: a failed write drops the connection to reconnect", aborted[0]
			&& relay.state() == dev.justaclient.feedback.RelayClient.State.WAITING && relay.error() != null, relay.state() + ", " + relay.error());
		check("relay: nothing is sent after a failed write", !relay.send("three") && texts.size() == 2, texts.toString());
	}

	/** Review fix: a delayed Auto GG belongs to the connection and world it was triggered in. */
	private static void autoGgOrigin() {
		Object a = new Object();
		Object b = new Object();
		Object worldA = new Object();
		Object worldB = new Object();
		var gg = new dev.justaclient.common.chat.GgSend();
		gg.schedule(a, worldA, "gg", 2);
		String out = gg.tick(b, worldB, true);
		boolean later = false;
		for (int i = 0; i < 5; i++) {
			later |= gg.tick(a, worldA, true) != null;
		}
		check("auto gg: triggered on server A, moved to B during the delay: nothing is said", out == null && !later && !gg.pending(), "sent");
		gg.schedule(a, worldA, "gg", 2);
		check("auto gg: a new world on the same connection drops it", gg.tick(a, worldB, true) == null && !gg.pending(), "kept");
		gg.schedule(a, worldA, "gg", 2);
		check("auto gg: a disconnect drops it", gg.tick(null, null, true) == null && !gg.pending(), "kept");
		gg.schedule(a, worldA, "gg", 2);
		check("auto gg: a server that is no longer allowed drops it", gg.tick(a, worldA, false) == null && !gg.pending(), "kept");
		gg.schedule(a, worldA, "gg", 2);
		List<String> said = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			String s = gg.tick(a, worldA, true);
			if (s != null) {
				said.add(i + ":" + s);
			}
		}
		check("auto gg: same server and world: said once, after the delay", said.equals(List.of("2:gg")), said.toString());
	}

	private static void relayProtocol() throws IOException {
		check("relay: https Worker address becomes wss://.../relay",
			"wss://justaclient.me.workers.dev/relay".equals(dev.justaclient.common.relay.RelayProtocol.socketUrl("https://justaclient.me.workers.dev/")),
			String.valueOf(dev.justaclient.common.relay.RelayProtocol.socketUrl("https://justaclient.me.workers.dev/")));
		check("relay: http and bare addresses", "ws://localhost:8787/relay".equals(dev.justaclient.common.relay.RelayProtocol.socketUrl("http://localhost:8787"))
			&& "wss://x.dev/relay".equals(dev.justaclient.common.relay.RelayProtocol.socketUrl("x.dev"))
			&& dev.justaclient.common.relay.RelayProtocol.socketUrl("  ") == null, "wrong address");
		var dm = dev.justaclient.common.relay.RelayProtocol.parse(dev.justaclient.common.relay.RelayProtocol.dm("a1", "Bob", "x".repeat(700)));
		check("relay: a message is cut to 500 characters before sending", dm != null && dev.justaclient.common.relay.RelayProtocol.str(dm, "text").length() == 500,
			String.valueOf(dm));
		var p = dev.justaclient.common.relay.RelayProtocol.presence(dev.justaclient.common.relay.RelayProtocol.parse(
			"{\"t\":\"presence\",\"name\":\"Bob\",\"online\":true,\"status\":\"Mining\",\"lastSeen\":5}"));
		check("relay: presence is read", p.online() && !p.pending() && p.status().equals("Mining") && p.lastSeen() == 5, p.toString());
		check("relay: junk frames are ignored", dev.justaclient.common.relay.RelayProtocol.parse("not json") == null
			&& dev.justaclient.common.relay.RelayProtocol.parse("[1,2]") == null, "parsed junk");
		var c = new dev.justaclient.common.relay.Conversations();
		c.add("Bob", new dev.justaclient.common.relay.Conversations.Message("1", false, "hi", 1, ""));
		c.add("bob", new dev.justaclient.common.relay.Conversations.Message("1", false, "hi", 1, ""));
		c.add("Bob", new dev.justaclient.common.relay.Conversations.Message("2", true, "yo", 2, "sending"));
		check("messages: one conversation per friend, duplicates dropped", c.with("BOB").size() == 2 && c.unread("bob") == 1, c.with("bob").size() + "");
		check("messages: an ack updates your message", c.setState("2", "delivered") && "delivered".equals(c.with("Bob").get(1).state), "not updated");
		Path tmp = Files.createTempFile("jac-messages", ".json");
		try {
			c.save(tmp);
			var back = new dev.justaclient.common.relay.Conversations();
			back.load(tmp);
			check("messages: history survives saving", back.with("bob").size() == 2 && back.unread("bob") == 1 && "yo".equals(back.with("bob").get(1).text),
				back.with("bob").size() + "");
			Files.writeString(tmp, "{broken");
			back.load(tmp);
			check("messages: a broken history file loads as empty", back.with("bob").isEmpty(), "kept data");
		} finally {
			Files.deleteIfExists(tmp);
		}
		for (int i = 0; i < dev.justaclient.common.relay.Conversations.MAX + 20; i++) {
			c.add("Cara", new dev.justaclient.common.relay.Conversations.Message("m" + i, true, "n", i, "delivered"));
		}
		check("messages: a conversation keeps its newest " + dev.justaclient.common.relay.Conversations.MAX,
			c.with("cara").size() == dev.justaclient.common.relay.Conversations.MAX && "m20".equals(c.with("cara").getFirst().id), c.with("cara").size() + "");
		check("messages: off by default", !dev.justaclient.feature.impl.MessagingFeature.get().enabledByDefault(), "on by default");
	}

	private static void check(String name, boolean ok, String detail) {
		if (ok) {
			lines.add("PASS " + name);
		} else {
			fail(name, detail);
		}
	}

	private static void fail(String name, String detail) {
		failed++;
		lines.add("FAIL " + name + ": " + detail);
	}

	/** Runs the checks at start-up when the game was launched with -Djustaclient.selftest=true. */
	public static void maybeRunAtStartup() {
		if (Boolean.getBoolean("justaclient.selftest")) {
			run();
			JacConfig.save();
		}
	}
}
