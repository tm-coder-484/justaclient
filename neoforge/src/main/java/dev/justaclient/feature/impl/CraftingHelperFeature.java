package dev.justaclient.feature.impl;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.inventory.ContainerOverlays;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.inventory.StorageFinder;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * A panel beside the crafting table for the recipe you last picked in the recipe book: each ingredient with how many you
 * have against how many the number of crafts you set needs, and what is missing. Missing items are looked up in the
 * chests you opened (notification, chip and GPS key). "Fill the grid" is the recipe book's own action, one per click.
 * Off by default.
 */
public final class CraftingHelperFeature extends Feature {
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;
	private static final float W = 150;
	private static final float ROW = 12;
	private static final int MAX_CRAFTS = 64;

	private final BoolSetting findMissing = bool("find_missing", "Find missing items in your chests", true);

	/** One kind of ingredient: the items that fit (any planks, say) and how many one craft uses. */
	record Need(List<ItemStack> options, int perCraft) {
		boolean fits(ItemStack stack) {
			for (ItemStack o : options) {
				if (ItemStack.isSameItem(o, stack)) {
					return true;
				}
			}
			return false;
		}

		String name() {
			String first = options.getFirst().getHoverName().getString();
			return options.size() > 1 ? first + " (any)" : first;
		}
	}

	private @Nullable RecipeHolder<?> target;
	private int crafts = 1;
	private @Nullable Object offeredFor;
	/** The connection {@link #target} was picked on: recipe ids are numbered per connection, so they mean nothing on another. */
	private @Nullable Object targetFor;
	private @Nullable Object pendingKey;
	private long pendingSince;
	private float @Nullable [] minus;
	private float @Nullable [] plus;
	private float @Nullable [] fill;
	private float @Nullable [] clear;

	public CraftingHelperFeature() {
		super("crafting_helper", "Crafting helper", "What a recipe needs, what you're missing and which chest has it", Category.INVENTORY, Icons.GRID,
			false);
	}

	public static CraftingHelperFeature get() {
		return Features.get(CraftingHelperFeature.class);
	}

	public static void init() {
		ContainerOverlays.register(get().new Panel());
	}

	/** Called when a recipe is picked in the recipe book (and when "Fill the grid" is used). */
    public static void picked(RecipeHolder<?> recipe) {
        CraftingHelperFeature f=get();Minecraft mc=Minecraft.getInstance();
        if(!f.isEnabled() || mc.player==null) return;
        if(f.target!=null && f.target.id().equals(recipe.id()) && f.targetFor==mc.getConnection()) return;
        f.target=recipe;f.targetFor=mc.getConnection();f.crafts=1;f.offeredFor=null;
    }

	/** Forgets a recipe picked on another connection (another server, or after rejoining). */
	private void dropStale() {
		if (target != null && targetFor != Minecraft.getInstance().getConnection()) {
			target = null;
			targetFor = null;
		}
	}

    private static @Nullable RegistryAccess context() {
        var level=Minecraft.getInstance().level;return level==null?null:level.registryAccess();
    }
    static List<Need> needs(Recipe<?> recipe,RegistryAccess registry) {
        if(!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) return List.of();
        Map<List<String>,Need> grouped=new LinkedHashMap<>();
        for(Ingredient ingredient:recipe.getIngredients()) {
            List<ItemStack> options=java.util.Arrays.stream(ingredient.getItems()).filter(stack->!stack.isEmpty()).toList();
            if(options.isEmpty())continue;
            List<String> key=options.stream().map(stack->InvItems.id(stack.getItem())).sorted().toList();
            grouped.compute(key,(k,old)->new Need(options,old==null?1:old.perCraft+1));
        }
        return List.copyOf(grouped.values());
    }

