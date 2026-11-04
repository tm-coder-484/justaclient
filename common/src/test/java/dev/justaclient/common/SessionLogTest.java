package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLogTest {
	@TempDir
	Path dir;

	private static SessionLog.Session session(String world) {
		SessionLog.Session s = new SessionLog.Session();
		s.world = world;
		s.started = "2026-11-03T10:00:00";
		return s;
	}

	private long brokenCount() throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(f -> f.getFileName().toString().endsWith(".broken")).count();
		}
	}

	@Test
	void missingFileLoadsEmpty() {
		assertTrue(SessionLog.load(dir.resolve("sessions.json")).isEmpty());
	}

	@Test
	void addPutsNewestFirstAndCapsTheList() throws IOException {
		Path file = dir.resolve("sessions.json");
		List<SessionLog.Session> list = SessionLog.load(file);
		for (int i = 0; i < 5; i++) {
			SessionLog.add(file, list, session("w" + i), 3);
		}
		List<SessionLog.Session> loaded = SessionLog.load(file);
		assertEquals(List.of("w4", "w3", "w2"), loaded.stream().map(s -> s.world).toList());
	}

	@Test
	void keepBelowOneStillKeepsTheNewest() throws IOException {
		Path file = dir.resolve("sessions.json");
		List<SessionLog.Session> list = SessionLog.load(file);
		SessionLog.add(file, list, session("a"), 0);
		SessionLog.add(file, list, session("b"), 0);
		List<SessionLog.Session> loaded = SessionLog.load(file);
		assertEquals(1, loaded.size());
		assertEquals("b", loaded.get(0).world);
	}

	@Test
	void everyFieldSurvivesARoundTrip() throws IOException {
		Path file = dir.resolve("deep").resolve("sessions.json");
		SessionLog.Session s = session("Survival");
		s.seconds = 3600;
		s.kills = 2;
		s.deaths = 1;
		s.blocks = 400;
		s.distance = 1234.5;
		s.pickedUp.put("minecraft:dirt", 64);
		s.carried.add(new SessionLog.Carried("minecraft:diamond", 3));
		s.slots.add(new SessionLog.Slot(40, "minecraft:shield", 1, "{\"id\":\"minecraft:shield\"}"));
		s.slots.add(new SessionLog.Slot(0, "minecraft:stone", 12, null));
		s.worldKey = "sp:Survival";
		SessionLog.add(file, SessionLog.load(file), s, 10);

		SessionLog.Session back = SessionLog.load(file).get(0);
		assertEquals("Survival", back.world);
		assertEquals(3600, back.seconds);
		assertEquals(2, back.kills);
		assertEquals(1, back.deaths);
		assertEquals(400, back.blocks);
		assertEquals(1234.5, back.distance);
		assertEquals(64, back.pickedUp.get("minecraft:dirt"));
		assertEquals("minecraft:diamond", back.carried.get(0).item);
		assertEquals(3, back.carried.get(0).count);
		assertEquals(40, back.slots.get(0).slot);
		assertEquals("{\"id\":\"minecraft:shield\"}", back.slots.get(0).stack);
		assertNull(back.slots.get(1).stack);
		assertEquals("sp:Survival", back.worldKey);
		try (Stream<Path> files = Files.list(file.getParent())) {
			assertEquals(1, files.count(), "no temporary file is left behind");
		}
	}

	@Test
	void nullsAreDroppedOrFilledIn() throws IOException {
		Path file = Files.writeString(dir.resolve("sessions.json"),
			"[null, {\"world\":null,\"started\":\"x\"}, {\"world\":\"ok\",\"started\":\"x\",\"pickedUp\":null,\"carried\":null,\"slots\":null,\"worldKey\":null}]");
		List<SessionLog.Session> list = SessionLog.load(file);
		assertEquals(1, list.size());
		SessionLog.Session s = list.get(0);
		assertNotNull(s.pickedUp);
		assertNotNull(s.carried);
		assertNotNull(s.slots);
		assertEquals("", s.worldKey);
	}

	@Test
	void aBrokenFileIsMovedAsideAndNotOverwritten() throws IOException {
		Path file = Files.writeString(dir.resolve("sessions.json"), "[{\"world\":\"lost?");
		List<SessionLog.Session> list = SessionLog.load(file);
		assertTrue(list.isEmpty());
		assertFalse(Files.exists(file));
		assertEquals(1, brokenCount());
		SessionLog.add(file, list, session("fresh"), 5);
		assertEquals(1, brokenCount());
		try (Stream<Path> files = Files.list(dir)) {
			Path broken = files.filter(f -> f.getFileName().toString().endsWith(".broken")).findFirst().orElseThrow();
			assertEquals("[{\"world\":\"lost?", Files.readString(broken));
		}
	}

	@Test
	void aFileOfTheWrongShapeIsAlsoKeptAside() throws IOException {
		Path file = Files.writeString(dir.resolve("sessions.json"), "{\"world\":\"not a list\"}");
		assertTrue(SessionLog.load(file).isEmpty());
		assertEquals(1, brokenCount());
	}
}
