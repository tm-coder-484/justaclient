package dev.justaclient.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.justaclient.TestGame;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The parts of profile import and export that are plain JSON (pasting from the clipboard, files from friends). */
class ProfilesTest {
	@BeforeAll
	static void game() {
		TestGame.dir();
	}

	@Test
	void parseProfileAcceptsWhatExportWrites() {
		String exported = """
			{"version": 1, "name": "PvP",
			 "features": {"zoom": {"enabled": true, "toggle_key": {"key": 67}, "settings": {"fov": 30.0}},
			              "fps_hud": {"enabled": false, "position": {"x": 0.1, "y": 0.9, "scale": 1}}},
			 "style": {"accent": "#FF0000"}}
			""";
		assertNotNull(Profiles.parseProfile(exported));
	}

	@Test
	void parseProfileRejectsAnythingElse() {
		assertNull(Profiles.parseProfile(""));
		assertNull(Profiles.parseProfile("hello there"));
		assertNull(Profiles.parseProfile("[1, 2]"));
		assertNull(Profiles.parseProfile("{\"name\": \"no features\"}"));
		assertNull(Profiles.parseProfile("{\"features\": "));
	}

	@Test
	void validChecksTheShapeAllTheWayDown() {
		assertTrue(Profiles.valid(obj("{}")));
		assertTrue(Profiles.valid(obj("{\"features\": {}}")));
		assertFalse(Profiles.valid(obj("{\"features\": []}")));
		assertFalse(Profiles.valid(obj("{\"features\": {\"zoom\": true}}")));
		assertFalse(Profiles.valid(obj("{\"features\": {\"zoom\": {\"enabled\": \"yes\"}}}")));
		assertFalse(Profiles.valid(obj("{\"features\": {\"zoom\": {\"settings\": [1]}}}")));
		assertFalse(Profiles.valid(obj("{\"features\": {\"hud\": {\"position\": {\"x\": \"left\"}}}}")));
		assertFalse(Profiles.valid(obj("{\"features\": {}, \"style\": \"dark\"}")));
	}

	@Test
	void exportNamesAreSafeFileNames() {
		String stamp = Profiles.exportStamp(LocalDateTime.of(2026, 11, 3, 9, 5));
		assertEquals("2026-11-03_09-05", stamp);
		assertTrue(stamp.matches("[0-9_-]+"));
		assertFalse(Profiles.exportBase(stamp).matches(".*[\\\\/:*?\"<>|].*"));
	}

	private static com.google.gson.JsonObject obj(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}
}
