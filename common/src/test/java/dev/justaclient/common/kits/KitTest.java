package dev.justaclient.common.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.justaclient.common.kits.Kit.Slot;
import dev.justaclient.common.kits.Kit.State;
import java.util.List;
import org.junit.jupiter.api.Test;

class KitTest {
	private static final Slot SWORD = new Slot(0, "minecraft:diamond_sword", 1, "Sword", null);
	private static final Slot PEARLS = new Slot(1, "minecraft:ender_pearl", 16, "Pearls", null);

	@Test
	void checkComparesItemAndCount() {
		assertEquals(State.RIGHT, Kit.check(SWORD, "minecraft:diamond_sword", 1));
		assertEquals(State.RIGHT, Kit.check(PEARLS, "minecraft:ender_pearl", 20));
		assertEquals(State.SHORT, Kit.check(PEARLS, "minecraft:ender_pearl", 15));
		assertEquals(State.WRONG, Kit.check(PEARLS, "minecraft:snowball", 16));
		assertEquals(State.EMPTY, Kit.check(PEARLS, "", 16));
		assertEquals(State.EMPTY, Kit.check(PEARLS, "minecraft:ender_pearl", 0));
	}

	@Test
	void cleanNameTrimsAndDropsControlCharacters() {
		assertEquals("PvP", Kit.cleanName("  P\u0007v\nP\t "));
		assertNull(Kit.cleanName(" \n\t "));
		assertNull(Kit.cleanName(""));
	}

	@Test
	void cleanNameCutsToMaxLength() {
		String cut = Kit.cleanName("abcdefghijklmnopqrstuvwxyz0123");
		assertEquals(Kit.MAX_NAME, cut.length());
		assertEquals("abcdefghijklmnopqrstuvwx", cut);
		// A cut that ends on a space leaves no trailing space.
		assertEquals("a".repeat(23), Kit.cleanName("a".repeat(23) + " bbbb"));
	}

	@Test
	void findIgnoresCaseAndSpaces() {
		Kit a = new Kit("PvP", 0, List.of());
		assertSame(a, Kit.find(List.of(a), "  pvp "));
		assertNull(Kit.find(List.of(a), "pv"));
	}

	@Test
	void clearAndAt() {
		Kit k = new Kit("k", 0, List.of(SWORD, PEARLS));
		assertSame(PEARLS, k.at(1));
		k.clear(1);
		assertNull(k.at(1));
		assertEquals(1, k.slots.size());
	}

	@Test
	void jsonRoundTripKeepsStacks() {
		Slot enchanted = new Slot(40, "minecraft:totem_of_undying", 1, "Totem",
			JsonParser.parseString("{\"id\":\"minecraft:totem_of_undying\",\"count\":1}"));
		List<Kit> kits = List.of(new Kit("PvP", 123L, List.of(SWORD, PEARLS, enchanted)), new Kit("Build", 7L, List.of()));
		List<Kit> back = Kit.fromJson(JsonParser.parseString(Kit.toJson(kits).toString()));
		assertEquals(2, back.size());
		assertEquals("PvP", back.get(0).name);
		assertEquals(123L, back.get(0).created);
		assertEquals(List.of(SWORD, PEARLS, enchanted), back.get(0).slots);
		assertEquals(Kit.toJson(kits), Kit.toJson(back));
	}

	@Test
	void fromJsonKeepsTheFirstOfADuplicateName() {
		String json = "{\"kits\":[{\"name\":\"PvP\",\"created\":1,\"slots\":[]},{\"name\":\" pvp \",\"created\":2,\"slots\":[]},"
			+ "{\"name\":\"\\u0001\",\"slots\":[]},{\"name\":\"NoSlots\"},{\"created\":3,\"slots\":[]}]}";
		List<Kit> kits = Kit.fromJson(JsonParser.parseString(json));
		assertEquals(1, kits.size());
		assertEquals(1L, kits.get(0).created);
	}

	@Test
	void fromJsonSkipsBadSlots() {
		String json = """
			{"kits":[{"name":"K","slots":[
			 {"slot":-1,"id":"a","count":1},
			 {"slot":41,"id":"b","count":1},
			 {"slot":40,"id":"MINECRAFT:SHIELD","count":0},
			 {"slot":40,"id":"minecraft:totem","count":1},
			 {"slot":3,"count":1},
			 "junk",
			 {"slot":0,"id":"minecraft:bow","count":-5,"name":"Bow"}
			]}]}""";
		List<Kit> kits = Kit.fromJson(JsonParser.parseString(json));
		assertEquals(1, kits.size());
		Kit k = kits.get(0);
		assertEquals(0L, k.created);
		assertEquals(List.of(new Slot(40, "minecraft:shield", 1, "minecraft:shield", null), new Slot(0, "minecraft:bow", 1, "Bow", null)),
			k.slots);
	}

	@Test
	void fromJsonOfNothing() {
		assertTrue(Kit.fromJson(null).isEmpty());
		assertTrue(Kit.fromJson(JsonParser.parseString("{\"kits\":5}")).isEmpty());
		assertTrue(Kit.fromJson(JsonParser.parseString("\"x\"")).isEmpty());
	}
}
