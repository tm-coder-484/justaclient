package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.storage.StorageIndex;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.StorageMemoryFeature;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import dev.justaclient.waypoint.Locate;
import dev.justaclient.waypoint.Waypoints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Storage memory search: every item in the containers you opened in this world, with totals, and for the chosen item the
 * containers holding it, nearest first, each with a GPS button. Search by name or amount ("iron 32+", "&lt;5 torch").
 */
public final class StorageScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 18f;
	private static final float CROW = 41f;

	private final InputBox search = new InputBox("", 40, "Search: iron, 64 oak log, <5 torch", Icons.SEARCH);
	private final Scroller listScroll = new Scroller();
	private final Scroller rightScroll = new Scroller();
	private @Nullable String selected;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float lx;
	private float lw;
	private float rx;
	private float rw;
	private float top;
	private float bottom;
	private float rtop;

	public StorageScreen(@Nullable Screen parent) {
		super("Storage", parent);
	}

	private void layout() {
		pw = Math.min(680f, width - 24f);
		ph = Math.min(400f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		lx = px + PAD;
		lw = Math.min(230f, (pw - PAD * 3) * 0.42f);
		rx = lx + lw + PAD;
		rw = px + pw - PAD - rx;
		top = py + 62;
		bottom = py + ph - PAD;
		rtop = py + 76;
		search.place(lx, py + 38, lw);
	}

	private List<StorageIndex.Hit> hits() {
		return StorageMemoryFeature.get().index().search(StorageIndex.Query.parse(search.get()));
	}

	private static double distance(StorageIndex.Container c) {
		Minecraft mc = Minecraft.getInstance();
		if (c.isEnder() || mc.player == null || !c.dimension.equals(Waypoints.dimension())) {
			return Double.MAX_VALUE;
		}
		return Math.sqrt(mc.player.distanceToSqr(c.x + 0.5, c.y + 0.5, c.z + 0.5));
	}

	private List<StorageIndex.Hit> holding(List<StorageIndex.Hit> hits, String id) {
		List<StorageIndex.Hit> out = new ArrayList<>();
		for (StorageIndex.Hit h : hits) {
			if (h.id().equals(id)) {
				out.add(h);
			}
		}
		out.sort(Comparator.comparingDouble(h -> distance(h.container())));
		return out;
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		StorageMemoryFeature feature = StorageMemoryFeature.get();
		StorageIndex index = feature.index();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.PACKAGE_SEARCH, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Storage", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String sub = Waypoints.worldKey() == null ? "Join a world to see what you stored there"
			: index.size() + (index.size() == 1 ? " container" : " containers") + " remembered in this world"
			+ (feature.isEnabled() ? "" : " · Storage memory is off");
		JacText.draw(g, sub, px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		search.draw(g, mx, my, a);

		List<StorageIndex.Hit> hits = hits();
		List<Map.Entry<String, Integer>> totals = StorageIndex.totals(hits);
		if (selected != null && totals.stream().noneMatch(e -> e.getKey().equals(selected))) {
			selected = null;
		}
		if (selected == null && !totals.isEmpty() && !search.get().isBlank()) {
			selected = totals.get(0).getKey();
		}
		listScroll.setMax(totals.size() * ROW - (bottom - top));
		float s = listScroll.get();
		g.enableScissor((int) lx, (int) top, (int) (lx + lw), (int) bottom);
		for (int i = 0; i < totals.size(); i++) {
			float y = top + i * ROW - s;
			if (y + ROW < top || y > bottom) {
				continue;
			}
			Map.Entry<String, Integer> e = totals.get(i);
			boolean hot = mx >= lx && mx < lx + lw && my >= y && my < y + ROW && my >= top && my < bottom;
			boolean chosen = e.getKey().equals(selected);
			if (hot || chosen) {
				Draw.rect(g, lx, y, lw, ROW - 1, 4f, Colors.fade(chosen ? Colors.withAlpha(accent, 0x38) : Theme.CONTROL_HOVER, a));
			}
			ItemStack stack = InvItems.stack(e.getKey());
			InvItems.draw(g, stack, lx + 4, y + 2.5f, 12);
			String count = StorageIndex.stacks(e.getValue(), stack.getMaxStackSize());
			float cw = JacText.width(count, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, fit(name(hits, e.getKey()), Theme.LABEL, JacText.Weight.REGULAR, lw - 32 - cw), lx + 21, y + 5, Colors.fade(Theme.TEXT, a),
				Theme.LABEL, JacText.Weight.REGULAR);
			JacText.draw(g, count, lx + lw - 6 - cw, y + 5.5f, Colors.fade(chosen ? accent : Theme.TEXT_DIM, a), Theme.SMALL + 0.5f,
				JacText.Weight.SEMIBOLD);
		}
		g.disableScissor();
		if (totals.isEmpty()) {
			String empty = index.size() == 0 ? "Open a chest, barrel or shulker box and it shows up here" : "Nothing matches";
			for (String line : wrap(empty, Theme.LABEL, JacText.Weight.REGULAR, lw - 8)) {
				JacText.draw(g, line, lx + 4, top + 4 + wrap(empty, Theme.LABEL, JacText.Weight.REGULAR, lw - 8).indexOf(line) * 10,
					Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			}
		}

		Draw.rect(g, rx - PAD / 2f, py + 38, 0.75f, ph - 50, 0, Colors.fade(Theme.DIVIDER, a));
		if (selected == null) {
			JacText.draw(g, "Pick an item to see where it is", rx, py + 42, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			JacText.draw(g, fit("Amounts: \"iron 32+\", \">=10 diamond\", \"<5 torch\". Shulker box contents count too.", Theme.SMALL,
				JacText.Weight.REGULAR, rw), rx, py + 56, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			return;
		}
		drawHolders(g, hits, mx, my, a);
	}

	private static String name(List<StorageIndex.Hit> hits, String id) {
		for (StorageIndex.Hit h : hits) {
			if (h.id().equals(id)) {
				return h.name();
			}
		}
		return id;
	}

	private void drawHolders(GuiGraphics g, List<StorageIndex.Hit> hits, int mx, int my, float a) {
		int accent = JacConfig.accent();
		ItemStack stack = InvItems.stack(selected);
		List<StorageIndex.Hit> holders = holding(hits, selected);
		int total = holders.stream().mapToInt(StorageIndex.Hit::count).sum();
		float y = py + 38;
		InvItems.draw(g, stack, rx, y, 24);
		JacText.draw(g, fit(name(hits, selected), Theme.TITLE, JacText.Weight.SEMIBOLD, rw - 32), rx + 30, y + 2, Colors.fade(Theme.TEXT, a), Theme.TITLE,
			JacText.Weight.SEMIBOLD);
		String line = total + " in " + holders.size() + (holders.size() == 1 ? " container" : " containers")
			+ (total >= stack.getMaxStackSize() && stack.getMaxStackSize() > 1 ? " · " + StorageIndex.stacks(total, stack.getMaxStackSize()) : "");
		JacText.draw(g, fit(line, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, rw - 32), rx + 30, y + 15, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL + 0.5f, JacText.Weight.REGULAR);

		rightScroll.setMax(holders.size() * (CROW + 3) - (bottom - rtop));
		float s = rightScroll.get();
		boolean hideCoords = StreamModeFeature.hidesCoordinates();
		g.enableScissor((int) rx, (int) rtop, (int) (rx + rw), (int) bottom);
		for (int i = 0; i < holders.size(); i++) {
			float ry = rtop + i * (CROW + 3) - s;
			if (ry + CROW < rtop || ry > bottom) {
				continue;
			}
			StorageIndex.Hit h = holders.get(i);
			StorageIndex.Container c = h.container();
			Draw.rect(g, rx, ry, rw, CROW, 5f, Colors.fade(Theme.CONTROL, a));
			InvItems.draw(g, InvItems.stack(c.block), rx + 6, ry + 12.5f, 16);
			float bx = rx + rw - 6 - 18;
			// The amount sits left of the GPS button; shulker contents get their own small line under it.
			String n = String.valueOf(h.count());
			float nw = JacText.width(n, Theme.LABEL, JacText.Weight.SEMIBOLD);
			String sh = h.inShulkers() > 0 ? h.inShulkers() + " in shulkers" : "";
			float sw = sh.isEmpty() ? 0 : JacText.width(sh, Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, n, bx - 54 - nw, ry + (sh.isEmpty() ? 16f : 10.5f), Colors.fade(accent, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			if (!sh.isEmpty()) {
				JacText.draw(g, sh, bx - 54 - sw, ry + 22.5f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			}
			float textW = bx - 54 - Math.max(nw, sw) - 8 - (rx + 28);
			String title = InvItems.itemName(c.block) + (c.other != null ? " (double)" : "");
			JacText.draw(g, fit(title, Theme.LABEL, JacText.Weight.SEMIBOLD, textW), rx + 28, ry + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			double d = distance(c);
			String where = c.isEnder() ? "Any ender chest opens it"
				: !c.dimension.equals(Waypoints.dimension()) ? WorldArchiveScreen.dimension(c.dimension)
				: (d >= 1000 ? String.format("%.1f km", d / 1000) : String.format("%.0f m", d)) + (hideCoords ? "" : " · " + c.x + " " + c.y + " " + c.z);
			where += " · " + c.used + "/" + c.slots;
			JacText.draw(g, fit(where, Theme.SMALL, JacText.Weight.REGULAR, textW), rx + 28, ry + 17, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.REGULAR);
			// What's inside may have changed since: older looks turn amber, then red.
			long age = System.currentTimeMillis() - c.seen;
			int ageColor = c.seen <= 0 || age > 86_400_000L ? 0xFFF87171 : age > 3_600_000L ? 0xFFFBBF24 : Theme.TEXT_DIM;
			JacText.icon(g, Icons.CLOCK, rx + 28, ry + 28.5f, 6f, Colors.fade(ageColor, a));
			JacText.draw(g, fit("Last checked: " + StorageIndex.ago(c.seen, System.currentTimeMillis()), Theme.SMALL, JacText.Weight.REGULAR,
				bx - 56 - (rx + 37)),
				rx + 37, ry + 28.5f, Colors.fade(ageColor, a), Theme.SMALL, JacText.Weight.REGULAR);
			if (!c.isEnder() && c.dimension.equals(Waypoints.dimension())) {
				boolean tracked = Locate.isTracked(StorageMemoryFeature.target(c, ""));
				button(g, bx - 48, ry + 12.5f, 44, tracked ? "On GPS" : "GPS", tracked ? Icons.CHECK : Icons.NAVIGATION, tracked, mx, my, a);
			}
			boolean fhot = mx >= bx && mx < bx + 16 && my >= ry + 12.5f && my < ry + 28.5f && my >= rtop && my < bottom;
			JacText.icon(g, Icons.TRASH, bx + 4, ry + 16.5f, 8f, Colors.fade(fhot ? 0xFFF87171 : Theme.TEXT_DIM, a));
		}
		g.disableScissor();
	}

	private void button(GuiGraphics g, float x, float y, float w, String label, Icons icon, boolean on, int mx, int my, float a) {
		int accent = JacConfig.accent();
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + 16 && my >= rtop && my < bottom;
		Draw.rect(g, x, y, w, 16, 5f, Colors.fade(on ? Colors.withAlpha(accent, 0x40) : hot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent, a));
		JacText.icon(g, icon, x + 6, y + 4.5f, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, label, x + 16, y + 4.5f, Colors.fade(0xFFFFFFFF, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		search.click(mx, my);
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		List<StorageIndex.Hit> hits = hits();
		if (mx >= lx && mx < lx + lw && my >= top && my < bottom) {
			List<Map.Entry<String, Integer>> totals = StorageIndex.totals(hits);
			int i = (int) ((my - top + listScroll.get()) / ROW);
			if (i >= 0 && i < totals.size()) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				selected = totals.get(i).getKey();
				rightScroll.scrollTo(0);
			}
			return true;
		}
		if (selected == null || mx < rx || mx >= rx + rw || my < rtop || my >= bottom) {
			return false;
		}
		List<StorageIndex.Hit> holders = holding(hits, selected);
		int i = (int) ((my - rtop + rightScroll.get()) / (CROW + 3));
		if (i < 0 || i >= holders.size()) {
			return false;
		}
		float ry = rtop + i * (CROW + 3) - rightScroll.get();
		StorageIndex.Container c = holders.get(i).container();
		float bx = rx + rw - 6 - 18;
		if (my < ry + 12.5f || my >= ry + 28.5f) {
			return true;
		}
		if (mx >= bx && mx < bx + 16) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			StorageMemoryFeature.get().forget(c);
			return true;
		}
		if (mx >= bx - 48 && mx < bx - 4 && !c.isEnder() && c.dimension.equals(Waypoints.dimension())) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			Locate.Target target = StorageMemoryFeature.target(c, holders.get(i).name() + " · " + InvItems.itemName(c.block));
			if (Locate.isTracked(target)) {
				Locate.clear();
			} else {
				Locate.track(target);
				Locate.highlight(target, 45f);
				Notifications.push(Icons.NAVIGATION, "GPS set", target.label());
			}
			return true;
		}
		return true;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx >= rx) {
			rightScroll.scroll(amount, CROW * 2);
		} else {
			listScroll.scroll(amount, ROW * 2);
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused && search.key(key, ctrl)) {
			listScroll.scrollTo(0);
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (!search.focused) {
			search.focused = true;
		}
		search.typed(text);
		listScroll.scrollTo(0);
		return true;
	}
}
