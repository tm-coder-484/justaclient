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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotIndexTest {
	@TempDir
	Path dir;

	@Test
	void addTagTrimsLowerCasesAndRefusesDuplicatesAndBlanks() {
		ScreenshotIndex.Shot shot = new ScreenshotIndex.Shot();
		assertTrue(ScreenshotIndex.addTag(shot, "  Base "));
		assertFalse(ScreenshotIndex.addTag(shot, "BASE"));
		assertFalse(ScreenshotIndex.addTag(shot, "   "));
		assertEquals(List.of("base"), shot.tags);
	}

	@Test
	void searchTextHoldsTheKnownFieldsAndTagsInLowerCase() {
		ScreenshotIndex.Shot shot = new ScreenshotIndex.Shot();
		shot.world = "My World";
		shot.biome = "Cherry Grove";
		shot.tags.add("Sunset");
		String text = shot.searchText();
		assertTrue(text.contains("my world"));
		assertTrue(text.contains("cherry grove"));
		assertTrue(text.contains("sunset"));
		assertFalse(text.contains("null"));
		assertEquals("", new ScreenshotIndex.Shot().searchText());
	}

	@Test
	void getCreatesAndPeekDoesNot() {
		ScreenshotIndex index = new ScreenshotIndex();
		assertNull(index.peek("a.png"));
		ScreenshotIndex.Shot shot = index.get("a.png");
		assertSame(shot, index.get("a.png"));
		assertSame(shot, index.peek("a.png"));
	}

	@Test
	void pruneForgetsFilesThatAreGone() {
		ScreenshotIndex index = new ScreenshotIndex();
		index.get("keep.png");
		index.get("gone.png");
		index.prune(Set.of("keep.png", "new.png"));
		assertEquals(Set.of("keep.png"), index.shots.keySet());
	}

	@Test
	void loadDropsNullShotsAndBlankTags() throws IOException {
		Path file = Files.writeString(dir.resolve("screenshots.json"),
			"{\"shots\": {\"a.png\": {\"tags\": [\"x\", null, \" \"]}, \"b.png\": null, \"c.png\": {\"tags\": null}}}");
		ScreenshotIndex index = ScreenshotIndex.load(file);
		assertEquals(Set.of("a.png", "c.png"), index.shots.keySet());
		assertEquals(List.of("x"), index.peek("a.png").tags);
		assertTrue(index.peek("c.png").tags.isEmpty());
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		Path file = dir.resolve("deep").resolve("screenshots.json");
		ScreenshotIndex index = new ScreenshotIndex();
		ScreenshotIndex.Shot shot = index.get("2026-11-03_10.00.00.png");
		shot.favourite = true;
		shot.x = -12;
		shot.y = 64;
		shot.z = 300;
		shot.dimension = "minecraft:the_nether";
		shot.taken = 123L;
		ScreenshotIndex.addTag(shot, "portal");
		index.save(file);
		ScreenshotIndex.Shot back = ScreenshotIndex.load(file).peek("2026-11-03_10.00.00.png");
		assertTrue(back.favourite);
		assertEquals(-12, back.x);
		assertEquals(64, back.y);
		assertEquals(300, back.z);
		assertEquals("minecraft:the_nether", back.dimension);
		assertEquals(123L, back.taken);
		assertEquals(List.of("portal"), back.tags);
		assertNull(back.world);
	}

	@Test
	void aBrokenFileStartsEmptyAndIsKeptAside() throws IOException {
		Path file = Files.writeString(dir.resolve("screenshots.json"), "{\"shots\": 5}");
		assertTrue(ScreenshotIndex.load(file).shots.isEmpty());
		assertTrue(Files.exists(dir.resolve("screenshots.json.broken")));
	}
}
