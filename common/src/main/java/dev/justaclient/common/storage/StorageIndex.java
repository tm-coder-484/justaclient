package dev.justaclient.common.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What was inside the containers you opened in one world: chests, barrels, shulker boxes and the like, plus your ender
 * chest. Only what you saw yourself is kept, as it was when you last closed the container.
 */
public final class StorageIndex {
	/** One kind of item in a container. {@code inside} holds a shulker box's contents. */
	public record Item(String id, int count, String name, List<Item> inside) {
		public Item {
			inside = List.copyOf(inside);
		}

		public Item(String id, int count, String name) {
			this(id, count, name, List.of());
		}
	}

	/** A container at a block, or the ender chest ({@link #ENDER} kind, no position). */
	public static final class Container {
		public String kind;
		public String dimension;
		public int x;
		public int y;
		public int z;
		/** The other half of a double chest, or null. */
		public int @Nullable [] other;
		public String block;
		public int slots;
		public int used;
		public List<Item> items;
		public long seen;

		public Container(String kind, String dimension, int x, int y, int z, String block, int slots, int used, List<Item> items, long seen) {
			this.kind = kind;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.block = block;
			this.slots = slots;
			this.used = used;
			this.items = List.copyOf(items);
			this.seen = seen;
		}

		public String key() {
			return ENDER.equals(kind) ? ENDER : dimension + "@" + x + "," + y + "," + z;
		}

		public boolean isEnder() {
			return ENDER.equals(kind);
		}

		/** Every item, counting shulker box contents as well as the boxes themselves. */
		public int count(String id) {
			int n = 0;
			for (Item i : items) {
				if (i.id.equals(id)) {
					n += i.count;
				}
				for (Item in : i.inside) {
					if (in.id.equals(id)) {
						n += in.count;
					}
				}
			}
			return n;
		}
	}

	/** An item that matched a search, with where it is and how many there are. */
	public record Hit(Container container, String id, String name, int count, int inShulkers) {
	}

	/** A search: words that must appear in the item's name or id, and an optional amount. */
	public record Query(String text, int min, int max) {
		private static final Pattern AMOUNT = Pattern.compile("^(>=|<=|>|<|=)?(\\d+)(\\+)?$");

		/** "iron", "iron 32+", ">=10 diamond", "<5 torch", "64 oak log" (a bare number is "at least"). */
		public static Query parse(String raw) {
			int min = 0;
			int max = Integer.MAX_VALUE;
			List<String> words = new ArrayList<>();
			for (String token : raw.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
				if (token.isEmpty()) {
					continue;
				}
				Matcher m = AMOUNT.matcher(token);
				if (!m.matches()) {
					words.add(token);
					continue;
				}
				int n = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(m.group(2).length() > 9 ? "999999999" : m.group(2)));
				String op = m.group(1) == null ? ">=" : m.group(1);
				switch (op) {
					case ">" -> min = n + 1;
					case "<" -> max = Math.max(0, n - 1);
					case "<=" -> max = n;
					case "=" -> {
						min = n;
						max = n;
					}
					default -> min = n;
				}
			}
			return new Query(String.join(" ", words), min, max);
		}

		public boolean matchesName(String id, String name) {
			if (text.isEmpty()) {
				return true;
			}
			String hay = (name + " " + id.replace('_', ' ') + " " + id).toLowerCase(Locale.ROOT);
			for (String word : text.split(" ")) {
				if (!hay.contains(word)) {
					return false;
				}
			}
			return true;
		}

		public boolean matchesAmount(int n) {
			return n >= min && n <= max;
		}

