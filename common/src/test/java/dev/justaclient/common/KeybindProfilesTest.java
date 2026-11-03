package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeybindProfilesTest {
	@TempDir
	Path dir;

	@Test
	void serverKeyTrimsAndLowerCases() {
		assertEquals("play.example.com:25565", KeybindProfiles.serverKey("  Play.Example.COM:25565\t"));
		assertEquals("[::1]:25565", KeybindProfiles.serverKey("[::1]:25565"));
	}

	@Test
	void loadDropsNullAndBlankProfilesAndServersTiedToMissingOnes() throws IOException {
		Path file = Files.writeString(dir.resolve("keybinds.json"), """
			{"profiles": {"PvP": {"key.jump": "key.keyboard.space"}, "Null": null, " ": {}},
			 "servers": {"x.net": "Gone", "y.net": "PvP", "z.net": "Null", "w.net": null}}""");
		KeybindProfiles k = KeybindProfiles.load(file);
		assertEquals(Set.of("PvP"), k.profiles.keySet());
		assertEquals(Map.of("y.net", "PvP"), k.servers);
		assertEquals("PvP", k.profileFor("Y.NET "));
		assertNull(k.profileFor("x.net"));
	}

	@Test
	void missingSectionsLoadAsEmpty() throws IOException {
		Path file = Files.writeString(dir.resolve("keybinds.json"), "{\"profiles\":null}");
		KeybindProfiles k = KeybindProfiles.load(file);
		assertTrue(k.profiles.isEmpty());
		assertTrue(k.servers.isEmpty());
		assertNull(k.restore);
	}

	@Test
	void setProfileForNullUnties() {
		KeybindProfiles k = new KeybindProfiles();
		k.profiles.put("A", new LinkedHashMap<>());
		k.setProfileFor("Mc.Net", "A");
		assertEquals("A", k.profileFor("mc.net"));
		k.setProfileFor(" MC.NET ", null);
		assertNull(k.profileFor("mc.net"));
		assertTrue(k.servers.isEmpty());
	}

	@Test
	void removeProfileUntiesOnlyItsServers() {
		KeybindProfiles k = new KeybindProfiles();
		k.profiles.put("A", new LinkedHashMap<>());
		k.profiles.put("B", new LinkedHashMap<>());
		k.setProfileFor("a1", "A");
		k.setProfileFor("a2", "A");
		k.setProfileFor("b1", "B");
		k.removeProfile("A");
		assertEquals(Set.of("B"), k.profiles.keySet());
		assertEquals(Map.of("b1", "B"), k.servers);
	}

	@Test
	void restoreSurvivesARoundTrip() throws IOException {
		Path file = dir.resolve("cfg").resolve("keybinds.json");
		KeybindProfiles k = new KeybindProfiles();
		k.profiles.put("PvP", new LinkedHashMap<>(Map.of("key.attack", "key.mouse.left")));
		k.setProfileFor("pvp.net", "PvP");
		k.restore = new LinkedHashMap<>(Map.of("key.attack", "key.mouse.right"));
		k.save(file);
		KeybindProfiles back = KeybindProfiles.load(file);
		assertEquals("key.mouse.left", back.profiles.get("PvP").get("key.attack"));
		assertEquals("PvP", back.profileFor("pvp.net"));
		assertEquals("key.mouse.right", back.restore.get("key.attack"));
	}

	@Test
	void aBrokenFileStartsEmptyAndIsKeptAside() throws IOException {
		Path file = Files.writeString(dir.resolve("keybinds.json"), "{\"profiles\": [");
		assertTrue(KeybindProfiles.load(file).profiles.isEmpty());
		assertTrue(Files.exists(dir.resolve("keybinds.json.broken")));
		assertFalse(Files.exists(file));
	}
}
