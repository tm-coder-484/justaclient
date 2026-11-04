package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The Notifications tab of the Stats page: every toast from this session, newest first, including ones the widget hid. */
final class NotificationsTab {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	private static final float ROW = 15f;
	private static int scroll;
	private static float clearX;
	private static float clearY;
	private static float clearW;

	private NotificationsTab() {
	}

	static void leave() {
		scroll = 0;
	}

	static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float a, double mx, double my) {
		List<Notifications.Entry> entries = new ArrayList<>(Notifications.history());
		float radius = Math.min(5f, JacConfig.get().style.cornerRadius);
		int accent = JacConfig.accent();
		String clear = "Clear";
		clearW = JacText.width(clear, Theme.SMALL, JacText.Weight.REGULAR);
		clearX = x + w - clearW;
		clearY = y + 2;
		boolean hoverClear = !entries.isEmpty() && mx >= clearX && mx < clearX + clearW && my >= clearY - 2 && my < clearY + 9;
		if (!entries.isEmpty()) {
			JacText.draw(g, clear, clearX, clearY, Colors.fade(hoverClear ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		float listY = y + 22;
		float bottom = y + h - 4;
		if (entries.isEmpty()) {
			Draw.rect(g, x, listY, w, 30, radius, Colors.fade(0x0CFFFFFF, a));
			JacText.icon(g, Icons.BELL, x + 10, listY + 10, 9f, Colors.fade(accent, a));
			JacText.draw(g, "Notifications from this session show up here.", x + 26, listY + 11, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.REGULAR);
			return;
		}
		int fits = Math.max(1, (int) ((bottom - listY) / ROW));
		scroll = Math.max(0, Math.min(scroll, entries.size() - fits));
		g.enableScissor((int) x, (int) listY, (int) (x + w), (int) bottom);
		float ry = listY;
		for (int i = scroll; i < entries.size() && ry < bottom; i++) {
			Notifications.Entry e = entries.get(i);
			Draw.rect(g, x, ry, w, ROW - 2, radius, Colors.fade(i % 2 == 0 ? 0x0CFFFFFF : 0x06FFFFFF, a));
			int dot = switch (e.priority) {
				case HIGH -> 0xFFF59E0B;
				case NORMAL -> accent;
				case LOW -> 0xFF6B7280;
			};
			Draw.circle(g, x + 5, ry + (ROW - 2) / 2f, 1.75f, Colors.fade(dot, a));
			JacText.icon(g, e.icon, x + 10, ry + 3, 7f, Colors.fade(e.hidden ? Theme.TEXT_DIM : Theme.TEXT, a));
			float tx = x + 21;
			JacText.draw(g, e.bold, tx, ry + 3, Colors.fade(e.hidden ? Theme.TEXT_DIM : Theme.TEXT, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			tx += JacText.width(e.bold, Theme.SMALL, JacText.Weight.SEMIBOLD) + 4;
			String time = TIME.format(Instant.ofEpochMilli(e.millis)) + (e.hidden ? "  hidden" : "");
			float timeW = JacText.width(time, Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
			JacText.draw(g, fit(e.rest, x + w - timeW - 10 - tx), tx, ry + 3, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, time, x + w - timeW - 5, ry + 3.5f, Colors.fade(Colors.withAlpha(Theme.TEXT_DIM, 0xB0), a), Theme.SMALL - 0.5f,
				JacText.Weight.REGULAR);
			ry += ROW;
		}
		g.disableScissor();
	}

	private static String fit(String text, float max) {
		if (max <= 0) {
			return "";
		}
		if (JacText.width(text, Theme.SMALL, JacText.Weight.REGULAR) <= max) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && JacText.width(cut + "…", Theme.SMALL, JacText.Weight.REGULAR) > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "…";
	}

	static boolean click(double mx, double my) {
		if (!Notifications.history().isEmpty() && mx >= clearX && mx < clearX + clearW && my >= clearY - 2 && my < clearY + 9) {
			Notifications.clearHistory();
			scroll = 0;
			return true;
		}
		return false;
	}

	static boolean scroll(double amount) {
		scroll += amount > 0 ? -2 : 2;
		scroll = Math.max(0, scroll);
		return true;
	}
}
