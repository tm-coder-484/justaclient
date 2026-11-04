package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextBufferTest {
	private static List<String> wrapped(TextBuffer b, double width) {
		return b.wrap(width, String::length).stream().map(TextBuffer.Line::text).toList();
	}

	@Test
	void initialTextIsCutToTheLimitWithTheCaretAtTheEnd() {
		TextBuffer b = new TextBuffer("abcdef", 4);
		assertEquals("abcd", b.get());
		assertEquals(4, b.caret());
	}

	@Test
	void insertGoesAtTheCaretAndRefusesToPassTheLimit() {
		TextBuffer b = new TextBuffer("ac", 4);
		b.setCaret(1);
		assertTrue(b.insert("b"));
		assertEquals("abc", b.get());
		assertEquals(2, b.caret());
		assertFalse(b.insert("xy"));
		assertEquals("abc", b.get());
		b.setCaret(-5);
		assertEquals(0, b.caret());
		b.setCaret(99);
		assertEquals(3, b.caret());
	}

	@Test
	void backspaceAndDeleteRemoveWholeCodePoints() {
		String emoji = "😀";
		TextBuffer b = new TextBuffer("a" + emoji + "b", 10);
		b.setCaret(3);
		b.backspace(false);
		assertEquals("ab", b.get());
		assertEquals(1, b.caret());
		b.set(emoji + "x");
		b.setCaret(0);
		b.delete();
		assertEquals("x", b.get());
		b.setCaret(0);
		b.backspace(false);
		assertEquals("x", b.get());
	}

	@Test
	void wordBackspaceRemovesTheWordAndTheSpacesBeforeTheCaret() {
		TextBuffer b = new TextBuffer("hello big  world", 100);
		b.backspace(true);
		assertEquals("hello big  ", b.get());
		b.backspace(true);
		assertEquals("hello ", b.get());
	}

	@Test
	void wordMovesSkipSpacesThenAWord() {
		TextBuffer b = new TextBuffer("one two  three", 100);
		b.left(true);
		assertEquals(9, b.caret());
		b.left(true);
		assertEquals(4, b.caret());
		b.right(true);
		assertEquals(7, b.caret());
		b.right(true);
		assertEquals(14, b.caret());
		b.right(true);
		assertEquals(14, b.caret());
		b.setCaret(0);
		b.left(true);
		assertEquals(0, b.caret());
	}

	@Test
	void homeAndEndStayOnTheCaretsLine() {
		TextBuffer b = new TextBuffer("first\nsecond\nthird", 100);
		b.setCaret(9);
		b.home();
		assertEquals(6, b.caret());
		b.end();
		assertEquals(12, b.caret());
		b.setCaret(2);
		b.home();
		assertEquals(0, b.caret());
		b.setCaret(15);
		b.end();
		assertEquals(18, b.caret());
	}

	@Test
	void wrapBreaksAfterSpacesAndKeepsStartOffsets() {
		TextBuffer b = new TextBuffer("aaa bbb ccc", 100);
		List<TextBuffer.Line> lines = b.wrap(5, String::length);
		assertEquals(List.of("aaa ", "bbb ", "ccc"), lines.stream().map(TextBuffer.Line::text).toList());
		assertEquals(List.of(0, 4, 8), lines.stream().map(TextBuffer.Line::start).toList());
	}

	@Test
	void wrapCutsLongWordsAndKeepsEmptyLines() {
		assertEquals(List.of("abc", "def", "gh"), wrapped(new TextBuffer("abcdefgh", 100), 3));
		List<TextBuffer.Line> lines = new TextBuffer("a\n\nb", 100).wrap(10, String::length);
		assertEquals(List.of(new TextBuffer.Line(0, "a"), new TextBuffer.Line(2, ""), new TextBuffer.Line(3, "b")), lines);
		assertEquals(List.of(new TextBuffer.Line(0, "")), new TextBuffer("", 10).wrap(10, String::length));
	}

	@Test
	void wrapAlwaysMakesProgressWhenNothingFits() {
		assertEquals(List.of("a", "b"), wrapped(new TextBuffer("ab", 100), 0));
	}
}
