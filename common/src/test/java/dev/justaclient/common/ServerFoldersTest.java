package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerFoldersTest {
	@TempDir
	Path dir;

	@Test
	void loadDropsBlankFoldersNullServersAndUnknownFolderLinks() throws IOException {
		Path file = Files.writeString(dir.resolve("servers.json"), """
			{"folders": ["PvP", "", "  ", null],
			 "servers": {"a.net": {"folder": "PvP", "favourite": true}, "b.net": {"folder": "Gone"}, "c.net": null}}""");
		ServerFolders f = ServerFolders.load(file);
		assertEquals(List.of("PvP"), f.folders);
		assertEquals("PvP", f.peek("a.net").folder);
		assertTrue(f.peek("a.net").favourite);
		assertNull(f.peek("b.net").folder);
		assertNull(f.peek("c.net"));
	}

	@Test
	void addressesAreTrimmedAndCaseInsensitive() {
		ServerFolders f = new ServerFolders();
		ServerFolders.Meta m = f.meta("  Play.Example.COM:25565 ");
		assertSame(m, f.peek("play.example.com:25565"));
		assertNull(f.peek("other.net"));
		assertEquals(1, f.servers.size(), "peek does not create an entry");
	}

	@Test
	void addFolderRejectsBlankAndDuplicatesIgnoringCase() {
		ServerFolders f = new ServerFolders();
		assertTrue(f.addFolder("  Survival "));
		assertFalse(f.addFolder("survival"));
		assertFalse(f.addFolder("   "));
		assertEquals(List.of("Survival"), f.folders);
	}

	@Test
	void renameFolderMovesItsServersAndRefusesTakenNames() {
		ServerFolders f = new ServerFolders();
		f.addFolder("pvp");
		f.addFolder("Build");
		f.meta("a.net").folder = "pvp";
		assertFalse(f.renameFolder("pvp", "build"));
		assertFalse(f.renameFolder("missing", "x"));
		assertFalse(f.renameFolder("pvp", " "));
		assertTrue(f.renameFolder("pvp", "PvP"), "changing only the case of its own name is allowed");
		assertEquals("PvP", f.peek("a.net").folder);
		assertEquals(List.of("PvP", "Build"), f.folders);
	}

	@Test
	void removeFolderPutsItsServersBackInNoFolder() {
		ServerFolders f = new ServerFolders();
		f.addFolder("A");
		f.meta("x.net").folder = "A";
		f.meta("y.net").favourite = true;
		f.removeFolder("A");
		assertTrue(f.folders.isEmpty());
		assertNull(f.peek("x.net").folder);
		assertTrue(f.peek("y.net").favourite);
	}

	@Test
	void moveFolderStaysInBounds() {
		ServerFolders f = new ServerFolders();
		f.addFolder("A");
		f.addFolder("B");
		f.addFolder("C");
		f.moveFolder("A", 2);
		assertEquals(List.of("B", "C", "A"), f.folders);
		f.moveFolder("A", 1);
		f.moveFolder("B", -1);
		f.moveFolder("nope", 1);
		assertEquals(List.of("B", "C", "A"), f.folders);
		f.moveFolder("A", -2);
		assertEquals(List.of("A", "B", "C"), f.folders);
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		Path file = dir.resolve("sub").resolve("servers.json");
		ServerFolders f = new ServerFolders();
		f.addFolder("Friends");
		ServerFolders.Meta m = f.meta("mc.friend.net");
		m.folder = "Friends";
		m.lastJoined = 1_700_000_000_000L;
		f.save(file);
		ServerFolders back = ServerFolders.load(file);
		assertEquals(List.of("Friends"), back.folders);
		assertEquals("Friends", back.peek("mc.friend.net").folder);
		assertEquals(1_700_000_000_000L, back.peek("mc.friend.net").lastJoined);
		assertFalse(Files.exists(file.resolveSibling("servers.json.tmp")));
	}

	@Test
	void aBrokenFileStartsEmptyAndIsKeptAside() throws IOException {
		Path file = Files.writeString(dir.resolve("servers.json"), "{ broken");
		assertTrue(ServerFolders.load(file).folders.isEmpty());
		assertEquals("{ broken", Files.readString(broken("servers.json")));
		assertFalse(Files.exists(file));
	}

	/** The one file set aside as {@code <name>.<time>.broken}. */
	private Path broken(String name) throws IOException {
		try (java.util.stream.Stream<Path> files = Files.list(dir)) {
			List<Path> found = files.filter(f -> f.getFileName().toString().startsWith(name + ".") && f.toString().endsWith(".broken")).toList();
			assertEquals(1, found.size(), found.toString());
			return found.getFirst();
		}
	}
}
