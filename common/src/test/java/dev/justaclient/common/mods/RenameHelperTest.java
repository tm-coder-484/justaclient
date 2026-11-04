package dev.justaclient.common.mods;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameHelperTest {
	@TempDir
	Path dir;

	@Test
	void renamesWhenOnlyTheSourceExists() throws IOException {
		Path from = Files.writeString(dir.resolve("a.jar"), "jar");
		Path to = dir.resolve("a.jar.disabled");
		assertEquals(RenameHelper.Outcome.DONE, RenameHelper.tryOnce(from, to));
		assertFalse(Files.exists(from));
		assertEquals("jar", Files.readString(to));
	}

	@Test
	void alreadyRenamedAndGoneAreFinished() throws IOException {
		Path to = Files.writeString(dir.resolve("a.jar"), "jar");
		assertEquals(RenameHelper.Outcome.ALREADY_DONE, RenameHelper.tryOnce(dir.resolve("a.jar.disabled"), to));
		assertEquals(RenameHelper.Outcome.GONE, RenameHelper.tryOnce(dir.resolve("x.jar"), dir.resolve("x.jar.disabled")));
		assertTrue(RenameHelper.Outcome.DONE.finished);
		assertTrue(RenameHelper.Outcome.ALREADY_DONE.finished);
		assertTrue(RenameHelper.Outcome.GONE.finished);
	}

	@Test
	void aConflictOverwritesNothingAndStaysPending() throws IOException {
		Path from = Files.writeString(dir.resolve("a.jar.disabled"), "old");
		Path to = Files.writeString(dir.resolve("a.jar"), "new");
		assertEquals(RenameHelper.Outcome.CONFLICT, RenameHelper.tryOnce(from, to));
		assertEquals("old", Files.readString(from));
		assertEquals("new", Files.readString(to));
		assertFalse(RenameHelper.Outcome.CONFLICT.finished);
	}

	@Test
	void aFailedMoveIsLockedAndStaysPending() throws IOException {
		Path from = Files.writeString(dir.resolve("a.jar"), "jar");
		Path to = dir.resolve("no-such-dir").resolve("a.jar.disabled");
		assertEquals(RenameHelper.Outcome.LOCKED, RenameHelper.tryOnce(from, to));
		assertTrue(Files.exists(from));
		assertFalse(RenameHelper.Outcome.LOCKED.finished);
	}

	@Test
	void applyLogsTheOutcome() throws Exception {
		Path from = Files.writeString(dir.resolve("a.jar"), "jar");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RenameHelper.Outcome outcome = RenameHelper.apply(from, dir.resolve("a.jar.disabled"), new PrintStream(out, true, StandardCharsets.UTF_8));
		assertEquals(RenameHelper.Outcome.DONE, outcome);
		assertTrue(out.toString(StandardCharsets.UTF_8).contains("Renamed"));
	}

	@Test
	void pendingRoundTripsAndSkipsBadLines() throws IOException {
		Path file = dir.resolve("state").resolve("pending.txt");
		List<String[]> entries = List.of(new String[] {"/mods/My Mod 1.0.jar", "/mods/My Mod 1.0.jar.disabled"},
			new String[] {"C:\\mods\\b.jar.disabled", "C:\\mods\\b.jar"});
		RenameHelper.writePending(file, entries);
		List<String[]> back = RenameHelper.readPending(file);
		assertEquals(2, back.size());
		assertArrayEquals(entries.get(0), back.get(0));
		assertArrayEquals(entries.get(1), back.get(1));

		Files.writeString(file, "\n/a.jar\t/a.jar.disabled\n   \nonly-one-part\n\t/x\n/y\t\n /z \t \na\tb\tc\n/b.jar\t/b.jar.disabled\r\n");
		back = RenameHelper.readPending(file);
		assertEquals(2, back.size());
		assertArrayEquals(new String[] {"/a.jar", "/a.jar.disabled"}, back.get(0));
		assertArrayEquals(new String[] {"/b.jar", "/b.jar.disabled"}, back.get(1));
	}

	@Test
	void noPendingEntriesMeansNoFile() throws IOException {
		Path file = dir.resolve("pending.txt");
		assertTrue(RenameHelper.readPending(file).isEmpty());
		RenameHelper.writePending(file, List.<String[]>of(new String[] {"a", "b"}));
		assertTrue(Files.exists(file));
		RenameHelper.writePending(file, List.of());
		assertFalse(Files.exists(file));
	}

	@Test
	void relaunchFileHoldsTheFolderThenOneArgumentPerLine() throws IOException {
		Path file = dir.resolve("relaunch.txt");
		RenameHelper.writeRelaunch(file, "/home/me/.minecraft", List.of("/usr/bin/java", "-Xmx4G", "-cp", "a.jar:b with space.jar", "Main"));
		assertEquals(List.of("/home/me/.minecraft", "/usr/bin/java", "-Xmx4G", "-cp", "a.jar:b with space.jar", "Main"),
			Files.readAllLines(file, StandardCharsets.UTF_8));
	}
}
