package dev.justaclient.common.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeybindKeeperTest {
	@TempDir
	Path dir;

	@Test
	void readsOnlyKeyLines() throws IOException {
		Path options = Files.writeString(dir.resolve("options.txt"),
			"version:4189\nkey_key.jump:key.keyboard.space\nfov:70\nkey_:broken\nkey_nocolon\nkey_key.mymod.dash:key.keyboard.r\n");
		Map<String, String> binds = KeybindKeeper.readKeybinds(options);
		assertEquals(List.of("key_key.jump", "key_key.mymod.dash"), List.copyOf(binds.keySet()));
		assertEquals("key_key.mymod.dash:key.keyboard.r", binds.get("key_key.mymod.dash"));
	}

	@Test
	void aMissingFileHasNoBinds() {
		assertTrue(KeybindKeeper.readKeybinds(dir.resolve("options.txt")).isEmpty());
		assertTrue(KeybindKeeper.readKeybinds(dir).isEmpty());
	}

	@Test
	void restoresOnlyTheBindsTheGameNoLongerKnows() throws IOException {
		Path options = Files.writeString(dir.resolve("options.txt"),
			"key_key.jump:key.keyboard.space\nkey_key.othermod.dash:key.keyboard.r\nfov:70\n");
		Map<String, String> before = KeybindKeeper.readKeybinds(options);
		Files.writeString(options, "key_key.jump:key.keyboard.w\nfov:70\n");
		KeybindKeeper.restoreMissing(options, before, Set.of("key.jump"));
		assertEquals(List.of("key_key.jump:key.keyboard.w", "fov:70", "key_key.othermod.dash:key.keyboard.r"), Files.readAllLines(options));
	}

	@Test
	void leavesTheFileAloneWhenNothingIsMissing() throws IOException {
		String text = "key_key.jump:key.keyboard.space\n";
		Path options = Files.writeString(dir.resolve("options.txt"), text);
		KeybindKeeper.restoreMissing(options, KeybindKeeper.readKeybinds(options), Set.of("key.jump"));
		assertEquals(text, Files.readString(options));
		KeybindKeeper.restoreMissing(options, Map.of(), Set.of());
		assertEquals(text, Files.readString(options));
	}

	@Test
	void neverCreatesTheOptionsFile() {
		Path options = dir.resolve("options.txt");
		KeybindKeeper.restoreMissing(options, Map.of("key_key.x", "key_key.x:key.keyboard.x"), Set.of());
		assertFalse(Files.exists(options));
	}

	@Test
	void aRestoredBindStartsOnItsOwnLineWhenTheFileHasNoFinalNewline() throws IOException {
		Path options = Files.writeString(dir.resolve("options-nonl.txt"), "key_key.mod.x:key.keyboard.x\nfov:70");
		Map<String, String> before = KeybindKeeper.readKeybinds(options);
		Files.writeString(options, "fov:70");
		KeybindKeeper.restoreMissing(options, before, Set.of());
		assertEquals(List.of("fov:70", "key_key.mod.x:key.keyboard.x"), Files.readAllLines(options));
	}
}
