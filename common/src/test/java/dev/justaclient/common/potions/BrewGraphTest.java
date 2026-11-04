package dev.justaclient.common.potions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.common.potions.BrewGraph.Mix;
import dev.justaclient.common.potions.BrewGraph.State;
import dev.justaclient.common.potions.BrewGraph.Step;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrewGraphTest {
	private static final String WART = "minecraft:nether_wart";
	private static final String SUGAR = "minecraft:sugar";
	private static final String GUNPOWDER = "minecraft:gunpowder";
	private static final String REDSTONE = "minecraft:redstone";
	private static final State WATER = BrewGraph.WATER;
	private static final State AWKWARD = new State(BrewGraph.POTION, "minecraft:awkward");
	private static final State SWIFT = new State(BrewGraph.POTION, "minecraft:swiftness");
	private static final State LONG_SWIFT = new State(BrewGraph.POTION, "minecraft:long_swiftness");
	private static final State SPLASH_WATER = new State(BrewGraph.SPLASH, "minecraft:water");
	private static final State SPLASH_AWKWARD = new State(BrewGraph.SPLASH, "minecraft:awkward");
	private static final State SPLASH_SWIFT = new State(BrewGraph.SPLASH, "minecraft:swiftness");
	private static final State SPLASH_LONG_SWIFT = new State(BrewGraph.SPLASH, "minecraft:long_swiftness");
	private static final State LUCK = new State(BrewGraph.POTION, "minecraft:luck");
	private static final State ISLAND_A = new State(BrewGraph.POTION, "x:a");
	private static final State ISLAND_B = new State(BrewGraph.POTION, "x:b");

	private static BrewGraph graph() {
		return new BrewGraph(List.of(
			new Mix(WATER, GUNPOWDER, SPLASH_WATER),
			new Mix(SPLASH_WATER, WART, SPLASH_AWKWARD),
			new Mix(SPLASH_AWKWARD, SUGAR, SPLASH_SWIFT),
			new Mix(WATER, WART, AWKWARD),
			new Mix(AWKWARD, SUGAR, SWIFT),
			new Mix(AWKWARD, SUGAR, SWIFT),
			new Mix(SWIFT, GUNPOWDER, SPLASH_SWIFT),
			new Mix(SWIFT, REDSTONE, LONG_SWIFT),
			new Mix(LONG_SWIFT, GUNPOWDER, SPLASH_LONG_SWIFT),
			new Mix(ISLAND_A, SUGAR, ISLAND_B)));
	}

	private static List<String> reagents(Optional<List<Step>> route) {
		return route.orElseThrow().stream().map(Step::reagent).toList();
	}

	@Test
	void duplicatesAreDropped() {
		assertEquals(9, graph().mixes().size());
		assertEquals(List.of(GUNPOWDER, WART, REDSTONE, SUGAR), graph().reagents());
	}

	@Test
	void routesAreShortestWithTheBottleChangeLast() {
		BrewGraph g = graph();
		assertEquals(Optional.of(List.of()), g.route(WATER));
		assertEquals(List.of(WART, SUGAR), reagents(g.route(SWIFT)));
		assertEquals(List.of(WART, SUGAR, GUNPOWDER), reagents(g.route(SPLASH_SWIFT)));
		assertEquals(List.of(WART, SUGAR, REDSTONE, GUNPOWDER), reagents(g.route(SPLASH_LONG_SWIFT)));
		assertEquals(SPLASH_SWIFT, g.route(SPLASH_SWIFT).orElseThrow().getLast().result());
	}

	@Test
	void unreachableTargets() {
		BrewGraph g = graph();
		assertTrue(g.route(LUCK).isEmpty());
		assertTrue(g.route(ISLAND_B).isEmpty());
		assertTrue(g.next(WATER, LUCK).isEmpty());
		assertTrue(g.next(ISLAND_A, ISLAND_B).isEmpty());
		assertTrue(!g.reachable().contains(ISLAND_A) && !g.reachable().contains(ISLAND_B));
		assertEquals(WATER, g.reachable().getFirst());
	}

	@Test
	void nextFromAlongTheRoute() {
		BrewGraph g = graph();
		assertEquals(Optional.of(new Step(WART, AWKWARD)), g.next(WATER, SPLASH_SWIFT));
		assertEquals(Optional.of(new Step(SUGAR, SWIFT)), g.next(AWKWARD, SPLASH_SWIFT));
		assertEquals(Optional.of(new Step(GUNPOWDER, SPLASH_SWIFT)), g.next(SWIFT, SPLASH_SWIFT));
	}

	@Test
	void nextWhenAlreadyThereOrStuck() {
		BrewGraph g = graph();
		assertTrue(g.next(SPLASH_SWIFT, SPLASH_SWIFT).isEmpty());
		assertTrue(g.next(WATER, WATER).isEmpty());
		// Long swiftness can't be turned back into plain swiftness.
		assertTrue(g.next(LONG_SWIFT, SWIFT).isEmpty());
		assertTrue(g.next(SPLASH_SWIFT, SWIFT).isEmpty());
	}

	@Test
	void nextOffTheRouteFindsAnotherWay() {
		BrewGraph g = graph();
		// Already made splash water: carry on in splash bottles rather than give up.
		assertEquals(Optional.of(new Step(WART, SPLASH_AWKWARD)), g.next(SPLASH_WATER, SPLASH_SWIFT));
		assertEquals(Optional.of(new Step(SUGAR, SPLASH_SWIFT)), g.next(SPLASH_AWKWARD, SPLASH_SWIFT));
	}

	@Test
	void resultAndFrom() {
		BrewGraph g = graph();
		assertEquals(Optional.of(SWIFT), g.result(AWKWARD, SUGAR));
		assertTrue(g.result(AWKWARD, REDSTONE).isEmpty());
		assertEquals(2, g.from(WATER).size());
		assertTrue(g.from(LUCK).isEmpty());
		assertTrue(new BrewGraph(List.of()).isEmpty());
		assertEquals(List.of(WATER), new BrewGraph(List.of()).reachable());
	}

	@Test
	void shoppingListCountsRepeats() {
		List<Step> steps = List.of(new Step(WART, AWKWARD), new Step(SUGAR, SWIFT), new Step(WART, LUCK));
		assertEquals(Map.of(WART, 2, SUGAR, 1), BrewGraph.shoppingList(steps));
		assertEquals(List.of(WART, SUGAR), List.copyOf(BrewGraph.shoppingList(steps).keySet()));
	}

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void parseRecipeGameForm() {
		JsonObject recipe = json("{\"type\":\"minecraft:brewing\",\"input\":{\"item\":\"minecraft:potion\",\"potion_contents\":{\"potions\":"
			+ "\"minecraft:water\"}},\"output\":{\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:awkward\"}},"
			+ "\"id\":\"minecraft:potion\"},\"reagent\":{\"item\":\"minecraft:nether_wart\"}}");
		assertEquals(Optional.of(new Mix(WATER, WART, AWKWARD)), BrewGraph.parseRecipe(recipe));
	}

	@Test
	void parseRecipeLooseForms() {
		// Bare ids, one-element arrays, a plain string for the potion contents and swapped potion/potions keys.
		JsonObject recipe = json("{\"type\":\"minecraft:brewing\",\"input\":{\"item\":[\"potion\"],\"potion_contents\":\"water\"},"
			+ "\"output\":{\"components\":{\"minecraft:potion_contents\":{\"potions\":[\"awkward\"]}},\"id\":\"minecraft:potion\"},"
			+ "\"reagent\":{\"item\":[\"nether_wart\"]}}");
		assertEquals(Optional.of(new Mix(WATER, WART, AWKWARD)), BrewGraph.parseRecipe(recipe));
	}

	@Test
	void parseRecipeRejectsOthers() {
		String good = "{\"type\":\"minecraft:brewing\",\"input\":{\"item\":\"minecraft:potion\",\"potion_contents\":{\"potions\":"
			+ "\"minecraft:water\"}},\"output\":{\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:awkward\"}},"
			+ "\"id\":\"minecraft:potion\"},\"reagent\":{\"item\":\"REAGENT\"}}";
		assertTrue(BrewGraph.parseRecipe(json(good.replace("REAGENT", "#minecraft:warts"))).isEmpty());
		assertTrue(BrewGraph.parseRecipe(json(good.replace("REAGENT", " "))).isEmpty());
		assertTrue(BrewGraph.parseRecipe(json(good.replace("REAGENT", WART).replace("minecraft:brewing", "minecraft:crafting_shaped")))
			.isEmpty());
		assertTrue(BrewGraph.parseRecipe(json(good.replace("REAGENT", WART).replace("\"minecraft:water\"", "[\"a\",\"b\"]"))).isEmpty());
		assertTrue(BrewGraph.parseRecipe(json("{\"type\":\"minecraft:brewing\",\"input\":\"x\"}")).isEmpty());
		assertTrue(BrewGraph.parseRecipe(json("{\"type\":\"minecraft:brewing\",\"input\":{\"item\":\"minecraft:potion\",\"potion_contents\":"
			+ "\"water\"},\"output\":{\"id\":\"minecraft:potion\"},\"reagent\":{\"item\":\"sugar\"}}")).isEmpty());
	}
}
