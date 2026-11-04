package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TipsTest {
	@Test
	void everyTipIsAShortSentence() {
		assertFalse(Tips.all().isEmpty());
		for (String tip : Tips.all()) {
			assertFalse(tip.isBlank());
			assertEquals(tip.strip(), tip);
			assertTrue(tip.endsWith("."), tip);
			assertTrue(tip.length() <= 120, tip);
		}
	}

	@Test
	void noTipIsRepeated() {
		assertEquals(Tips.all().size(), new HashSet<>(Tips.all()).size());
	}

	@Test
	void randomPicksFromTheList() {
		for (int i = 0; i < 50; i++) {
			assertTrue(Tips.all().contains(Tips.random()));
		}
	}

	@Test
	void theListCannotBeChanged() {
		List<String> all = Tips.all();
		assertThrows(UnsupportedOperationException.class, () -> all.add("x"));
	}
}
