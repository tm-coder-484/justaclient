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
