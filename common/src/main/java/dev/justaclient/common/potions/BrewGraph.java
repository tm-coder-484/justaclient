package dev.justaclient.common.potions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every brewing step the game knows, as plain ids, and the shortest way from a water bottle to each potion. A state is a
 * bottle item ({@code minecraft:potion}, {@code minecraft:splash_potion}, ...) holding a potion ({@code minecraft:swiftness}).
 * Built from the game's brewing rules by each version, so this class needs nothing from Minecraft.
 */
public final class BrewGraph {
	public static final String POTION = "minecraft:potion";
	public static final String SPLASH = "minecraft:splash_potion";
	public static final String LINGERING = "minecraft:lingering_potion";
	public static final State WATER = new State(POTION, "minecraft:water");

	public record State(String item, String potion) {
	}

	public record Mix(State from, String reagent, State to) {
	}

	/** One brew: put {@code reagent} in the top slot and the bottles become {@code result}. */
	public record Step(String reagent, State result) {
	}

	private final List<Mix> mixes;
	private final Map<State, List<Mix>> byFrom = new HashMap<>();
	private final Map<State, List<Step>> routes = new LinkedHashMap<>();

	public BrewGraph(Collection<Mix> all) {
		// A fixed order (by reagent, then result) keeps the chosen route the same every time.
		List<Mix> sorted = new ArrayList<>(new LinkedHashSet<>(all));
		sorted.sort(Comparator.comparing((Mix m) -> m.reagent).thenComparing(m -> m.to.item).thenComparing(m -> m.to.potion));
		this.mixes = List.copyOf(sorted);
		for (Mix mix : mixes) {
			byFrom.computeIfAbsent(mix.from, k -> new ArrayList<>()).add(mix);
		}
		search();
	}

	/**
	 * Fewest brews first; among routes of the same length, the one that turns the bottle into a splash or lingering one
	 * last, the way people brew (and the way that wastes the least gunpowder and dragon's breath).
	 */
	private void search() {
		record Entry(State state, long cost, long order) {
		}
		Map<State, Mix> parent = new HashMap<>();
		Map<State, Long> best = new HashMap<>();
		java.util.PriorityQueue<Entry> queue = new java.util.PriorityQueue<>(
			Comparator.comparingLong(Entry::cost).thenComparingLong(Entry::order));
		Set<State> done = new LinkedHashSet<>();
		long order = 0;
		best.put(WATER, 0L);
		queue.add(new Entry(WATER, 0L, order++));
		while (!queue.isEmpty()) {
			Entry e = queue.poll();
			if (!done.add(e.state)) {
				continue;
			}
			for (Mix mix : byFrom.getOrDefault(e.state, List.of())) {
				// Each brew costs 1000; a brew done in a splash or lingering bottle costs 1 more.
				long cost = e.cost + 1000 + (POTION.equals(mix.from.item) ? 0 : 1);
				Long known = best.get(mix.to);
				if (!done.contains(mix.to) && (known == null || cost < known)) {
					best.put(mix.to, cost);
					parent.put(mix.to, mix);
					queue.add(new Entry(mix.to, cost, order++));
				}
			}
		}
		for (State state : done) {
			List<Step> steps = new ArrayList<>();
			State current = state;
			Mix mix;
			while ((mix = parent.get(current)) != null) {
				steps.add(new Step(mix.reagent, mix.to));
				current = mix.from;
			}
			Collections.reverse(steps);
			routes.put(state, List.copyOf(steps));
		}
	}

	public List<Mix> mixes() {
		return mixes;
	}

	public boolean isEmpty() {
		return mixes.isEmpty();
	}

	/** Every bottle that can be brewed from water, in the order found (shortest first). Includes water itself. */
	public List<State> reachable() {
		return List.copyOf(routes.keySet());
	}

	/** The brews from a water bottle to {@code target}, or empty if it can't be brewed. Water itself has no steps. */
	public Optional<List<Step>> route(State target) {
		return Optional.ofNullable(routes.get(target));
	}

	/** What {@code reagent} turns {@code from} into, if anything. */
	public Optional<State> result(State from, String reagent) {
		for (Mix mix : byFrom.getOrDefault(from, List.of())) {
			if (mix.reagent.equals(reagent)) {
				return Optional.of(mix.to);
			}
		}
		return Optional.empty();
	}

	/** Every brew that starts from {@code from}. */
	public List<Mix> from(State from) {
		return byFrom.getOrDefault(from, List.of());
	}

