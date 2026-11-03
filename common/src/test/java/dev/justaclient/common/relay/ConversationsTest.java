package dev.justaclient.common.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversationsTest {
	@TempDir
	Path dir;

	private static Conversations.Message msg(String id, boolean mine, long at) {
		return new Conversations.Message(id, mine, "t" + id, at, mine ? "delivered" : "");
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException {
		Conversations c = new Conversations();
		c.add("Bob", msg("1", false, 1));
		c.add("bob", msg("1", false, 1));
		c.add("Bob", msg("2", true, 2));
		Path file = dir.resolve("messages.json");
		c.save(file);
		Conversations back = new Conversations();
		back.load(file);
		assertEquals(2, back.with("BOB").size());
		assertEquals(1, back.unread("bob"));
	}

	@Test
	void aBrokenFileIsKeptAsideNotOverwritten() throws IOException {
		Path file = dir.resolve("messages.json");
		Files.writeString(file, "{\"byFriend\": {\"bob\": [ {\"id\":");
		Conversations c = new Conversations();
		c.load(file);
		assertTrue(c.with("bob").isEmpty());
		assertFalse(Files.exists(file));
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> broken = files.filter(f -> f.getFileName().toString().endsWith(".broken")).toList();
			assertEquals(1, broken.size());
			assertTrue(Files.readString(broken.getFirst()).startsWith("{\"byFriend\""));
		}
	}

	@Test
	void loadKeepingMergesWhatCameInWhileHistoryWasOff() throws IOException {
		Path file = dir.resolve("messages.json");
		Conversations old = new Conversations();
		old.add("Bob", msg("a", false, 10));
		old.add("Bob", msg("b", true, 20));
		old.save(file);
		Conversations live = new Conversations();
		live.add("bob", msg("c", false, 30));
		live.add("bob", msg("a", false, 10));
		live.add("Cara", msg("d", false, 5));
		live.loadKeeping(file);
		assertEquals(List.of("a", "b", "c"), live.with("bob").stream().map(m -> m.id).toList());
		assertEquals(1, live.with("cara").size());
		// "a" was unread in the file already; only "c" is new.
		assertEquals(2, live.unread("bob"));
		assertEquals(1, live.unread("cara"));
	}

	@Test
	void nullEntriesInTheFileAreSkipped() throws IOException {
		Path file = dir.resolve("messages.json");
		Files.writeString(file, "{\"byFriend\":{\"bob\":[null,{\"id\":\"1\",\"text\":\"hi\"},{\"id\":null}]},\"unread\":{\"bob\":null}}");
		Conversations c = new Conversations();
		c.load(file);
		assertEquals(1, c.with("bob").size());
		assertEquals(0, c.unread("bob"));
	}

	@Test
	void keepsTheNewestMessages() {
		Conversations c = new Conversations();
		for (int i = 0; i < Conversations.MAX + 20; i++) {
			c.add("Cara", msg("m" + i, true, i));
		}
		assertEquals(Conversations.MAX, c.with("cara").size());
		assertEquals("m20", c.with("cara").getFirst().id);
	}
}
