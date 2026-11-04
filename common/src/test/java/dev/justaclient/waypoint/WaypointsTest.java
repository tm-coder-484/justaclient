package dev.justaclient.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.justaclient.TestGame;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WaypointsTest {
	@BeforeAll
	static void game() {
		TestGame.dir();
	}

	@Test
	void parseDropsBrokenEntriesAndKeepsDefaultsForOldSaves() {
		Map<String, List<Waypoints.Waypoint>> read = Waypoints.parse(new StringReader("""
			{"sp:World": [
			  {"name": "Home", "x": 1, "y": 64, "z": -3, "dimension": "minecraft:overworld", "color": -1},
			  {"name": null, "dimension": "minecraft:overworld"},
			  {"name": "No dimension"},
			  null
			 ],
			 "mp:gone": null}
			"""));
		assertNotNull(read);
		assertEquals(1, read.size());
		List<Waypoints.Waypoint> list = read.get("sp:World");
		assertEquals(1, list.size());
		Waypoints.Waypoint home = list.getFirst();
		assertEquals("Home", home.name);
		assertEquals(-3, home.z);
		// Fields an older save does not have keep their defaults.
		assertTrue(home.visible);
		assertTrue(home.visited);
		assertFalse(home.death);
	}

	@Test
	void tagsAndNames() {
		assertEquals(0, Waypoints.tag(Waypoints.PALETTE[0]));
		assertEquals(-1, Waypoints.tag(0xFF123456));
		assertEquals("Village plains", Waypoints.structureName("minecraft:village_plains"));
		assertEquals("Ancient city", Waypoints.structureName("ancient_city"));
	}

	@Test
	void anUnreadableFileIsSetAsideAndSavingWritesAWholeFile() throws IOException {
		Path file = TestGame.config().resolve("waypoints.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "{\"sp:World\": [ {\"name\": \"Home\", ");
		Waypoints.load();
		assertFalse(Files.exists(file), "the broken file should be moved aside, not kept to be overwritten");
		try (Stream<Path> files = Files.list(file.getParent())) {
			assertEquals(1, files.filter(f -> f.getFileName().toString().startsWith("waypoints.json.") && f.toString().endsWith(".broken")).count());
		}
		Waypoints.save();
		assertNotNull(Waypoints.parse(Files.newBufferedReader(file)));
		assertFalse(Files.exists(file.resolveSibling("waypoints.json.tmp")));
	}
}
