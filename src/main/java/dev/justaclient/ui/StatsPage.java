package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.LifetimeStats;
import dev.justaclient.hud.SessionStats;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/** The Stats page of the click GUI: lifetime totals, a few records and play time over the last two weeks. */
final class StatsPage {
	private static final int DAYS = 14;
	private static float resetX;
	private static float resetY;
	private static float resetW;
	private static long armedAt = -1;
	/** Smoothed bar heights, so the chart grows in when the page opens. */
	private static final float[] bars = new float[DAYS];

	private StatsPage() {
	}

	static void leave() {
		armedAt = -1;
		java.util.Arrays.fill(bars, 0f);
	}

	static void draw(GuiGraphics g, float x, float y, float w, float h, float a, double mx, double my) {
		LifetimeStats.Data d = LifetimeStats.data();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		int accent = JacConfig.accent();
		JacText.draw(g, "Stats", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String hint = "Since " + d.since + "  ·  " + d.sessions + (d.sessions == 1 ? " session" : " sessions");
		JacText.draw(g, hint, x + w - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);

		// Headline: play time and K/D.
		float hy = y + 22;
		Draw.rect(g, x, hy, w, 42, radius, Colors.fade(0x10FFFFFF, a));
		JacText.icon(g, Icons.CLOCK, x + 10, hy + 9, 9f, Colors.fade(accent, a));
		JacText.draw(g, duration(d.playSeconds), x + 24, hy + 6, Colors.fade(Theme.TEXT, a), 13f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "played  ·  " + duration(SessionStats.playMillis() / 1000) + " this session", x + 24, hy + 24, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);
		String kd = d.deaths == 0 ? String.valueOf(d.kills) : String.format(Locale.ROOT, "%.2f", d.kills / (float) d.deaths);
		float kdw = JacText.width(kd, 13f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, kd, x + w - 10 - kdw, hy + 6, Colors.fade(Theme.TEXT, a), 13f, JacText.Weight.SEMIBOLD);
		String kdLabel = "kills per death";
		JacText.draw(g, kdLabel, x + w - 10 - JacText.width(kdLabel, Theme.SMALL, JacText.Weight.REGULAR), hy + 24, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);

		// Tiles.
		Object[][] tiles = {
			{Icons.SWORD, "Kills", String.valueOf(d.kills)},
			{Icons.SKULL, "Deaths", String.valueOf(d.deaths)},
			{Icons.BOX, "Blocks mined", compact(d.blocks)},
			{Icons.FOOTPRINTS, "Walked", distance(d.distance)},
			{Icons.GEM, "Diamonds mined", String.valueOf(d.diamonds)},
			{Icons.FLAME, "Ancient debris", String.valueOf(d.debris)},
			{Icons.TROPHY, "Best kill streak", String.valueOf(d.bestKillStreak)},
			{Icons.TIMER, "Longest session", duration(d.longestSessionSeconds)},
		};
		float gap = 5f;
		float tw = (w - gap * 3) / 4f;
		float th = 32f;
		float ty = hy + 42 + 6;
		for (int i = 0; i < tiles.length; i++) {
			float tx = x + (i % 4) * (tw + gap);
			float tyy = ty + (i / 4) * (th + gap);
			Draw.rect(g, tx, tyy, tw, th, radius, Colors.fade(0x0CFFFFFF, a));
			JacText.icon(g, (Icons) tiles[i][0], tx + 7, tyy + 6, 6.5f, Colors.fade(Colors.withAlpha(accent, 0xD0), a));
			JacText.draw(g, (String) tiles[i][1], tx + 17, tyy + 5, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, (String) tiles[i][2], tx + 7, tyy + 16, Colors.fade(Theme.TEXT, a), Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
		}

		// Last two weeks of play time.
		float cy = ty + 2 * (th + gap) + 4;
		JacText.draw(g, "Play time, last two weeks", x, cy, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		float chartTop = cy + 12;
		float chartBottom = y + h - 22;
		float chartH = Math.max(10f, chartBottom - chartTop);
		LocalDate today = LocalDate.now();
		long max = 1800;
		for (int i = 0; i < DAYS; i++) {
			max = Math.max(max, LifetimeStats.secondsOn(today.minusDays(DAYS - 1 - i)));
		}
		float bw = (w - (DAYS - 1) * 4f) / DAYS;
		int hovered = -1;
		for (int i = 0; i < DAYS; i++) {
			LocalDate day = today.minusDays(DAYS - 1 - i);
			long secs = LifetimeStats.secondsOn(day);
			float target = secs / (float) max;
			bars[i] += (target - bars[i]) * 0.18f;
			float bx = x + i * (bw + 4f);
			float bh = secs == 0 ? 0f : Math.max(2f, chartH * bars[i]);
			boolean hover = mx >= bx && mx < bx + bw && my >= chartTop && my < chartBottom + 10;
			if (hover) {
				hovered = i;
			}
			Draw.rect(g, bx, chartTop, bw, chartH, 2f, Colors.fade(0x08FFFFFF, a));
			int color = i == DAYS - 1 ? accent : Colors.withAlpha(accent, hover ? 0xE0 : 0x90);
			Draw.rect(g, bx, chartBottom - bh, bw, bh, 2f, Colors.fade(color, a));
			String label = day.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.ENGLISH);
			JacText.drawCentered(g, label, bx + bw / 2f, chartBottom + 3, Colors.fade(i == DAYS - 1 ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL - 0.5f,
				JacText.Weight.REGULAR);
		}
		if (hovered >= 0) {
			LocalDate day = today.minusDays(DAYS - 1 - hovered);
			String tip = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + day.getDayOfMonth() + "  ·  "
				+ duration(LifetimeStats.secondsOn(day));
			float tw2 = JacText.width(tip, Theme.SMALL, JacText.Weight.REGULAR) + 10;
			float bx = x + hovered * (bw + 4f) + bw / 2f - tw2 / 2f;
			bx = Math.max(x, Math.min(x + w - tw2, bx));
			Draw.rect(g, bx, chartTop - 2, tw2, 13, 4f, Colors.fade(0xE0101218, a));
			JacText.draw(g, tip, bx + 5, chartTop + 0.5f, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
		}

		// Reset, which asks for a second click.
		boolean armed = armedAt >= 0 && System.currentTimeMillis() - armedAt < 3000;
		String reset = armed ? "Click again to clear every total" : "Reset stats";
		resetW = JacText.width(reset, Theme.SMALL, JacText.Weight.REGULAR);
		resetX = x + w - resetW;
		resetY = y + h - 8;
		boolean hoverReset = mx >= resetX && mx < resetX + resetW && my >= resetY - 2 && my < resetY + 9;
		int resetColor = armed ? 0xFFF43F5E : hoverReset ? Theme.TEXT : Theme.TEXT_DIM;
		JacText.draw(g, reset, resetX, resetY, Colors.fade(resetColor, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.draw(g, "Saved in config/justaclient/stats.json", x, resetY, Colors.fade(Colors.withAlpha(Theme.TEXT_DIM, 0xB0), a), Theme.SMALL - 0.5f,
			JacText.Weight.REGULAR);
	}

	static boolean click(double mx, double my) {
		if (mx >= resetX && mx < resetX + resetW && my >= resetY - 2 && my < resetY + 9) {
			if (armedAt >= 0 && System.currentTimeMillis() - armedAt < 3000) {
				LifetimeStats.reset();
				armedAt = -1;
			} else {
				armedAt = System.currentTimeMillis();
			}
			return true;
		}
		return false;
	}

	static String duration(long seconds) {
		long h = seconds / 3600;
		long m = seconds / 60 % 60;
		if (h > 0) {
			return h + "h " + m + "m";
		}
		return m > 0 ? m + "m" : seconds + "s";
	}

	private static String compact(long n) {
		if (n >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", n / 1e6);
		}
		return n >= 10_000 ? String.format(Locale.ROOT, "%.1fk", n / 1e3) : String.valueOf(n);
	}

	private static String distance(double blocks) {
		return blocks >= 1000 ? String.format(Locale.ROOT, "%.1f km", blocks / 1000) : Math.round(blocks) + " m";
	}
}
