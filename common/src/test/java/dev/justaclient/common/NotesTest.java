package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotesTest {
	@TempDir
	Path dir;

	@Test
	void saveReplacesTheFileWholeAndLeavesNoTemporaryFile() throws IOException {
		Path file = dir.resolve("sub").resolve("notes.json");
		Notes n = new Notes();
		n.notepad = "first";
		n.save(file);
		n.notepad = "second";
		n.save(file);
		assertEquals("second", Notes.load(file).notepad);
		try (Stream<Path> files = Files.list(file.getParent())) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void nullFieldsLoadAsEmpty() throws IOException {
		Path file = Files.writeString(dir.resolve("notes.json"), "{\"notepad\":null,\"todos\":[null]}");
		Notes n = Notes.load(file);
		assertEquals("", n.notepad);
		assertTrue(n.todos.isEmpty());
	}

	@Test
	void aBrokenFileIsCopiedAsideEachTime() throws IOException, InterruptedException {
		Path file = Files.writeString(dir.resolve("notes.json"), "{\"notepad\":\"keep me");
		assertEquals("", Notes.load(file).notepad);
		Thread.sleep(5);
		Files.writeString(file, "{\"notepad\":\"and me");
		Notes.load(file);
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(2, files.filter(f -> f.getFileName().toString().endsWith(".broken")).count());
		}
		assertFalse(Files.readString(file).isEmpty());
	}
}
