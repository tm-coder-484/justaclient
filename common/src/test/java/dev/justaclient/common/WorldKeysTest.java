package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldKeysTest {
	private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8",
		"com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	/** Whether Windows (and every other common file system) accepts {@code name} as one folder name. */
	private static void assertSafe(String name) {
		assertFalse(name.isEmpty(), "empty");
		for (char c : name.toCharArray()) {
			assertTrue(c >= 32 && ":*?\"<>|\\/".indexOf(c) < 0, () -> "bad character in " + name);
		}
		assertFalse(name.endsWith(".") || name.endsWith(" "), () -> "trailing dot or space: " + name);
		assertFalse(name.equals(".") || name.equals(".."), name);
		String base = name.toLowerCase(Locale.ROOT);
		int dot = base.indexOf('.');
		assertFalse(RESERVED.contains(dot < 0 ? base : base.substring(0, dot)), () -> "reserved device name: " + name);
		assertTrue(name.length() <= 120, () -> "too long: " + name);
	}

	@Test
	void serverAddressesBecomeSafeNames() {
		assertEquals("mp_play.example.com_25565", WorldKeys.folder("mp:play.example.com:25565"));
		for (String key : List.of("mp:play.example.com:25565", "mp:[::1]:25565", "mp:[2001:db8::1]", "mp:127.0.0.1", "mp:localhost")) {
			assertSafe(WorldKeys.folder(key));
		}
	}

	@Test
	void oddWorldNamesBecomeSafeNames() {
		for (String key : List.of("sp:New World", "sp:My \"best\" world?", "sp:a<b>c|d*e", "sp:back\\slash/forward", "sp:tab\tand\nnewline",
			"sp:Ünïcödé 世界", "sp:con", "sp:NUL", "unknown", "sp:")) {
			assertSafe(WorldKeys.folder(key));
		}
	}

	@Test
	void runsOfUnsafeCharactersCollapseToOneUnderscore() {
		assertEquals("sp_a_b", WorldKeys.folder("sp:a :: b"));
		assertEquals("sp_my_world", WorldKeys.folder("SP:My World"));
	}

	@Test
	void emptyKeysGetAFallbackName() {
		assertEquals("world", WorldKeys.folder(""));
	}

	@Test
	void longKeysAreShortenedButStayDistinct() {
		String a = WorldKeys.folder("sp:" + "x".repeat(200) + "a");
		String b = WorldKeys.folder("sp:" + "x".repeat(200) + "b");
		assertSafe(a);
		assertSafe(b);
		assertTrue(a.length() < 100);
		assertNotEquals(a, b);
	}

	@Test
	void theSameKeyAlwaysGivesTheSameFolder() {
		assertEquals(WorldKeys.folder("mp:Play.Example.com"), WorldKeys.folder("mp:Play.Example.com"));
	}
}
