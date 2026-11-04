package dev.justaclient.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.justaclient.common.storage.StorageIndex.Container;
import dev.justaclient.common.storage.StorageIndex.Hit;
import dev.justaclient.common.storage.StorageIndex.Item;
import dev.justaclient.common.storage.StorageIndex.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageIndexTest {
	private static Container chest(int x, List<Item> items) {
		return new Container("chest", "minecraft:overworld", x, 64, 0, "minecraft:chest", 27, items.size(), items, 1000L);
	}

	@Test
	void queryOperators() {
		Query gt = Query.parse(">5 iron");
		assertEquals("iron", gt.text());
		assertEquals(6, gt.min());
		assertEquals(Integer.MAX_VALUE, gt.max());

		Query lt = Query.parse("<5 torch");
		assertEquals(0, lt.min());
		assertEquals(4, lt.max());

		Query eq = Query.parse("=3");
		assertEquals(3, eq.min());
		assertEquals(3, eq.max());
		assertEquals("", eq.text());

		Query le = Query.parse("<=10");
		assertEquals(10, le.max());
		assertEquals(0, le.min());
	}

	@Test
	void bareNumberAndPlusMeanAtLeast() {
		Query bare = Query.parse("64 Oak LOG");
		assertEquals("oak log", bare.text());
		assertEquals(64, bare.min());
		assertEquals(Integer.MAX_VALUE, bare.max());
		Query plus = Query.parse("iron 32+");
		assertEquals("iron", plus.text());
		assertEquals(32, plus.min());
	}

	@Test
	void lessThanZeroMatchesNoRealStack() {
		Query q = Query.parse("<0");
		assertEquals(0, q.max());
		assertFalse(q.matchesAmount(1));
	}

	@Test
	void hugeNumbersDoNotOverflow() {
		Query huge = Query.parse("99999999999999999999");
		assertTrue(huge.min() > 0);
		assertFalse(huge.matchesAmount(1_000_000));
		Query gt = Query.parse(">2147483647");
		assertTrue(gt.min() > 0, "must not wrap negative");
		assertFalse(gt.matchesAmount(64));
	}

	@Test
	void nonAmountTokensAreWords() {
		Query q = Query.parse("  -5  >=  iron32 ");
		assertEquals("-5 >= iron32", q.text());
		assertEquals(0, q.min());
		assertTrue(Query.parse("   ").isEmpty());
		assertFalse(Query.parse("1").isEmpty());
	}

	@Test
	void namesMatchAllWordsInNameOrId() {
		Query q = Query.parse("oak log");
		assertTrue(q.matchesName("minecraft:oak_log", "Oak Log"));
		assertTrue(q.matchesName("minecraft:oak_log", "Firewood"));
		assertFalse(q.matchesName("minecraft:oak_planks", "Oak Planks"));
		assertTrue(Query.parse("").matchesName("x", "y"));
	}

	@Test
	void searchCountsShulkerContentsAndFiltersOnTheTotal() {
		Item box = new Item("minecraft:shulker_box", 1, "Shulker Box", List.of(new Item("minecraft:diamond", 10, "Diamond")));
		StorageIndex index = new StorageIndex();
		index.put(chest(0, List.of(box, new Item("minecraft:diamond", 5, "Diamond"))));
		index.put(chest(1, List.of(new Item("minecraft:diamond", 3, "Diamond"), new Item("minecraft:dirt", 64, "Dirt"))));

		List<Hit> all = index.search(Query.parse("diamond"));
		assertEquals(2, all.size());
		Hit first = all.get(0);
		assertEquals(15, first.count());
		assertEquals(10, first.inShulkers());
		assertEquals(3, all.get(1).count());
		assertEquals(0, all.get(1).inShulkers());

		List<Hit> many = index.search(Query.parse(">=12 diamond"));
		assertEquals(1, many.size());
		assertEquals(0, many.get(0).container().x);

		List<Hit> boxes = index.search(Query.parse("shulker"));
		assertEquals(1, boxes.size());
		assertEquals("minecraft:shulker_box", boxes.get(0).id());

		List<Map.Entry<String, Integer>> totals = StorageIndex.totals(index.search(Query.parse("")));
		assertEquals("minecraft:dirt", totals.get(0).getKey());
		assertEquals(64, totals.get(0).getValue());
		assertEquals(18, totals.get(1).getValue());
	}

	@Test
	void containerCountIncludesShulkerContents() {
		Item box = new Item("minecraft:shulker_box", 1, "Shulker Box", List.of(new Item("minecraft:diamond", 10, "Diamond")));
		Container c = chest(0, List.of(box, new Item("minecraft:diamond", 5, "Diamond")));
		assertEquals(15, c.count("minecraft:diamond"));
		assertEquals(1, c.count("minecraft:shulker_box"));
	}

	@Test
	void putReplacesTheSameSpot() {
		StorageIndex index = new StorageIndex();
		index.put(chest(0, List.of(new Item("minecraft:dirt", 1, "Dirt"))));
		index.put(chest(1, List.of()));
		index.put(chest(0, List.of(new Item("minecraft:stone", 2, "Stone"))));
		assertEquals(2, index.size());
		assertEquals("minecraft:stone", index.get("minecraft:overworld@0,64,0").items.get(0).id());
		assertTrue(index.remove("minecraft:overworld@1,64,0"));
		assertFalse(index.remove("minecraft:overworld@1,64,0"));
	}

	@Test
	void agoBoundaries() {
		long now = 10_000_000_000L;
		assertEquals("a while ago", StorageIndex.ago(0, now));
		assertEquals("just now", StorageIndex.ago(now + 50_000, now));
		assertEquals("just now", StorageIndex.ago(now - 59_999, now));
		assertEquals("1 minute ago", StorageIndex.ago(now - 60_000, now));
		assertEquals("59 minutes ago", StorageIndex.ago(now - 3_599_999, now));
		assertEquals("1 hour ago", StorageIndex.ago(now - 3_600_000, now));
		assertEquals("23 hours ago", StorageIndex.ago(now - 86_399_999, now));
		assertEquals("1 day ago", StorageIndex.ago(now - 86_400_000, now));
		assertEquals("2 days ago", StorageIndex.ago(now - 2 * 86_400_000L, now));
	}

	@Test
	void stacks() {
		assertEquals("40", StorageIndex.stacks(40, 64));
		assertEquals("0", StorageIndex.stacks(0, 64));
		assertEquals("1 stack", StorageIndex.stacks(64, 64));
		assertEquals("1 stack + 1", StorageIndex.stacks(65, 64));
		assertEquals("3 stacks + 12", StorageIndex.stacks(204, 64));
		assertEquals("2 stacks", StorageIndex.stacks(32, 16));
		assertEquals("5", StorageIndex.stacks(5, 1));
	}

	@Test
	void jsonRoundTrip() {
		StorageIndex index = new StorageIndex();
		Container c = chest(5, List.of(new Item("minecraft:shulker_box", 1, "Box", List.of(new Item("minecraft:apple", 3, "Apple")))));
		c.other = new int[] {6, 64, 0};
		index.put(c);
		index.put(new Container(StorageIndex.ENDER, "", 0, 0, 0, "minecraft:ender_chest", 27, 0, List.of(), 42L));

		StorageIndex back = StorageIndex.fromJson(JsonParser.parseString(index.toJson().toString()));
		assertEquals(2, back.size());
		Container b = back.get("minecraft:overworld@5,64,0");
		assertNotNull(b);
		assertArrayEquals(new int[] {6, 64, 0}, b.other);
		assertEquals(c.items, b.items);
		assertEquals(1000L, b.seen);
		assertEquals(27, b.slots);
		assertTrue(back.get(StorageIndex.ENDER).isEnder());
		assertEquals(42L, back.get(StorageIndex.ENDER).seen);
		assertEquals(index.toJson(), back.toJson());
	}

	@Test
	void malformedEntriesAreSkipped() {
		String json = """
			{"containers":[
			 5,
			 {"kind":"chest","dimension":"d","y":1,"z":1,"block":"b","slots":27},
			 {"kind":"chest","dimension":"d","x":1,"y":2,"z":3,"block":"b","slots":27,"other":[1,2],
			  "items":[{"id":"minecraft:a"},"junk",{"id":"minecraft:b","count":2}]},
			 {"kind":"chest","dimension":"d","x":9,"y":9,"z":9,"block":"b","slots":27,"other":[1,2,3,4]}
			]}""";
		StorageIndex index = StorageIndex.fromJson(JsonParser.parseString(json));
		assertEquals(2, index.size());
		Container c = index.get("d@1,2,3");
		assertNotNull(c);
		assertNull(c.other);
		assertEquals(0, c.used);
		assertEquals(0L, c.seen);
		assertEquals(List.of(new Item("minecraft:b", 2, "minecraft:b")), c.items);
		assertNull(index.get("d@9,9,9").other);
		assertFalse(index.toJson().getAsJsonArray("containers").get(0).getAsJsonObject().has("other"));

		assertEquals(0, StorageIndex.fromJson(null).size());
		assertEquals(0, StorageIndex.fromJson(JsonParser.parseString("{\"containers\":{}}")).size());
		assertEquals(0, StorageIndex.fromJson(JsonParser.parseString("[]")).size());
	}
}
