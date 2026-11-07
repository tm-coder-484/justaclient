package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.inventory.AnvilMath;
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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jspecify.annotations.Nullable;

/**
 * A panel beside the anvil listing what in your inventory can be combined: enchanted books that improve an item,
 * repairs with the item's material or a second copy, and two books that merge. Clicking a pair moves one item per click
 * (first the item, then the book or material), the same as a shift-click, so it never sends a sequence of actions. A
 * damaged item with no material on you asks storage memory which chest has some. Off by default.
 */
public final class AnvilHelperFeature extends Feature {
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;
	private static final float W = 152;
	private static final float ROW = 13;
	private static final int MAX_ROWS = 9;
	/** Menu slots: 0 the item, 1 the book or material, 2 the result, then your inventory. */
	private static final int LEFT = 0;
	private static final int RIGHT = 1;
	private static final int FIRST_INV = 3;

	private final BoolSetting books = bool("books", "Enchanted books onto items", true);
	private final BoolSetting repairs = bool("repairs", "Repairs (material or a second item)", true);
	private final BoolSetting mergeBooks = bool("merge_books", "Two books into one", true);
	private final BoolSetting ring = bool("ring", "Ring the pair you point at", true);
	private final BoolSetting findMaterials = bool("find_materials", "Find repair materials in your chests", true);

	enum Kind {
		REPAIR, BOOK, COMBINE, MERGE_BOOKS
	}

	/** Two menu slots that combine, what you get, and the levels their past anvil work adds. */
	record Pair(Kind kind, int left, int right, ItemStack leftStack, ItemStack rightStack, String what, int work) {
		boolean ready() {
			return left == LEFT && right == RIGHT;
		}
	}

	private List<Pair> pairs = List.of();
	private @Nullable AnvilMenu builtFor;
	private int builtState = -1;
	private long builtAt;
	private int hovered = -1;
	private final List<String> order = new ArrayList<>();
	private @Nullable Object offeredFor;

	public AnvilHelperFeature() {
		super("anvil_helper", "Anvil helper", "Lists what you can combine at an anvil; a click moves the next item in", Category.INVENTORY,
			Icons.HAMMER, false);
	}

	public static AnvilHelperFeature get() {
		return Features.get(AnvilHelperFeature.class);
	}

	public static void init() {
		ContainerOverlays.register(get().new Panel());
	}

	private List<Pair> pairs(AnvilMenu menu) {
		long now = System.currentTimeMillis();
		if (menu != builtFor || menu.getStateId() != builtState || now - builtAt > 250) {
			if (menu != builtFor) {
				order.clear();
			}
			builtFor = menu;
			builtState = menu.getStateId();
			builtAt = now;
			pairs = keepOrder(find(menu));
		}
		return pairs;
	}

	/** Which two items a pair is about, whatever slots they are in now. */
	private static String key(Pair p) {
		return p.kind + ":" + ItemStack.hashItemAndComponents(p.leftStack) + ":" + ItemStack.hashItemAndComponents(p.rightStack) + ":" + p.what;
	}

	/**
	 * Keeps each pair on the row it was on while the anvil stays open, so clicking the same row again carries on with the
	 * same pair after its first item has moved.
	 */
	private List<Pair> keepOrder(List<Pair> found) {
		if (!order.isEmpty()) {
			List<Pair> sorted = new ArrayList<>(found);
			sorted.sort(Comparator.comparingInt(p -> {
				int at = order.indexOf(key(p));
				return at < 0 ? Integer.MAX_VALUE : at;
			}));
			found = sorted;
		}
		order.clear();
		for (Pair p : found) {
			order.add(key(p));
		}
		return found;
	}

