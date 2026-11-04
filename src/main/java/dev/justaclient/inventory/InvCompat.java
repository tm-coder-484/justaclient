package dev.justaclient.inventory;

import dev.justaclient.common.potions.BrewGraph;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;

/** The few container and brewing calls that differ between game versions. This is the 1.21.11 copy. */
public final class InvCompat {
	public interface Painter {
		void paint(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta);
	}

	private static PotionBrewing builtFrom;
	private static BrewGraph graph = new BrewGraph(List.of());

	private InvCompat() {
	}

	/** True if a screen mouse event's button number is the left button (it counts from 0 on this version). */
	public static boolean isLeft(int button) {
		return button == 0;
	}

	/** One shift-click on a menu slot, sent the way the game sends yours. */
	public static void quickMove(net.minecraft.world.inventory.AbstractContainerMenu menu, int slot) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.gameMode != null && mc.player != null) {
			mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
		}
	}

	/** Runs {@code painter} after the screen has drawn everything, every frame it is open. */
	public static void afterDraw(Screen screen, Painter painter) {
		ScreenEvents.afterRender(screen).register(painter::paint);
	}

	/**
	 * Ticks a furnace burns {@code stack} for, 0 if it is not fuel, -1 if unknown. Blast furnaces and smokers ({@code fast})
	 * burn fuel twice as fast.
	 */
	public static int burnTicks(ItemStack stack, boolean fast) {
		Minecraft mc = Minecraft.getInstance();
		int ticks = mc.level == null || stack.isEmpty() ? 0 : mc.level.fuelValues().burnDuration(stack);
		return fast ? ticks / 2 : ticks;
	}

	/** The items stored inside a shulker box (or any item holding a container), empty for other items. */
	public static List<ItemStack> contents(ItemStack stack) {
		var c = stack.get(DataComponents.CONTAINER);
		return c == null ? List.of() : c.nonEmptyStream().toList();
	}

	/** The brewing rules of the world you are in (the server's, including data packs), rebuilt when they change. */
	public static BrewGraph brewing() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return graph;
		}
		PotionBrewing brewing = mc.level.potionBrewing();
		if (brewing != builtFrom) {
			builtFrom = brewing;
			graph = build(brewing);
		}
		return graph;
	}

	private static BrewGraph build(PotionBrewing brewing) {
		List<ItemStack> ingredients = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = item.getDefaultInstance();
			if (brewing.isIngredient(stack)) {
				ingredients.add(stack);
			}
		}
		List<BrewGraph.Mix> mixes = new ArrayList<>();
		for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
			for (Holder<Potion> potion : BuiltInRegistries.POTION.asHolderIdMap()) {
				ItemStack from = PotionContents.createItemStack(bottle, potion);
				BrewGraph.State state = InvItems.state(from);
				if (state == null) {
					continue;
				}
				for (ItemStack ingredient : ingredients) {
					if (!brewing.hasMix(from, ingredient)) {
						continue;
					}
					BrewGraph.State to = InvItems.state(brewing.mix(ingredient, from.copy()));
					if (to != null && !to.equals(state)) {
						mixes.add(new BrewGraph.Mix(state, InvItems.id(ingredient.getItem()), to));
					}
				}
			}
		}
		return new BrewGraph(mixes);
	}

	static Holder<Potion> potionOf(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? null : contents.potion().orElse(null);
	}
}
