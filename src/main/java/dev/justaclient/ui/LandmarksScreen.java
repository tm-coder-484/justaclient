package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.impl.WaypointsFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The landmark board: every waypoint of this world as a card, filtered by colour tag or "Not visited", nearest first.
 * Each card points the way, and has GPS and Map buttons (the map is where you rename, move, recolour or delete).
 */
public final class LandmarksScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float CARD_H = 64f;
	private static final float GAP = 6f;
	/** Filters: ALL, UNVISITED, or a colour tag (0 and up), OTHER for custom colours. */
	private static final int ALL = -3;
	private static final int UNVISITED = -2;
	private static final int OTHER = -1;

	private final InputBox search = new InputBox("", 32, "Search landmarks", Icons.SEARCH);
	private final Scroller scroll = new Scroller();
	private int filter = ALL;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float gx;
	private float gw;
	private float top;
	private float bottom;
	private float chipY;
	private int cols;
	private float cardW;

	public LandmarksScreen(@Nullable Screen parent) {
		super("Landmarks", parent);
	}

	private void layout() {
		pw = Math.min(700f, width - 24f);
		ph = Math.min(400f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		gx = px + PAD;
		gw = pw - PAD * 2;
		search.place(px + pw - PAD - 160, py + 12, 140);
		chipY = py + 38;
		top = py + 60;
		bottom = py + ph - PAD;
		cols = Math.max(1, (int) ((gw + GAP) / (170 + GAP)));
		cardW = (gw - GAP * (cols - 1)) / cols;
	}

	private static double distance(Waypoint w) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !w.dimension.equals(Waypoints.dimension())) {
			return Double.MAX_VALUE;
		}
		double dx = w.x + 0.5 - p.getX();
		double dz = w.z + 0.5 - p.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private List<Waypoint> shown() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		WaypointsFeature feature = WaypointsFeature.get();
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : Waypoints.all()) {
			int tag = Waypoints.tag(w.color);
			boolean pass = filter == ALL || filter == UNVISITED && !w.visited || filter >= OTHER && tag == filter;
			if (pass && (q.isEmpty() || w.name.toLowerCase(Locale.ROOT).contains(q) || feature.tagName(tag).toLowerCase(Locale.ROOT).contains(q))) {
				out.add(w);
			}
		}
		out.sort(Comparator.comparingDouble(LandmarksScreen::distance).thenComparing(w -> w.name.toLowerCase(Locale.ROOT)));
		return out;
	}

	/** The filter chips: label, colour (0 for none) and filter value, only for tags in use. */
	private List<Object[]> chips() {
		WaypointsFeature feature = WaypointsFeature.get();
		List<Waypoint> all = Waypoints.all();
		List<Object[]> out = new ArrayList<>();
		out.add(new Object[] {"All " + all.size(), 0, ALL});
		long unvisited = all.stream().filter(w -> !w.visited).count();
		if (unvisited > 0) {
			out.add(new Object[] {"Not visited " + unvisited, 0, UNVISITED});
		}
		for (int t = 0; t < feature.tagCount(); t++) {
			int tag = t;
			long n = all.stream().filter(w -> Waypoints.tag(w.color) == tag).count();
			if (n > 0) {
				out.add(new Object[] {feature.tagName(t) + " " + n, Waypoints.PALETTE[t], t});
			}
		}
		long other = all.stream().filter(w -> Waypoints.tag(w.color) < 0).count();
		if (other > 0) {
			out.add(new Object[] {"Other " + other, 0xFF9CA3AF, OTHER});
		}
		return out;
	}

	private static float chipWidth(Object[] chip) {
		return JacText.width((String) chip[0], Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + ((int) chip[1] != 0 ? 22 : 14);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.LANDMARK, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Landmarks", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, Waypoints.worldKey() == null ? "Join a world to see its landmarks" : "Waypoints of this world, nearest first",
			px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		search.draw(g, mx, my, a);

		float cx = gx;
		for (Object[] chip : chips()) {
			float w = chipWidth(chip);
			if (cx + w > gx + gw) {
				break;
			}
			boolean on = (int) chip[2] == filter;
			boolean hot = mx >= cx && mx < cx + w && my >= chipY && my < chipY + 15;
			Draw.rect(g, cx, chipY, w, 15, 7.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x50) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			float tx = cx + 7;
			if ((int) chip[1] != 0) {
				Draw.circle(g, cx + 9, chipY + 7.5f, 3f, Colors.fade((int) chip[1], a));
				tx += 8;
			}
			JacText.draw(g, (String) chip[0], tx, chipY + 4, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}

		List<Waypoint> list = shown();
		int rows = (list.size() + cols - 1) / cols;
		scroll.setMax(rows * (CARD_H + GAP) - GAP - (bottom - top));
		float s = scroll.get();
		g.enableScissor((int) gx, (int) top, (int) (gx + gw), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float x = gx + (i % cols) * (cardW + GAP);
			float y = top + (i / cols) * (CARD_H + GAP) - s;
			if (y + CARD_H < top || y > bottom) {
				continue;
			}
			card(g, list.get(i), x, y, mx, my, a);
		}
		g.disableScissor();
		if (list.isEmpty()) {
			JacText.draw(g, Waypoints.all().isEmpty() ? "No waypoints yet: press B where you stand, or right-click the map" : "Nothing matches", gx + 2,
				top + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
	}

	private void card(GuiGraphics g, Waypoint w, float x, float y, int mx, int my, float a) {
		int accent = JacConfig.accent();
		int tag = Waypoints.tag(w.color);
		Draw.rect(g, x, y, cardW, CARD_H, 6f, Colors.fade(Theme.CONTROL, a));
		Draw.rect(g, x, y, 3f, CARD_H, 1.5f, Colors.fade(w.color, a));
		String tagLabel = (w.death ? "Death" : w.structure != null ? "Structure" : WaypointsFeature.get().tagName(tag)).toUpperCase(Locale.ROOT);
		JacText.draw(g, tagLabel, x + 10, y + 6, Colors.fade(w.color, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		if (!w.visited) {
			String nv = "Not visited";
			float nw = JacText.width(nv, Theme.SMALL, JacText.Weight.SEMIBOLD) + 10;
			Draw.rect(g, x + cardW - nw - 6, y + 4, nw, 11, 5.5f, Colors.fade(Colors.withAlpha(0xFFFBBF24, 0x40), a));
			JacText.draw(g, nv, x + cardW - nw - 1, y + 6.5f, Colors.fade(0xFFFBBF24, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		}
		JacText.draw(g, fit(w.name, Theme.LABEL + 0.5f, JacText.Weight.SEMIBOLD, cardW - 34), x + 10, y + 17, Colors.fade(Theme.TEXT, a),
			Theme.LABEL + 0.5f, JacText.Weight.SEMIBOLD);
		double d = distance(w);
		String where;
		LocalPlayer p = Minecraft.getInstance().player;
		if (d == Double.MAX_VALUE) {
			where = WorldArchiveScreen.dimension(w.dimension);
		} else {
			where = d >= 1000 ? String.format("%.1f km", d / 1000) : String.format("%.0f m", d);
			if (p != null) {
				// An arrow pointing the way, relative to where you face.
				float bearing = (float) Math.toDegrees(Math.atan2(-(w.x + 0.5 - p.getX()), w.z + 0.5 - p.getZ()));
				float angle = Mth.wrapDegrees(bearing - p.getYRot());
				g.pose().pushMatrix();
				g.pose().translate(x + cardW - 16, y + 23);
				g.pose().rotate((float) Math.toRadians(angle));
				JacText.icon(g, Icons.NAVIGATION, -5f, -5f, 10f, Colors.fade(w.color, a));
				g.pose().popMatrix();
			}
		}
		if (!StreamModeFeature.hidesCoordinates()) {
			where += " · " + w.x + " " + w.y + " " + w.z;
		}
		JacText.draw(g, fit(where, Theme.SMALL, JacText.Weight.REGULAR, cardW - 20), x + 10, y + 30, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		float by = y + CARD_H - 21;
		boolean here = w.dimension.equals(Waypoints.dimension());
		if (here) {
			boolean tracked = Waypoints.isTracked(w);
			button(g, x + 10, by, 52, tracked ? "On GPS" : "GPS", tracked ? Icons.CHECK : Icons.NAVIGATION, tracked, accent, mx, my, a);
			button(g, x + 66, by, 46, "Map", Icons.MAP, false, Theme.CONTROL_HOVER, mx, my, a);
		}
		if (!w.visible) {
			JacText.icon(g, Icons.EYE_OFF, x + cardW - 16, by + 4, 8f, Colors.fade(Theme.TEXT_DIM, a));
		}
	}

	private void button(GuiGraphics g, float x, float y, float w, String label, Icons icon, boolean on, int color, int mx, int my, float a) {
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + 15 && my >= top && my < bottom;
		int accent = JacConfig.accent();
		int fill = on ? Colors.withAlpha(accent, 0x40) : hot ? Colors.lerp(color, 0xFFFFFFFF, 0.15f) : color;
		Draw.rect(g, x, y, w, 15, 5f, Colors.fade(fill, a));
		JacText.icon(g, icon, x + 6, y + 4, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, label, x + 16, y + 4, Colors.fade(0xFFFFFFFF, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
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
		if (my >= chipY && my < chipY + 15) {
			float cx = gx;
			for (Object[] chip : chips()) {
				float w = chipWidth(chip);
				if (mx >= cx && mx < cx + w) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					filter = (int) chip[2];
					scroll.scrollTo(0);
					return true;
				}
				cx += w + 4;
			}
			return true;
		}
		if (my < top || my >= bottom || mx < gx || mx >= gx + gw) {
			return false;
		}
		List<Waypoint> list = shown();
		int col = (int) ((mx - gx) / (cardW + GAP));
		int row = (int) ((my - top + scroll.get()) / (CARD_H + GAP));
		int i = row * cols + col;
		if (col >= cols || i < 0 || i >= list.size()) {
			return false;
		}
		Waypoint w = list.get(i);
		float x = gx + col * (cardW + GAP);
		float by = top + row * (CARD_H + GAP) - scroll.get() + CARD_H - 21;
		if (!w.dimension.equals(Waypoints.dimension()) || my < by || my >= by + 15) {
			return true;
		}
		if (mx >= x + 10 && mx < x + 62) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			Waypoints.track(Waypoints.isTracked(w) ? null : w.name);
			return true;
		}
		if (mx >= x + 66 && mx < x + 112) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			open(new WorldMapScreen(w));
			return true;
		}
		return true;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, CARD_H);
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused && search.key(key, ctrl)) {
			scroll.scrollTo(0);
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
		scroll.scrollTo(0);
		return true;
	}
}
