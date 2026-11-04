package dev.justaclient.inventory;

import dev.justaclient.common.potions.BrewGraph;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

/** Turns the plain ids the guides work with into item stacks and names, and back. */
public final class InvItems {
	private InvItems() {
	}

	public static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
	}

	public static ItemStack stack(String itemId) {
		return new ItemStack(item(itemId));
	}

	public static Optional<Holder.Reference<Potion>> potion(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Optional.empty() : BuiltInRegistries.POTION.get(key);
	}

	/** The bottle and potion in {@code stack}, or null if it is not a potion bottle. */
	public static BrewGraph.@Nullable State state(ItemStack stack) {
		Holder<Potion> potion = InvCompat.potionOf(stack);
		if (potion == null || potion.unwrapKey().isEmpty()) {
			return null;
		}
		return new BrewGraph.State(id(stack.getItem()), potion.unwrapKey().get().identifier().toString());
	}

	public static ItemStack stack(BrewGraph.State state) {
		Item bottle = item(state.item());
		return potion(state.potion()).map(p -> PotionContents.createItemStack(bottle, p)).orElseGet(() -> new ItemStack(bottle));
	}

	/** "Splash Potion of Swiftness II", "Potion of Night Vision (long)". The game gives long and strong ones the same name. */
	public static String name(BrewGraph.State state) {
		String base = stack(state).getHoverName().getString();
		String path = state.potion().substring(state.potion().indexOf(':') + 1);
		if (path.startsWith("strong_")) {
			return base + " II";
		}
		return path.startsWith("long_") ? base + " (long)" : base;
	}

	/** Just the potion part: "Swiftness II", "Night Vision (long)". */
	public static String shortName(BrewGraph.State state) {
		String name = name(new BrewGraph.State(BrewGraph.POTION, state.potion()));
		for (String prefix : new String[] {"Potion of ", "Uncraftable Potion"}) {
			if (name.startsWith(prefix)) {
				return name.substring(prefix.length()).isEmpty() ? name : name.substring(prefix.length());
			}
		}
		return name;
	}

	public static String itemName(String itemId) {
		return stack(itemId).getHoverName().getString();
	}

	/** Draws an item at {@code size} pixels (16 is the game's own size). */
	public static void draw(GuiGraphics g, ItemStack stack, float x, float y, float size) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 16f, size / 16f);
		g.renderItem(stack, 0, 0);
		g.pose().popMatrix();
	}
}
