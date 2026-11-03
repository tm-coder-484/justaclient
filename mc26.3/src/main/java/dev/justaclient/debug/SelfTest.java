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
