package dev.justaclient.common.chat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.justaclient.common.chat.ChatText.Entry;
import dev.justaclient.common.chat.ChatText.Kind;
import dev.justaclient.common.chat.ChatText.Query;
import dev.justaclient.common.chat.ChatText.Tab;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatTextTest {
	private static void player(String line, String sender) {
		assertEquals(sender, ChatText.sender(line), line);
		assertEquals(Kind.PLAYER, ChatText.kind(line), line);
	}

	private static void whisper(String line, String sender) {
		assertEquals(sender, ChatText.sender(line), line);
		assertEquals(Kind.PRIVATE, ChatText.kind(line), line);
	}

	@Test
	void playerChatFormats() {
		player("<Bob> hi", "Bob");
		player("[VIP] Bob: hi", "Bob");
		player("[Guild] [MVP+] Steve » hi", "Steve");
		player("Bob » hi", "Bob");
		player("Bob >> hi", "Bob");
		player("Bob | hi", "Bob");
		player("Some_Name_16char: hi", "Some_Name_16char");
	}

	@Test
	void whispers() {
		whisper("Alex whispers to you: psst", "Alex");
		whisper("Alex whispers: psst", "Alex");
		whisper("[Alex -> me] psst", "Alex");
		whisper("[Alex → you] psst", "Alex");
		whisper("From Alex: psst", "Alex");
		whisper("(From Alex) psst", "Alex");
		whisper("Alex -> me psst", "Alex");
	}

	@Test
	void serverLines() {
		for (String line : List.of("Steve joined the game", "Steve has made the advancement [Stone Age]", "[Server] Restarting in 5 minutes",
			"<B> one letter is no name", "")) {
			assertEquals("", ChatText.sender(line), line);
			assertEquals(Kind.SYSTEM, ChatText.kind(line), line);
		}
	}

	@Test
	void serverLineShapedLikeChatIsReadAsChat() {
		// From text alone "Winner: Steve" looks like player "Winner" talking; the chat hook uses the game's signed flag and
		// GgGate only accepts such lines when they start with the trigger.
		assertEquals("Winner", ChatText.sender("Winner: Steve"));
		assertEquals(Kind.PLAYER, ChatText.kind("Winner: Steve"));
	}

	@Test
	void spansAreWholeWordsInOrderWithoutOverlap() {
		List<int[]> s = ChatText.spans("Diamonds? DIAMOND, diamond_ore and diamond", List.of("diamond"));
		assertEquals(2, s.size());
		assertArrayEquals(new int[] {10, 17}, s.get(0));
		assertArrayEquals(new int[] {35, 42}, s.get(1));

		List<int[]> o = ChatText.spans("buy ice cream now", List.of("cream", " ice cream ", "", "  ", "now"));
		assertEquals(2, o.size());
		assertArrayEquals(new int[] {8, 13}, o.get(0));
		assertArrayEquals(new int[] {14, 17}, o.get(1));
		assertTrue(ChatText.spans("aaa", List.of("aa")).isEmpty());
	}

	@Test
	void mentions() {
		assertTrue(ChatText.mentions("<Alex> hey Steve, come here", "Steve", List.of()));
		assertTrue(ChatText.mentions("<Alex> hey STEVE", "Steve", List.of()));
		assertTrue(ChatText.mentions("[VIP] Alex: selling diamonds", "Steve", List.of("diamonds")));
		assertTrue(ChatText.mentions("Alex whispers to you: Steve?", "Steve", List.of()));
		assertTrue(ChatText.mentions("Server restarting, steve", "Steve", List.of()));
		assertTrue(ChatText.mentions("<Alex> raid at spawn", "", List.of("raid")));
		assertFalse(ChatText.mentions("<Alex> Steven is here", "Steve", List.of()));
		assertFalse(ChatText.mentions("<Steve> I am Steve, buy diamonds", "Steve", List.of("diamonds")));
		assertFalse(ChatText.mentions("Steve has made the advancement [Stone Age]", "Steve", List.of()));
		assertFalse(ChatText.mentions("steve joined the game", "Steve", List.of()));
		assertFalse(ChatText.mentions("<Alex> hi", "", List.of()));
	}

	@Test
	void aboutSelf() {
		assertTrue(ChatText.aboutSelf("Steve left the game", "Steve"));
		assertFalse(ChatText.aboutSelf("Steven left the game", "Steve"));
		assertFalse(ChatText.aboutSelf("Steve", "Steve"));
		assertFalse(ChatText.aboutSelf("Steve left", ""));
	}

	@Test
	void queryParse() {
		Query q = ChatText.Query.parse("  from:BoB has:link  is:Private Hello  WORLD ");
		assertEquals(List.of("hello", "world"), q.words());
		assertEquals("BoB", q.from());
		assertTrue(q.link());
		assertEquals(Kind.PRIVATE, q.kind());
		assertFalse(q.mention());

		Query bare = ChatText.Query.parse("from: is:mention is:system is:player");
		assertEquals(List.of("from:"), bare.words());
		assertNull(bare.from());
		assertTrue(bare.mention());
		assertEquals(Kind.PLAYER, bare.kind(), "the last is: wins");
		assertTrue(ChatText.Query.parse("").words().isEmpty());
	}

	@Test
	void queryMatches() {
		Entry e = new Entry(0, "Bob whispers to you: see https://example.com now", "Bob", Kind.PRIVATE, true);
		assertTrue(Query.parse("from:bob has:link is:private is:mention SEE now").matches(e));
		assertFalse(Query.parse("from:alice").matches(e));
		assertFalse(Query.parse("is:player").matches(e));
		assertFalse(Query.parse("see later").matches(e));
		Entry plain = new Entry(0, "<Al> visit www.example.org", "Al", Kind.PLAYER, false);
		assertTrue(Query.parse("has:link").matches(plain));
		assertFalse(Query.parse("is:mention").matches(plain));
		assertFalse(Query.parse("has:link").matches(new Entry(0, "<Al> no links", "Al", Kind.PLAYER, false)));
	}

	@Test
	void tabParse() {
		Tab t = Tab.parse(" Trade : buy, sell, , wts ");
		assertEquals("Trade", t.name());
		assertEquals(List.of("buy", "sell", "wts"), t.words());
		assertNull(Tab.parse(":buy"));
		assertNull(Tab.parse("Trade"));
		assertNull(Tab.parse("Trade: , ,"));
		assertNull(Tab.parse("   : buy"));
		assertTrue(t.matches(new Entry(0, "<Al> WTS 64 iron", "Al", Kind.PLAYER, false)));
		assertFalse(t.matches(new Entry(0, "<Al> buying iron", "Al", Kind.PLAYER, false)));
	}

	@Test
	void copyFormats() {
		Entry e = new Entry(0, "Server restarting", "", Kind.SYSTEM, false);
		String time = ChatText.time(0, ChatText.TimeStyle.SHORT);
		assertEquals("Server restarting", ChatText.copy(e, ChatText.CopyFormat.PLAIN, ChatText.TimeStyle.SHORT));
		assertEquals("[" + time + "] Server restarting", ChatText.copy(e, ChatText.CopyFormat.WITH_TIME, ChatText.TimeStyle.SHORT));
		assertEquals("> Server restarting\n> — server, " + time, ChatText.copy(e, ChatText.CopyFormat.QUOTE, ChatText.TimeStyle.SHORT));
	}
}