	/** How many fitting items you hold, counting the crafting grid as well as your inventory. */
	private static int have(AbstractContainerMenu menu, Need need) {
		int n = 0;
		// Slot 0 is the result; the grid and your inventory follow.
		for (int i = 1; i < menu.slots.size(); i++) {
			ItemStack s = menu.getSlot(i).getItem();
			if (!s.isEmpty() && need.fits(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private void offerMissing(AbstractContainerMenu menu, List<Need> needs, String resultName) {
		if (!findMissing.on()) {
			return;
		}
		List<String> ids = new ArrayList<>();
		for (Need need : needs) {
			if (have(menu, need) < need.perCraft * crafts) {
				for (ItemStack o : need.options) {
					String id = InvItems.id(o.getItem());
					if (!ids.contains(id)) {
						ids.add(id);
					}
				}
			}
		}
		if (!ids.isEmpty()) {
			StorageFinder.offer(ids, "For " + (crafts > 1 ? crafts + "× " : "") + resultName);
		}
	}

	private static boolean in(float @Nullable [] r, double mx, double my) {
		return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	private final class Panel implements ContainerOverlays.Overlay {
		@Override
		public boolean applies(AbstractContainerScreen<?> screen) {
			return isEnabled() && screen instanceof CraftingScreen;
		}

		@Override
		public float width(ContainerOverlays.Box box) {
			return W;
		}

		@Override
		public float height(ContainerOverlays.Box box) {
			dropStale();
			RegistryAccess ctx = context();
			if (target == null || ctx == null) {
				return 18 + 24;
			}
			int rows = Math.max(1, needs(target.value(), ctx).size());
			return 18 + 16 + rows * ROW + 4 + 12 + 17 + 2;
		}

		@Override
		public void draw(ContainerOverlays.Box box, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			AbstractContainerMenu menu = box.screen().getMenu();
			float ly = ContainerOverlays.header(g, Icons.GRID, "Crafting helper", x, y, w);
			dropStale();
			RegistryAccess ctx = context();
			minus = plus = fill = clear = null;
			if (target == null || ctx == null) {
				JacText.draw(g, "Pick a recipe in the recipe book", x + 6, ly + 1, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				JacText.draw(g, "to see what you're missing.", x + 6, ly + 11, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				return;
			}
			JacText.icon(g, Icons.X, x + w - 13, y + 6, 7f, in(new float[] {x + w - 18, y, 18, 16}, mx, my) ? Theme.TEXT : Theme.TEXT_DIM);
			clear = new float[] {x + w - 18, y, 18, 16};

			ItemStack result = target.value().getResultItem(ctx);
			String resultName = result.isEmpty() ? "Recipe" : result.getHoverName().getString();
			InvItems.draw(g, result, x + 6, ly, 12);
			// The craft count: "- 3 +" on the right.
			String count = String.valueOf(crafts);
			float cw = JacText.width(count, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			float px = x + w - 6 - 10;
			float cx = px - 4 - cw;
			float mxp = cx - 4 - 10;
			minus = new float[] {mxp, ly, 10, 12};
			plus = new float[] {px, ly, 10, 12};
			Draw.rect(g, mxp, ly + 1, 10, 10, 3f, in(minus, mx, my) ? Theme.CONTROL_HOVER : Theme.CONTROL);
			Draw.rect(g, px, ly + 1, 10, 10, 3f, in(plus, mx, my) ? Theme.CONTROL_HOVER : Theme.CONTROL);
			JacText.draw(g, "-", mxp + 3.5f, ly + 2.5f, Theme.TEXT, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, "+", px + 2.5f, ly + 2.5f, Theme.TEXT, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, count, cx, ly + 2.5f, Theme.TEXT, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			int makes = Math.max(1, result.getCount()) * crafts;
			String title = resultName + (makes > 1 ? " ×" + makes : "");
			JacText.draw(g, JacScreen.fit(title, Theme.LABEL, JacText.Weight.SEMIBOLD, mxp - x - 26), x + 21, ly + 2.5f, Theme.TEXT, Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			ly += 16;

			List<Need> needs = needs(target.value(), ctx);
			int missing = 0;
			if (needs.isEmpty()) {
				JacText.draw(g, "Not a crafting table recipe", x + 6, ly + 2, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			// Items with choices (any planks) cycle once a second so you can see what fits.
			int tick = (int) (System.currentTimeMillis() / 1000);
			for (Need need : needs) {
				int want = need.perCraft * crafts;
				int got = have(menu, need);
				missing += Math.max(0, want - got);
				InvItems.draw(g, need.options.get(tick % need.options.size()), x + 6, ly, 10);
				String amount = Math.min(got, 9999) + "/" + want;
				float aw = JacText.width(amount, Theme.SMALL, JacText.Weight.SEMIBOLD);
				JacText.draw(g, amount, x + w - 6 - aw, ly + 2.5f, got >= want ? GREEN : AMBER, Theme.SMALL, JacText.Weight.SEMIBOLD);
				JacText.draw(g, JacScreen.fit(need.name(), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 30 - aw), x + 19, ly + 2, Theme.TEXT,
					Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			ly += 4;
			if (!needs.isEmpty()) {
				if (missing == 0) {
					JacText.draw(g, "You have everything", x + 6, ly, GREEN, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
				} else {
					JacText.draw(g, "Missing " + missing + (missing == 1 ? " item" : " items"), x + 6, ly, AMBER, Theme.SMALL + 0.5f,
						JacText.Weight.SEMIBOLD);
				}
			}
			ly += 12;
			fill = new float[] {x + 6, ly, w - 12, 13};
			Draw.rect(g, x + 6, ly, w - 12, 13, 4f, in(fill, mx, my) ? Theme.CONTROL_HOVER : Theme.CONTROL);
			String label = "Fill the grid";
			JacText.draw(g, label, x + w / 2f - JacText.width(label, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) / 2f, ly + 3.5f, Theme.TEXT,
				Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);

			// Look in your chests once per recipe, count and crafting table (after the server has sent the inventory).
			// Waits until the count has stayed put for a moment, so clicking + five times gives one notification.
			Object key = List.of(target.id(), crafts, box.screen());
			long now = System.currentTimeMillis();
			if (!Objects.equals(key, pendingKey)) {
				pendingKey = key;
				pendingSince = now;
			}
			if (!Objects.equals(key, offeredFor) && menu.getStateId() != 0 && now - pendingSince > 600) {
				offeredFor = key;
				offerMissing(menu, needs, resultName);
			}
		}

		@Override
		public boolean click(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, int button) {
			if (button != 0 || target == null) {
				return true;
			}
			if (in(clear, mx, my)) {
				GuiSoundsFeature.menuClick();
				target = null;
			} else if (in(minus, mx, my)) {
				GuiSoundsFeature.menuClick();
				crafts = Math.max(1, crafts - 1);
			} else if (in(plus, mx, my)) {
				GuiSoundsFeature.menuClick();
				crafts = Math.min(MAX_CRAFTS, crafts + 1);
			} else if (in(fill, mx, my)) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.gameMode != null) {
					GuiSoundsFeature.menuClick();
					// The recipe book's own action: one request, the server moves the items.
					mc.gameMode.handlePlaceRecipe(box.screen().getMenu().containerId, target, false);
				}
			}
			return true;
		}

		@Override
		public boolean scroll(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, double amount) {
			if (target != null && amount != 0) {
				crafts = Math.max(1, Math.min(MAX_CRAFTS, crafts + (amount > 0 ? 1 : -1)));
			}
			return true;
		}
	}
}