	/**
	 * The next brew towards {@code target} for bottles that are {@code current} now, or empty if they are already there or
	 * not on the way. A bottle part way along the route continues from where it is.
	 */
	public Optional<Step> next(State current, State target) {
		List<Step> steps = routes.get(target);
		if (steps == null || current.equals(target)) {
			return Optional.empty();
		}
		if (current.equals(WATER)) {
			return steps.isEmpty() ? Optional.empty() : Optional.of(steps.getFirst());
		}
		for (int i = 0; i < steps.size() - 1; i++) {
			if (steps.get(i).result.equals(current)) {
				return Optional.of(steps.get(i + 1));
			}
		}
		// Off the shortest route: any route from here to the target, if one exists.
		return pathFrom(current, target).map(List::getFirst);
	}

	private Optional<List<Step>> pathFrom(State start, State target) {
		Map<State, Mix> parent = new HashMap<>();
		Deque<State> queue = new ArrayDeque<>();
		Set<State> seen = new LinkedHashSet<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			State state = queue.poll();
			if (state.equals(target)) {
				List<Step> steps = new ArrayList<>();
				State current = state;
				Mix mix;
				while ((mix = parent.get(current)) != null) {
					steps.add(new Step(mix.reagent, mix.to));
					current = mix.from;
				}
				Collections.reverse(steps);
				return steps.isEmpty() ? Optional.empty() : Optional.of(steps);
			}
			for (Mix mix : byFrom.getOrDefault(state, List.of())) {
				if (seen.add(mix.to)) {
					parent.put(mix.to, mix);
					queue.add(mix.to);
				}
			}
		}
		return Optional.empty();
	}

	/** Every reagent used anywhere, sorted. */
	public List<String> reagents() {
		return mixes.stream().map(Mix::reagent).distinct().sorted().toList();
	}

	/** The reagents a route needs, in order, with how many brews use each (a route can use one twice). */
	public static Map<String, Integer> shoppingList(List<Step> steps) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Step step : steps) {
			out.merge(step.reagent, 1, Integer::sum);
		}
		return out;
	}

	/**
	 * Reads one brewing recipe in the data format the game uses since brewing became data driven: {@code input} (item and
	 * potion), {@code reagent} (item) and {@code output} (id and potion contents). Returns empty for anything else, such
	 * as tag ingredients.
	 */
	public static Optional<Mix> parseRecipe(com.google.gson.JsonObject json) {
		try {
			if (!"minecraft:brewing".equals(string(json, "type"))) {
				return Optional.empty();
			}
			com.google.gson.JsonObject input = json.getAsJsonObject("input");
			com.google.gson.JsonObject output = json.getAsJsonObject("output");
			com.google.gson.JsonObject reagent = json.getAsJsonObject("reagent");
			String inItem = id(string(input, "item"));
			String inPotion = id(potion(input.get("potion_contents"), "potions"));
			String reagentItem = id(string(reagent, "item"));
			String outItem = id(string(output, "id"));
			com.google.gson.JsonObject components = output.getAsJsonObject("components");
			String outPotion = components == null ? null : id(potion(components.get("minecraft:potion_contents"), "potion"));
			if (inItem == null || inPotion == null || reagentItem == null || outItem == null || outPotion == null) {
				return Optional.empty();
			}
			return Optional.of(new Mix(new State(inItem, inPotion), reagentItem, new State(outItem, outPotion)));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static String potion(com.google.gson.JsonElement contents, String key) {
		if (contents == null) {
			return null;
		}
		if (contents.isJsonPrimitive()) {
			return contents.getAsString();
		}
		if (!contents.isJsonObject()) {
			return null;
		}
		com.google.gson.JsonElement value = contents.getAsJsonObject().get(key);
		if (value == null) {
			value = contents.getAsJsonObject().get(key.equals("potion") ? "potions" : "potion");
		}
		if (value != null && value.isJsonArray() && value.getAsJsonArray().size() == 1) {
			value = value.getAsJsonArray().get(0);
		}
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String string(com.google.gson.JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		com.google.gson.JsonElement e = o.get(key);
		if (e != null && e.isJsonArray() && e.getAsJsonArray().size() == 1) {
			e = e.getAsJsonArray().get(0);
		}
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** Adds the minecraft namespace to bare ids; tags (#...) are not single items, so they give null. */
	private static String id(String raw) {
		if (raw == null || raw.isBlank() || raw.startsWith("#")) {
			return null;
		}
		return raw.contains(":") ? raw : "minecraft:" + raw;
	}
}
