package dev.justaclient.common.kits;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * A saved inventory layout: which item goes in which slot. Slots use the player inventory's numbering: 0-8 hotbar, 9-35
 * the main inventory, 36-39 boots, leggings, chestplate, helmet, 40 the off hand. The full item (enchantments and all)
 * is kept as the game's own JSON so singleplayer can give it back exactly.
 */
public final class Kit {
	public static final int SLOTS = 41;
	public static final int MAX_NAME = 24;

	/** One slot of a kit. {@code stack} is the game's JSON for the item, or null if only the id is known. */
	public record Slot(int slot, String id, int count, String name, @Nullable JsonElement stack) {
	}

	/** How a slot you hold compares with the kit. */
	public enum State {
		/** The right item, at least as many. */
		RIGHT,
		/** The right item, fewer than the kit has. */
		SHORT,
		/** Nothing there yet. */
		EMPTY,
		/** Something else there. */
		WRONG
	}

	public String name;
	public long created;
	public List<Slot> slots;

	public Kit(String name, long created, List<Slot> slots) {
		this.name = name;
		this.created = created;
		this.slots = new ArrayList<>(slots);
	}

	public @Nullable Slot at(int slot) {
		for (Slot s : slots) {
			if (s.slot == slot) {
				return s;
			}
		}
		return null;
	}

	/** Drops a slot from the kit (the editor's "leave this one free"). */
	public void clear(int slot) {
		slots.removeIf(s -> s.slot == slot);
	}

	/** Compares one slot you hold ({@code haveId} empty for nothing) with what the kit wants there. */
	public static State check(Slot want, String haveId, int haveCount) {
		if (haveId.isEmpty() || haveCount <= 0) {
			return State.EMPTY;
		}
		if (!haveId.equals(want.id)) {
			return State.WRONG;
		}
		return haveCount >= want.count ? State.RIGHT : State.SHORT;
	}

	/** A name safe to show and store: trimmed, at most {@link #MAX_NAME} characters, or null if nothing is left. */
	public static @Nullable String cleanName(String raw) {
		String n = raw.replaceAll("[\\p{Cntrl}]", "").trim();
		if (n.length() > MAX_NAME) {
			n = n.substring(0, MAX_NAME).trim();
		}
		return n.isEmpty() ? null : n;
	}

	public static @Nullable Kit find(List<Kit> kits, String name) {
		for (Kit k : kits) {
			if (k.name.equalsIgnoreCase(name.trim())) {
				return k;
			}
		}
		return null;
	}

	public static JsonObject toJson(List<Kit> kits) {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray list = new JsonArray();
		for (Kit k : kits) {
			JsonObject o = new JsonObject();
			o.addProperty("name", k.name);
			o.addProperty("created", k.created);
			JsonArray slots = new JsonArray();
			for (Slot s : k.slots) {
				JsonObject so = new JsonObject();
				so.addProperty("slot", s.slot);
				so.addProperty("id", s.id);
				so.addProperty("count", s.count);
				so.addProperty("name", s.name);
				if (s.stack != null) {
					so.add("stack", s.stack);
				}
				slots.add(so);
			}
			o.add("slots", slots);
			list.add(o);
		}
		root.add("kits", list);
		return root;
	}

	/** Reads what {@link #toJson} wrote; bad kits or slots are skipped, and a name used twice keeps the first kit. */
	public static List<Kit> fromJson(@Nullable JsonElement json) {
		List<Kit> out = new ArrayList<>();
		if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("kits") || !json.getAsJsonObject().get("kits").isJsonArray()) {
			return out;
		}
		for (JsonElement e : json.getAsJsonObject().getAsJsonArray("kits")) {
			try {
				JsonObject o = e.getAsJsonObject();
				String name = cleanName(o.get("name").getAsString());
				if (name == null || find(out, name) != null) {
					continue;
				}
				List<Slot> slots = new ArrayList<>();
				for (JsonElement se : o.getAsJsonArray("slots")) {
					try {
						JsonObject so = se.getAsJsonObject();
						int slot = so.get("slot").getAsInt();
						if (slot < 0 || slot >= SLOTS || slots.stream().anyMatch(s -> s.slot == slot)) {
							continue;
						}
						String id = so.get("id").getAsString().toLowerCase(Locale.ROOT);
						slots.add(new Slot(slot, id, Math.max(1, so.get("count").getAsInt()), so.has("name") ? so.get("name").getAsString() : id,
							so.has("stack") ? so.get("stack") : null));
					} catch (RuntimeException skipped) {
						// One bad slot does not lose the kit.
					}
				}
				out.add(new Kit(name, o.has("created") ? o.get("created").getAsLong() : 0L, slots));
			} catch (RuntimeException skipped) {
				// One bad kit does not lose the rest.
			}
		}
		return out;
	}
}
