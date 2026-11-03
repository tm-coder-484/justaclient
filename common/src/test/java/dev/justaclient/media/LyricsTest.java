package dev.justaclient.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Lyrics matching and LRC parsing; these run on game-free code paths only (no network). */
class LyricsTest {
	private static MediaState track(String title, String artist) {
		return new MediaState(MediaState.Status.PLAYING, title, artist, "", 0, 200_000, null, "test", 0);
	}

	@Test
	void searchTitleDropsVersionAndFeatureEndings() {
		assertEquals("Song", Lyrics.searchTitle("Song - Remastered 2011"));
		assertEquals("Song", Lyrics.searchTitle("Song (feat. Someone)"));
		assertEquals("Song", Lyrics.searchTitle("Song (Feat. Someone) - Live"));
		assertEquals("Song", Lyrics.searchTitle("Song [feat. X]"));
		assertEquals("Song (Live)", Lyrics.searchTitle("Song (Live)"));
		// A title that starts with the separator is left alone rather than emptied.
		assertEquals("- Intro", Lyrics.searchTitle("- Intro"));
	}

	@Test
	void simplifyKeepsLettersAndDigitsOnly() {
		assertEquals("song", Lyrics.simplify("Song - Remastered 2011"));
		assertEquals("dontstopmenow", Lyrics.simplify("Don't Stop Me Now (2011 Mix)"));
		assertEquals("东风破", Lyrics.simplify("东风破 (Live)"));
		assertEquals("abc", Lyrics.simplify("ABC feat. D"));
	}

	@Test
	void firstArtistOfSeveral() {
		assertEquals("Queen", Lyrics.firstArtist("Queen, David Bowie"));
		assertEquals("A", Lyrics.firstArtist("A; B"));
		assertEquals("A", Lyrics.firstArtist("A & B"));
		assertEquals("A", Lyrics.firstArtist("A Feat. B"));
		assertEquals("Solo", Lyrics.firstArtist("Solo"));
	}

	@Test
	void parseReadsStampsFractionsAndRepeats() {
		List<Lyrics.Line> lines = Lyrics.parse("[00:12.5]half\n[01:02.05]fifty\r\n[00:01.123][00:30]twice\nno stamp\n[ar:Someone]");
		assertEquals(List.of(1_123L, 12_500L, 30_000L, 62_050L), lines.stream().map(Lyrics.Line::timeMs).toList());
		assertEquals("twice", lines.get(0).text());
		assertEquals("twice", lines.get(2).text());
		assertTrue(Lyrics.parse("").isEmpty());
	}

	@Test
	void toAnswerDropsCreditsAndTheTitleLine() {
		String lrc = """
			[00:00.00]Song - Artist
			[00:01.00]作词 : Someone
			[00:02.00]Composer: Someone
			[00:31.00]first line
			[00:35.00]second line
			[00:40.00]third line
			[00:45.00]fourth line
			""";
		var answer = Lyrics.toAnswer("Test", lrc, track("Song - Remastered", "Artist, Other"));
		assertNotNull(answer);
		String synced = answer.get("syncedLyrics").getAsString();
		assertTrue(synced.startsWith("[00:31.00]first line"), synced);
		assertEquals(4, synced.lines().count());
		assertEquals("Test", answer.get("source").getAsString());
	}

	@Test
	void toAnswerRejectsPlaceholders() {
		assertNull(Lyrics.toAnswer("Test", "[00:01.00]only\n[00:02.00]three\n[00:03.00]lines", track("Song", "Artist")));
		assertNull(Lyrics.toAnswer("Test", "", track("Song", "Artist")));
	}
}
