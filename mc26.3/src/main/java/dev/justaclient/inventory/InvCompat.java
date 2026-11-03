package dev.justaclient.inventory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.common.potions.BrewGraph;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * The few container and brewing calls that differ between game versions. This is the 26.3 copy: brewing is data recipes
 * here and the client is not sent them, so the guide reads the game's own recipe files.
 */
public final class InvCompat {
	public interface Painter {
		void paint(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);
	}

	private static BrewGraph graph;

	private InvCompat() {
	}

	/** Runs {@code painter} after the screen has drawn everything, every frame it is open. */
	public static void afterDraw(Screen screen, Painter painter) {
		ScreenEvents.afterExtract(screen).register(painter::paint);
	}

	/**
	 * Ticks a furnace burns {@code stack} for, 0 if it is not fuel, -1 if unknown. Fuel times are data here: most point at
	 * a named value such as {@code cooking/time_coal}, "1600 divided by 2 in a blast furnace or smoker ({@code fast})".
	 */
	public static int burnTicks(ItemStack stack, boolean fast) {
		CookingFuel fuel = stack.isEmpty() ? null : stack.get(DataComponents.COOKING_FUEL);
		if (fuel == null) {
			return 0;
		}
		return switch (fuel.burnTime()) {
			case ResolvableInt.Constant constant -> constant.value();
			case ResolvableInt.Reference reference -> {
				Integer base = fuelTimes().get(reference.key().identifier().toString());
				yield base == null ? -1 : fast ? base / 2 : base;
			}
		};
	}

	private static java.util.Map<String, Integer> fuelTimes;

	/** Base burn times by name, read once from the game's {@code context_int_provider/cooking} files. */
	private static java.util.Map<String, Integer> fuelTimes() {
		if (fuelTimes != null) {
			return fuelTimes;
		}
		java.util.Map<String, Integer> out = new java.util.HashMap<>();
		try (net.minecraft.server.packs.PackResources pack = ServerPacksSource.createVanillaPackSource().fullResources()) {
			pack.listResources(PackType.SERVER_DATA, "minecraft", "context_int_provider/cooking", (id, supplier) -> {
				try (InputStream in = supplier.get()) {
					com.google.gson.JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
					// "context_int_provider/cooking/time_coal.json" is the value named "minecraft:cooking/time_coal".
					String path = id.getPath();
					String name = id.getNamespace() + ":" + path.substring("context_int_provider/".length(), path.length() - ".json".length());
					if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
						out.put(name, json.getAsInt());
					} else if (json.isJsonObject() && json.getAsJsonObject().has("left")
						&& json.getAsJsonObject().get("left").isJsonPrimitive()) {
						out.put(name, json.getAsJsonObject().get("left").getAsInt());
					}
				} catch (Exception e) {
					Justaclient.LOGGER.debug("Skipped fuel time {}", id, e);
				}
			});
		} catch (Exception e) {
			Justaclient.LOGGER.warn("Could not read the fuel times", e);
		}
		fuelTimes = out;
		return out;
	}

	/** The items stored inside a shulker box (or any item holding a container), empty for other items. */
	public static List<ItemStack> contents(ItemStack stack) {
		var c = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
		return c == null ? List.of() : c.nonEmptyItemCopyStream().toList();
	}

	/** The game's brewing recipes, read once from its own data. */
	public static BrewGraph brewing() {
		if (graph == null) {
			graph = build();
		}
		return graph;
	}

	private static BrewGraph build() {
		List<BrewGraph.Mix> mixes = new ArrayList<>();
		try (net.minecraft.server.packs.PackResources pack = ServerPacksSource.createVanillaPackSource().fullResources()) {
			pack.listResources(PackType.SERVER_DATA, "minecraft", "recipe/brewing", (id, supplier) -> {
				try (InputStream in = supplier.get()) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					BrewGraph.parseRecipe(json).ifPresent(mixes::add);
				} catch (Exception e) {
					Justaclient.LOGGER.debug("Skipped brewing recipe {}", id, e);
				}
			});
		} catch (Exception e) {
			Justaclient.LOGGER.warn("Could not read the brewing recipes", e);
		}
		Justaclient.LOGGER.info("Potion guide: {} brewing recipes", mixes.size());
		return new BrewGraph(mixes);
	}

	static Holder<Potion> potionOf(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? null : contents.potion().orElse(null);
	}
}
