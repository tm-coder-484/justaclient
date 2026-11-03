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
		String profile = Profiles.active();
		Profiles.save();
		int before = Profiles.fingerprint();
		try {
			lifecycle();
			filenameCollision(profile);
			malformed(profile);
			hudRoundTrip(profile);
			failedBackupBlocksReset();
			retryTarget();
			uniqueIds();
			modSwitching();
			listsAndKeys();
		} catch (RuntimeException | IOException e) {
			fail("unexpected exception", e.toString());
		} finally {
			if (!Profiles.active().equals(profile)) {
				Profiles.switchTo(profile);
			}
		}
		check("setup unchanged after all checks", Profiles.fingerprint() == before, "fingerprint differs");
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
		Path existing = Profiles.DIR.resolve("SelfTest A_B.json");
		Files.writeString(existing, "{\"features\":{}}");
		String imported = null;
		try {
			JsonObject root = JsonParser.parseString("{\"name\":\"SelfTest A/B\",\"features\":{}}").getAsJsonObject();
			imported = Profiles.importJson(root, "Imported", false);
			check("import named A/B does not reuse A_B.json", imported != null && !imported.equals("SelfTest A_B"), "got " + imported);
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
			String name = Profiles.importJson(root, "SelfTest Bad", false);
			check("import rejects " + text + " without changes", name == null && Profiles.fingerprint() == print && Profiles.active().equals(profile),
				"imported as " + name);
		}
		Path file = Profiles.EXPORTS.resolve("selftest-bad.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, bad[1]);
		try {
			Profiles.Preview preview = Profiles.preview(file);
			check("preview of a malformed file is empty", preview.root() == null, "had a root");
		} finally {
			Files.deleteIfExists(file);
		}
		// A malformed active profile on load is kept aside and the client starts from defaults.
		Path broken = Profiles.DIR.resolve("SelfTest Broken.json");
		Files.writeString(broken, bad[0]);
		try {
			Profiles.switchTo("SelfTest Broken");
			boolean kept;
			try (Stream<Path> files = Files.list(Profiles.DIR)) {
				kept = files.anyMatch(p -> p.getFileName().toString().startsWith("SelfTest Broken.json.") && p.toString().endsWith(".broken"));
			}
			check("unreadable profile kept as .broken on load", kept, "no .broken copy");
		} finally {
			Profiles.switchTo(profile);
			Files.deleteIfExists(broken);
			try (Stream<Path> files = Files.list(Profiles.DIR)) {
				for (Path p : files.filter(p -> p.getFileName().toString().startsWith("SelfTest Broken")).toList()) {
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
			imported = root == null ? null : Profiles.importJson(root, "SelfTest HUD", false);
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
	}

	private static void failedBackupBlocksReset() throws IOException {
		Path backups = Profiles.BACKUPS;
		Path aside = backups.resolveSibling("backups-selftest-aside");
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
		AutoReconnectFeature.clearTarget();
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
