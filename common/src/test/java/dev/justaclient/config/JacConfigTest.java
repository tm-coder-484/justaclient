package dev.justaclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.justaclient.TestGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JacConfigTest {
	private static Path file;

	@BeforeAll
	static void game() {
		TestGame.dir();
		file = TestGame.config().resolveSibling("justaclient.json");
	}

	@BeforeEach
	void clean() throws IOException {
		Files.createDirectories(file.getParent());
		try (Stream<Path> files = Files.list(file.getParent())) {
			for (Path f : files.filter(f -> f.getFileName().toString().startsWith("justaclient.json")).toList()) {
				Files.delete(f);
			}
		}
	}

	@Test
	void saveThenLoadRoundTrips() {
		JacConfig.load();
		JacConfig.get().profile = "PvP";
		JacConfig.get().style.accent = "#FF0000";
		JacConfig.get().style.ring.speed = 2;
		JacConfig.rememberSearch("zoom");
		JacConfig.save();
		JacConfig.get().profile = "changed in memory";
		JacConfig.load();
		assertEquals("PvP", JacConfig.get().profile);
		assertEquals("#FF0000", JacConfig.get().style.accent);
		assertEquals(2, JacConfig.get().style.ring.speed);
		assertEquals("zoom", JacConfig.get().searchHistory.getFirst());
		assertEquals(0xFFFF0000, JacConfig.accent());
		assertFalse(Files.exists(file.resolveSibling("justaclient.json.tmp")));
	}

	@Test
	void oddValuesAreCleanedUp() throws IOException {
		Files.writeString(file, """
			{"profile": "  ", "searchHistory": [null, "", "ok"],
			 "style": {"accent": "not a colour", "outline": "SPARKLY", "surfaceOpacity": 7, "blurStrength": 99, "ring": null}}
			""");
		JacConfig.load();
		JacConfig c = JacConfig.get();
		assertEquals("Default", c.profile);
		assertEquals(java.util.List.of("ok"), c.searchHistory);
		assertEquals(StyleTokens.Outline.OFF, c.style.outline);
		assertEquals(1f, c.style.surfaceOpacity);
		assertEquals(5, c.style.blurStrength);
		assertEquals(StyleTokens.RingLook.PARTICLES, c.style.ring.look);
		assertEquals(0xFF4C8DFF, JacConfig.accent());
	}

	@Test
	void anUnreadableFileIsKeptAside() throws IOException {
		Files.writeString(file, "{\"profile\": \"PvP\", \"style\": {");
		JacConfig.load();
		try (Stream<Path> files = Files.list(file.getParent())) {
			assertTrue(files.anyMatch(f -> f.getFileName().toString().startsWith("justaclient.json.") && f.toString().endsWith(".broken")));
		}
		// The defaults were saved in its place, as a whole file.
		assertTrue(Files.readString(file).contains("\"profile\""));
	}
}