	/** Every useful pair, the ones already in the anvil first, then repairs, books and merges. */
	List<Pair> find(AnvilMenu menu) {
		List<Integer> lefts = new ArrayList<>();
		List<Integer> rights = new ArrayList<>();
		if (!menu.getSlot(LEFT).getItem().isEmpty()) {
			lefts.add(LEFT);
		}
		if (!menu.getSlot(RIGHT).getItem().isEmpty()) {
			rights.add(RIGHT);
		}
		for (int i = FIRST_INV; i < menu.slots.size(); i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) {
				lefts.add(i);
				rights.add(i);
			}
		}
		List<Pair> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int l : lefts) {
			ItemStack ls = menu.getSlot(l).getItem();
			for (int r : rights) {
				if (r == l) {
					continue;
				}
				ItemStack rs = menu.getSlot(r).getItem();
				Pair p = match(l, r, ls, rs);
				if (p == null) {
					continue;
				}
				// Two copies of the same book, or A+B and B+A, are listed once.
				boolean symmetric = p.kind == Kind.MERGE_BOOKS || p.kind == Kind.COMBINE;
				String key = symmetric && l >= FIRST_INV && r >= FIRST_INV ? Math.min(l, r) + ":" + Math.max(l, r) : l + ":" + p.what;
				if (seen.add(key)) {
					out.add(p);
				}
			}
		}
		out.sort(Comparator.<Pair>comparingInt(p -> p.left == LEFT || p.right == RIGHT ? 0 : 1).thenComparingInt(p -> p.kind.ordinal())
			.thenComparingInt(Pair::left));
		return out;
	}

	private @Nullable Pair match(int l, int r, ItemStack left, ItemStack right) {
		boolean leftBook = left.is(Items.ENCHANTED_BOOK);
		boolean rightBook = right.is(Items.ENCHANTED_BOOK);
		int work = AnvilMath.priorWork(left.getOrDefault(DataComponents.REPAIR_COST, 0), right.getOrDefault(DataComponents.REPAIR_COST, 0));
		ItemEnchantments stored = right.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (rightBook && leftBook) {
			if (!mergeBooks.on()) {
				return null;
			}
			List<String> gains = gains(left, stored, true);
			return gains.isEmpty() ? null : new Pair(Kind.MERGE_BOOKS, l, r, left, right, String.join(", ", gains), work);
		}
		if (leftBook) {
			return null;
		}
		if (rightBook) {
			if (!books.on()) {
				return null;
			}
			List<String> gains = gains(left, stored, false);
			return gains.isEmpty() ? null : new Pair(Kind.BOOK, l, r, left, right, String.join(", ", gains), work);
		}
		if (!repairs.on()) {
			return null;
		}
		if (left.isDamaged() && left.getItem().isValidRepairItem(left,right)) {
			return new Pair(Kind.REPAIR, l, r, left, right, "Repair with " + right.getHoverName().getString(), work);
		}
		if (left.isDamageableItem() && left.is(right.getItem()) && right.isDamageableItem()) {
			List<String> gains = gains(left, right.getEnchantments(), false);
			if (!left.isDamaged() && gains.isEmpty()) {
				return null;
			}
			String what = left.isDamaged() ? gains.isEmpty() ? "Repair" : "Repair, " + String.join(", ", gains) : String.join(", ", gains);
			return new Pair(Kind.COMBINE, l, r, left, right, what, work);
		}
		return null;
	}

	/** The enchantments {@code add} would raise on {@code target}, by their new names ("Sharpness V"). */
	static List<String> gains(ItemStack target, ItemEnchantments add, boolean targetIsBook) {
		ItemEnchantments have = targetIsBook ? target.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
			: target.getEnchantments();
		List<String> out = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : add.entrySet()) {
			Holder<Enchantment> e = entry.getKey();
			if (!targetIsBook && !e.value().canEnchant(target)) {
				continue;
			}
			boolean clash = false;
			for (Holder<Enchantment> h : have.keySet()) {
				if (!h.equals(e) && !Enchantment.areCompatible(h, e)) {
					clash = true;
					break;
				}
			}
			int current = have.getLevel(e);
			int max = e.value().getMaxLevel();
			if (!clash && AnvilMath.improves(current, entry.getIntValue(), max)) {
				out.add(Enchantment.getFullname(e, AnvilMath.merged(current, entry.getIntValue(), max)).getString());
			}
		}
		return out;
	}

	/**
	 * Damaged items with nothing on you to repair them: asks storage memory which chest holds their material, so the
	 * notification and the GPS key point there. Once per anvil opened; the item in the anvil goes first.
	 */
	private void offerRepairs(AnvilMenu menu, List<Pair> list) {
		if (!findMaterials.on() || !repairs.on()) {
			return;
		}
		List<String> materials = new ArrayList<>();
		String first = null;
		for (int i = 0; i < menu.slots.size(); i++) {
			if (i == RIGHT || i == 2) {
				continue;
			}
			ItemStack stack = menu.getSlot(i).getItem();
			int slot = i;
			boolean repairable = stack.isDamaged();
			if (!repairable || list.stream().anyMatch(p -> p.left == slot && (p.kind == Kind.REPAIR || p.kind == Kind.COMBINE))) {
				continue;
			}
			for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if(!stack.getItem().isValidRepairItem(stack,item.getDefaultInstance())) continue;
				String id = InvItems.id(item);
				if (!materials.contains(id)) {
					materials.add(id);
				}
			}
			if (first == null) {
				first = stack.getHoverName().getString();
			}
		}
		if (first != null) {
			StorageFinder.offer(materials, "To repair " + first);
		}
	}

	/** One shift-click towards the pair: clears a slot that holds something else, else moves the next item in. */
	private static void step(AnvilMenu menu, Pair p) {
		ItemStack inLeft = menu.getSlot(LEFT).getItem();
		ItemStack inRight = menu.getSlot(RIGHT).getItem();
		if (p.ready()) {
			return;
		}
		if (p.left == LEFT) {
			InvCompat.quickMove(menu, inRight.isEmpty() ? p.right : RIGHT);
			return;
		}
		if (!inLeft.isEmpty()) {
			InvCompat.quickMove(menu, LEFT);
		} else if (!inRight.isEmpty() && p.right != RIGHT) {
			InvCompat.quickMove(menu, RIGHT);
		} else {
			InvCompat.quickMove(menu, p.left);
		}
	}

	private static String label(Pair p) {
		return switch (p.kind) {
			case REPAIR, COMBINE -> p.what;
			case BOOK, MERGE_BOOKS -> "+ " + p.what;
		};
	}

	private final class Panel implements ContainerOverlays.Overlay {
		@Override
		public boolean applies(AbstractContainerScreen<?> screen) {
			return isEnabled() && screen instanceof AnvilScreen;
		}

		@Override
		public float width(ContainerOverlays.Box box) {
			return W;
		}

		private int rows(List<Pair> list) {
			return Math.max(1, Math.min(MAX_ROWS, list.size()));
		}

		@Override
		public float height(ContainerOverlays.Box box) {
			List<Pair> list = pairs((AnvilMenu) box.screen().getMenu());
			return 18 + rows(list) * ROW + (list.size() > MAX_ROWS ? 10 : 0) + 14;
		}

		@Override
		public void draw(ContainerOverlays.Box box, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			AnvilMenu menu = (AnvilMenu) box.screen().getMenu();
			List<Pair> list = pairs(menu);
			float ly = ContainerOverlays.header(g, Icons.HAMMER, "Anvil helper", x, y, w);
			String count = list.isEmpty() ? "" : list.size() + (list.size() == 1 ? " pair" : " pairs");
			JacText.draw(g, count, x + w - 6 - JacText.width(count, Theme.SMALL, JacText.Weight.REGULAR), y + 6.5f, Theme.TEXT_DIM, Theme.SMALL,
				JacText.Weight.REGULAR);
			hovered = -1;
			// Wait for the server to send the inventory (state 0 means it has not yet) before looking.
			if (box.screen() != offeredFor && menu.getStateId() != 0) {
				offeredFor = box.screen();
				offerRepairs(menu, list);
			}
			if (list.isEmpty()) {
				JacText.draw(g, "Nothing here combines", x + 6, ly + 2, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			for (int i = 0; i < Math.min(MAX_ROWS, list.size()); i++) {
				Pair p = list.get(i);
				boolean hot = mx >= x && mx < x + w && my >= ly && my < ly + ROW;
				boolean inAnvil = p.left == LEFT;
				if (hot) {
					hovered = i;
				}
				if (hot || inAnvil) {
					Draw.rect(g, x + 3, ly - 1, w - 6, ROW, 3f, hot ? Theme.CONTROL_HOVER : Colors.withAlpha(JacConfig.accent(), 0x30));
				}
				InvItems.draw(g, p.leftStack, x + 6, ly, 10);
				InvItems.draw(g, p.rightStack, x + 18, ly, 10);
				String status;
				int statusColor;
				if (p.ready()) {
					status = "ready";
					statusColor = GREEN;
				} else if (inAnvil) {
					status = "click";
					statusColor = JacConfig.accent();
				} else if (AnvilMath.tooExpensive(p.work)) {
					status = "too costly";
					statusColor = AMBER;
				} else {
					status = p.work > 0 ? "work " + p.work : "";
					statusColor = Theme.TEXT_DIM;
				}
				float sw = JacText.width(status, Theme.SMALL, JacText.Weight.SEMIBOLD);
				JacText.draw(g, status, x + w - 6 - sw, ly + 2.5f, statusColor, Theme.SMALL, JacText.Weight.SEMIBOLD);
				JacText.draw(g, JacScreen.fit(label(p), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 40 - sw), x + 31, ly + 2, Theme.TEXT,
					Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			if (list.size() > MAX_ROWS) {
				JacText.draw(g, "and " + (list.size() - MAX_ROWS) + " more", x + 6, ly + 1, Theme.TEXT_DIM, Theme.SMALL, JacText.Weight.REGULAR);
				ly += 10;
			}
			JacText.draw(g, "Click a pair: one item moves per click", x + 6, ly + 3, Theme.TEXT_DIM, Theme.SMALL, JacText.Weight.REGULAR);
		}

		@Override
		public boolean click(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, int button) {
			if (button != 0) {
				return true;
			}
			AnvilMenu menu = (AnvilMenu) box.screen().getMenu();
			List<Pair> list = pairs(menu);
			int i = (int) Math.floor((my - (y + 18)) / ROW);
			if (i >= 0 && i < Math.min(MAX_ROWS, list.size())) {
				GuiSoundsFeature.menuClick();
				step(menu, list.get(i));
				builtState = -1;
			}
			return true;
		}

		@Override
		public void decorate(ContainerOverlays.Box box, GuiGraphics g, double mx, double my) {
			List<Pair> list = pairs;
			if (!ring.on() || hovered < 0 || hovered >= list.size()) {
				return;
			}
			AnvilMenu menu = (AnvilMenu) box.screen().getMenu();
			Pair p = list.get(hovered);
			box.outline(g, menu.getSlot(p.left), JacConfig.accent());
			box.outline(g, menu.getSlot(p.right), AMBER);
		}
	}
}