		public boolean isEmpty() {
			return text.isEmpty() && min == 0 && max == Integer.MAX_VALUE;
		}
	}

	public static final String ENDER = "ender";
	private final Map<String, Container> containers = new LinkedHashMap<>();

	public void put(Container c) {
		containers.remove(c.key());
		containers.put(c.key(), c);
	}

	public @Nullable Container get(String key) {
		return containers.get(key);
	}

	public boolean remove(String key) {
		return containers.remove(key) != null;
	}

	public Collection<Container> all() {
		return containers.values();
	}

	public int size() {
		return containers.size();
	}

	/**
	 * Every item that matches, one hit per item kind per container (shulker contents counted in). The amount filter
	 * applies to each container's total of that item.
	 */
	public List<Hit> search(Query q) {
		List<Hit> hits = new ArrayList<>();
		for (Container c : containers.values()) {
			Map<String, int[]> counts = new LinkedHashMap<>();
			Map<String, String> names = new LinkedHashMap<>();
			for (Item i : c.items) {
				if (q.matchesName(i.id, i.name)) {
					counts.computeIfAbsent(i.id, k -> new int[2])[0] += i.count;
					names.putIfAbsent(i.id, i.name);
				}
				for (Item in : i.inside) {
					if (q.matchesName(in.id, in.name)) {
						int[] n = counts.computeIfAbsent(in.id, k -> new int[2]);
						n[0] += in.count;
						n[1] += in.count;
						names.putIfAbsent(in.id, in.name);
					}
				}
			}
			counts.forEach((id, n) -> {
				if (q.matchesAmount(n[0])) {
					hits.add(new Hit(c, id, names.get(id), n[0], n[1]));
				}
			});
		}
		return hits;
	}

	/** How many of each item there are across every container, most first. */
	public static List<Map.Entry<String, Integer>> totals(List<Hit> hits) {
		Map<String, Integer> sum = new LinkedHashMap<>();
		for (Hit h : hits) {
			sum.merge(h.id(), h.count(), Integer::sum);
		}
		List<Map.Entry<String, Integer>> out = new ArrayList<>(sum.entrySet());
		out.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
		return out;
	}

	/** "3 stacks + 12", "1 stack", "40" for an item that stacks to {@code stack}. */
	public static String stacks(int count, int stack) {
		if (stack <= 1 || count < stack) {
			return String.valueOf(count);
		}
		int full = count / stack;
		int rest = count % stack;
		return full + (full == 1 ? " stack" : " stacks") + (rest > 0 ? " + " + rest : "");
	}

	public JsonObject toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray list = new JsonArray();
		for (Container c : containers.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("kind", c.kind);
			o.addProperty("dimension", c.dimension);
			o.addProperty("x", c.x);
			o.addProperty("y", c.y);
			o.addProperty("z", c.z);
			if (c.other != null && c.other.length == 3) {
				JsonArray other = new JsonArray();
				for (int v : c.other) {
					other.add(v);
				}
				o.add("other", other);
			}
			o.addProperty("block", c.block);
			o.addProperty("slots", c.slots);
			o.addProperty("used", c.used);
			o.addProperty("seen", c.seen);
			o.add("items", items(c.items));
			list.add(o);
		}
		root.add("containers", list);
		return root;
	}

	private static JsonArray items(List<Item> items) {
		JsonArray out = new JsonArray();
		for (Item i : items) {
			JsonObject o = new JsonObject();
			o.addProperty("id", i.id);
			o.addProperty("count", i.count);
			o.addProperty("name", i.name);
			if (!i.inside.isEmpty()) {
				o.add("inside", items(i.inside));
			}
			out.add(o);
		}
		return out;
	}

	/** Reads what {@link #toJson} wrote. Entries that are not whole are skipped, never fatal. */
	public static StorageIndex fromJson(@Nullable JsonElement json) {
		StorageIndex index = new StorageIndex();
		if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("containers")
			|| !json.getAsJsonObject().get("containers").isJsonArray()) {
			return index;
		}
		for (JsonElement e : json.getAsJsonObject().getAsJsonArray("containers")) {
			try {
				JsonObject o = e.getAsJsonObject();
				Container c = new Container(o.get("kind").getAsString(), o.get("dimension").getAsString(), o.get("x").getAsInt(),
					o.get("y").getAsInt(), o.get("z").getAsInt(), o.get("block").getAsString(), o.get("slots").getAsInt(),
					o.has("used") ? o.get("used").getAsInt() : 0, readItems(o.get("items")), o.has("seen") ? o.get("seen").getAsLong() : 0L);
				if (o.has("other") && o.get("other").isJsonArray() && o.getAsJsonArray("other").size() == 3) {
					JsonArray a = o.getAsJsonArray("other");
					c.other = new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
				}
				index.put(c);
			} catch (RuntimeException skipped) {
				// One bad entry does not lose the rest.
			}
		}
		return index;
	}

	private static List<Item> readItems(@Nullable JsonElement json) {
		List<Item> out = new ArrayList<>();
		if (json == null || !json.isJsonArray()) {
			return out;
		}
		for (JsonElement e : json.getAsJsonArray()) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			if (!o.has("id") || !o.has("count")) {
				continue;
			}
			out.add(new Item(o.get("id").getAsString(), o.get("count").getAsInt(), o.has("name") ? o.get("name").getAsString() : o.get("id")
				.getAsString(), readItems(o.get("inside"))));
		}
		return out;
	}
}
