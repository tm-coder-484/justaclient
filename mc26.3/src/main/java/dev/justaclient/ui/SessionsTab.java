package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.SessionLog;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.SessionHistory;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Sessions tab of the Stats page: past sessions, newest first. Click one to see what you carried and picked up. */
final class SessionsTab {
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
	private static final float ROW = 24f;
	private static final float DETAIL = 46f;
	private static int expanded = -1;
	private static int scroll;
	private static float listX;
	private static float listY;
	private static float listW;
	private static float listBottom;

	private SessionsTab() {
	}

	static void leave() {
		expanded = -1;
		scroll = 0;
	}

	/** Shows only the current world's sessions. */
	private static boolean thisWorld;
	private static float chipX;
	private static float chipY;
	private static float chipW;

	private static List<SessionLog.Session> sessions() {
		String key = SessionHistory.currentWorldKey();
		if (!thisWorld || key.isEmpty()) {
			return SessionHistory.all();
		}
		return SessionHistory.all().stream().filter(s -> key.equals(s.worldKey)).toList();
	}

	private static float detailH(SessionLog.Session s) {
		return s.slots.isEmpty() ? DETAIL : 62f;
	}

	static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float a, double mx, double my) {
		List<SessionLog.Session> sessions = sessions();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		int accent = JacConfig.accent();
		long total = 0;
		for (SessionLog.Session s : sessions) {
			total += s.seconds;
		}
		String hint = sessions.isEmpty() ? "Nothing saved yet" : sessions.size() + (sessions.size() == 1 ? " session" : " sessions") + "  ·  average "
			+ StatsPage.duration(total / sessions.size());
		JacText.draw(g, hint, x + w - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		chipW = 0;
		if (!SessionHistory.currentWorldKey().isEmpty()) {
			String label = thisWorld ? "This world" : "All worlds";
			chipW = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 14;
			chipX = x + w - chipW;
			chipY = y + 12;
			boolean hover = mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + 9;
			Draw.rect(g, chipX, chipY, chipW, 9, 4.5f, Colors.fade(thisWorld ? Colors.withAlpha(accent, 0xC0) : hover ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.drawCentered(g, label, chipX + chipW / 2f, chipY + 1.5f, Colors.fade(thisWorld ? 0xFFFFFFFF : Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.SEMIBOLD);
		}
		listX = x;
		listY = y + 22;
		listW = w;
		listBottom = y + h - 12;
		if (sessions.isEmpty()) {
			Draw.rect(g, x, listY, w, 42, radius, Colors.fade(0x0CFFFFFF, a));
			JacText.icon(g, Icons.CLOCK, x + 10, listY + 9, 9f, Colors.fade(accent, a));
			JacText.draw(g, "Sessions show up here after you leave a world.", x + 26, listY + 9, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.REGULAR);
			JacText.draw(g, "Turn it off or change how many are kept in Utility > Session history.", x + 26, listY + 23, Colors.fade(Theme.TEXT_DIM, a),
				Theme.SMALL, JacText.Weight.REGULAR);
			return;
		}
		scroll = Math.max(0, Math.min(scroll, sessions.size() - 1));
		g.enableScissor((int) x, (int) listY, (int) (x + w), (int) listBottom);
		float ry = listY;
		for (int i = scroll; i < sessions.size() && ry < listBottom; i++) {
			SessionLog.Session s = sessions.get(i);
			boolean open = i == expanded;
			float rh = open ? ROW + detailH(s) : ROW;
			boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW && my < listBottom;
			Draw.rect(g, x, ry, w, rh - 3, radius, Colors.fade(open ? 0x14FFFFFF : hover ? 0x10FFFFFF : 0x0AFFFFFF, a));
			JacText.icon(g, open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT, x + 6, ry + 7.5f, 6f, Colors.fade(Theme.TEXT_DIM, a));
			JacText.draw(g, fit(s.world, w * 0.32f), x + 16, ry + 4, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			JacText.draw(g, when(s.started), x + 16, ry + 13, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
			String[][] cols = {
				{StatsPage.duration(s.seconds), "played"},
				{s.kills + " / " + s.deaths, "kills / deaths"},
				{StatsPage.compact(s.blocks), "mined"},
				{StatsPage.distance(s.distance), "travelled"},
			};
			float colW = w * 0.15f;
			for (int c = 0; c < cols.length; c++) {
				float colX = x + w - (cols.length - c) * colW;
				JacText.draw(g, cols[c][0], colX, ry + 4, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
				JacText.draw(g, cols[c][1], colX, ry + 13, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
			}
			if (open) {
				drawDetail(g, s, x + 16, ry + ROW, w - 24, a);
			}
			ry += rh;
		}
		g.disableScissor();
		if (scroll > 0 || ry >= listBottom) {
			String more = (scroll + 1) + " to " + Math.min(sessions.size(), scroll + (int) ((listBottom - listY) / ROW)) + " of " + sessions.size()
				+ "  ·  scroll for more";
			JacText.draw(g, more, x, y + h - 8, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
		}
		String file = "Saved in config/justaclient/sessions.json";
		JacText.draw(g, file, x + w - JacText.width(file, Theme.SMALL - 0.5f, JacText.Weight.REGULAR), y + h - 8,
			Colors.fade(Colors.withAlpha(Theme.TEXT_DIM, 0xB0), a), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
	}

	private static void drawDetail(GuiGraphicsExtractor g, SessionLog.Session s, float x, float y, float w, float a) {
		if (!s.slots.isEmpty()) {
			drawInventory(g, s, x, y, a);
			drawPickups(g, s, x, y, w, a);
			return;
		}
		JacText.draw(g, "Carried when you left", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		if (s.carried.isEmpty()) {
			JacText.draw(g, "Nothing recorded", x, y + 12, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL, JacText.Weight.REGULAR);
		}
		float ix = x;
		for (SessionLog.Carried c : s.carried) {
			if (ix > x + w * 0.55f - 16) {
				break;
			}
			Item item = item(c.item);
			if (a > 0.5f) {
				g.pose().pushMatrix();
				g.pose().translate(ix, y + 10);
				g.pose().scale(0.75f, 0.75f);
				g.item(new ItemStack(item), 0, 0);
				g.pose().popMatrix();
			}
			String n = c.count > 999 ? (c.count / 1000) + "k" : String.valueOf(c.count);
			JacText.draw(g, n, ix + 12 - JacText.width(n, Theme.SMALL - 1f, JacText.Weight.SEMIBOLD) / 2f, y + 21, Colors.fade(Theme.TEXT, a),
				Theme.SMALL - 1f, JacText.Weight.SEMIBOLD);
			ix += 16;
		}
		drawPickups(g, s, x, y, w, a);
	}

	private static final float CELL = 11f;

	/** The inventory as it was: main rows, the hotbar below, armour and offhand to the right. */
	private static void drawInventory(GuiGraphicsExtractor g, SessionLog.Session s, float x, float y, float a) {
		JacText.draw(g, "Inventory when you left", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		float gy = y + 10;
		for (int i = 0; i < 41; i++) {
			float[] p = cell(i, x, gy);
			Draw.rect(g, p[0], p[1], CELL - 1, CELL - 1, 1.5f, Colors.fade(0x14FFFFFF, a));
		}
		if (a <= 0.5f) {
			return;
		}
		for (SessionLog.Slot slot : s.slots) {
			if (slot.slot < 0 || slot.slot > 40) {
				continue;
			}
			float[] p = cell(slot.slot, x, gy);
			ItemStack stack = cache.computeIfAbsent(slot, SessionHistory::stackOf);
			g.pose().pushMatrix();
			g.pose().translate(p[0] + 0.5f, p[1] + 0.5f);
			g.pose().scale(0.5625f, 0.5625f);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
			if (slot.count > 1) {
				String n = String.valueOf(slot.count);
				JacText.draw(g, n, p[0] + CELL - 1.5f - JacText.width(n, Theme.SMALL - 2f, JacText.Weight.SEMIBOLD), p[1] + CELL - 6f,
					Colors.fade(0xFFFFFFFF, a), Theme.SMALL - 2f, JacText.Weight.SEMIBOLD);
			}
		}
	}

	private static final Map<SessionLog.Slot, ItemStack> cache = new java.util.IdentityHashMap<>();

	/** Where slot {@code i} sits in the little inventory picture. */
	private static float[] cell(int i, float x, float y) {
		if (i < 9) {
			return new float[] {x + i * CELL, y + 3 * CELL + 3};
		}
		if (i < 36) {
			int row = (i - 9) / 9;
			int col = (i - 9) % 9;
			return new float[] {x + col * CELL, y + row * CELL};
		}
		if (i < 40) {
			// Head at the top.
			return new float[] {x + 9 * CELL + 5, y + (39 - i) * CELL};
		}
		return new float[] {x + 10 * CELL + 7, y + 3 * CELL + 3};
	}

	private static void drawPickups(GuiGraphicsExtractor g, SessionLog.Session s, float x, float y, float w, float a) {
		float px = x + w * 0.58f;
		JacText.draw(g, "Picked up", px, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		int line = 0;
		for (Map.Entry<String, Integer> e : s.pickedUp.entrySet()) {
			if (line == 3) {
				break;
			}
			String name = item(e.getKey()).getName(new ItemStack(item(e.getKey()))).getString();
			JacText.draw(g, e.getValue() + "  " + fit(name, w * 0.36f), px, y + 11 + line * 8.5f, Colors.fade(Theme.TEXT, a), Theme.SMALL,
				JacText.Weight.REGULAR);
			line++;
		}
		if (s.pickedUp.isEmpty()) {
			JacText.draw(g, "Nothing", px, y + 11, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BARRIER);
	}

	private static String when(String iso) {
		try {
			return LocalDateTime.parse(iso).format(WHEN);
		} catch (DateTimeParseException e) {
			return iso;
		}
	}

	private static String fit(String s, float max) {
		if (JacText.width(s, Theme.LABEL, JacText.Weight.SEMIBOLD) <= max) {
			return s;
		}
		while (s.length() > 1 && JacText.width(s + "...", Theme.LABEL, JacText.Weight.SEMIBOLD) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	static boolean click(double mx, double my) {
		if (chipW > 0 && mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + 9) {
			thisWorld = !thisWorld;
			expanded = -1;
			scroll = 0;
			return true;
		}
		List<SessionLog.Session> sessions = sessions();
		float ry = listY;
		for (int i = scroll; i < sessions.size() && ry < listBottom; i++) {
			float rh = i == expanded ? ROW + detailH(sessions.get(i)) : ROW;
			if (mx >= listX && mx < listX + listW && my >= ry && my < ry + ROW && my < listBottom) {
				expanded = expanded == i ? -1 : i;
				return true;
			}
			ry += rh;
		}
		return false;
	}

	static boolean scroll(double amount) {
		scroll = Math.max(0, scroll - (int) Math.signum(amount));
		return true;
	}
}
