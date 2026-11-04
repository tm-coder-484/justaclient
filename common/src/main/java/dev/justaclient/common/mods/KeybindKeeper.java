package dev.justaclient.common.mods;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the key binds of switched-off mods. The game only saves binds for keys it knows, so a mod's binds would be lost
 * the first time options are saved without it. The old lines are read before saving and the missing ones put back after.
 * Adapted from Tom's server-switcher mod (CC0).
 */
public final class KeybindKeeper {
	private static final String KEY_PREFIX = "key_";

	private KeybindKeeper() {
	}

	public static Map<String, String> readKeybinds(Path optionsFile) {
		Map<String, String> keybinds = new LinkedHashMap<>();
		if (!Files.isRegularFile(optionsFile)) {
			return keybinds;
		}
		try {
			for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
				int colon = line.indexOf(':');
				if (line.startsWith(KEY_PREFIX) && colon > KEY_PREFIX.length()) {
					keybinds.put(line.substring(0, colon), line);
				}
			}
		} catch (IOException | RuntimeException e) {
			// Nothing to keep.
		}
		return keybinds;
	}

	/** {@code loadedNames} are the names of the key mappings the game knows now. */
	public static void restoreMissing(Path optionsFile, Map<String, String> previous, Set<String> loadedNames) {
		if (previous.isEmpty() || !Files.isRegularFile(optionsFile)) {
			return;
		}
		List<String> missing = new ArrayList<>();
		previous.forEach((name, line) -> {
			if (!loadedNames.contains(name.substring(KEY_PREFIX.length()))) {
				missing.add(line);
			}
		});
		if (missing.isEmpty()) {
			return;
		}
		try {
			// A file edited by hand may not end in a newline; the first restored line must not join its last line.
			byte[] now = Files.readAllBytes(optionsFile);
			if (now.length > 0 && now[now.length - 1] != '\n') {
				missing.addFirst("");
			}
			Files.write(optionsFile, missing, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException e) {
			// Best effort.
		}
	}
}
