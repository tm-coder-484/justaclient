package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.FrameCost;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Performance page of the click GUI: what Justaclient costs per frame, in total against the 5% budget and per
 * feature. Numbers are CPU time measured around each feature's hooks, smoothed over about a second.
 */
final class PerformancePage {
	private static final float ROW = 13f;
	private static final int OK = 0xFF22C55E;
	private static final int WARN = 0xFFF59E0B;
	private static final int OVER = 0xFFF43F5E;

	private PerformancePage() {
	}

	static void draw(GuiGraphics g, float x, float y, float w, float h, float a) {
		JacText.draw(g, "Performance", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String hint = "CPU time per frame, smoothed over a second";
		JacText.draw(g, hint, x + w - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);

		// Summary: total share against the budget, with the budget marked on the meter.
		float share = FrameCost.share();
		float frameMs = FrameCost.frameNanos() / 1e6f;
		int state = share < FrameCost.BUDGET * 0.6f ? OK : share < FrameCost.BUDGET ? WARN : OVER;
		float sy = y + 22;
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		Draw.rect(g, x, sy, w, 44, radius, Colors.fade(0x10FFFFFF, a));
		JacText.draw(g, String.format("%.1f%%", share * 100f), x + 10, sy + 8, Colors.fade(state, a), 13f, JacText.Weight.SEMIBOLD);
		String verdict = share < FrameCost.BUDGET ? "Under the 5% budget" : "Over the 5% budget";
		JacText.draw(g, verdict, x + 62, sy + 8, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		String frame = String.format("Frame %.2f ms  ·  %d FPS", frameMs, Math.round(1000f / Math.max(0.01f, frameMs)));
		JacText.draw(g, frame, x + 62, sy + 18, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		float mx = x + 10;
		float mw = w - 20;
		float my = sy + 33;
		// The meter spans 0..10% so the budget sits in the middle.
		float scale = 0.10f;
		Draw.rect(g, mx, my, mw, 4, 2, Colors.fade(Theme.CONTROL, a));
		Draw.rect(g, mx, my, Math.max(4f, mw * Math.min(1f, share / scale)), 4, 2, Colors.fade(state, a));
		float bx = mx + mw * (FrameCost.BUDGET / scale);
		Draw.rect(g, bx - 0.5f, my - 3, 1f, 10, 0, Colors.fade(0xC0FFFFFF, a));

		// Per-feature table.
		float ty = sy + 54;
		JacText.draw(g, "Feature", x + 4, ty, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		textRight(g, "Avg", x + w * 0.62f, ty, Colors.fade(Theme.TEXT_DIM, a));
		textRight(g, "Peak", x + w * 0.76f, ty, Colors.fade(Theme.TEXT_DIM, a));
		textRight(g, "Share", x + w - 4, ty, Colors.fade(Theme.TEXT_DIM, a));
		Draw.rect(g, x, ty + 10, w, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		List<FrameCost.Section> sections = FrameCost.sections();
		float rowsTop = ty + 14;
		int fit = (int) Math.max(0, Math.floor((y + h - rowsTop - 14) / ROW));
		float max = 1f;
		for (FrameCost.Section s : sections) {
			max = Math.max(max, s.averageNanos());
		}
		int accent = JacConfig.accent();
		for (int i = 0; i < Math.min(fit, sections.size()); i++) {
			FrameCost.Section s = sections.get(i);
			float ry = rowsTop + i * ROW;
			float barW = (w * 0.44f) * (s.averageNanos() / max);
			Draw.rect(g, x, ry - 1, Math.max(2f, barW), ROW - 2, 3f, Colors.fade(Colors.withAlpha(accent, 0x30), a));
			JacText.draw(g, s.name, x + 4, ry + 1.5f, Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			textRight(g, micros(s.averageNanos()), x + w * 0.62f, ry + 1.5f, Colors.fade(Theme.TEXT, a));
			textRight(g, micros(s.peakNanos()), x + w * 0.76f, ry + 1.5f, Colors.fade(Theme.TEXT_DIM, a));
			textRight(g, String.format("%.2f%%", s.share() * 100f), x + w - 4, ry + 1.5f, Colors.fade(Theme.TEXT, a));
		}
		if (sections.isEmpty()) {
			JacText.drawCentered(g, "Nothing measured yet. Join a world with some features on.", x + w / 2f, rowsTop + 10, Colors.fade(Theme.TEXT_DIM, a),
				Theme.LABEL, JacText.Weight.REGULAR);
		}
		String note = "GPU work (blur, shaders) is not in these numbers. Compare FPS with a feature on and off to see it.";
		JacText.draw(g, note, x, y + h - 10, Colors.fade(Colors.withAlpha(Theme.TEXT_DIM, 0xB0), a), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
	}

	private static String micros(float nanos) {
		float us = nanos / 1000f;
		return us >= 1000f ? String.format("%.2f ms", us / 1000f) : String.format("%.0f µs", us);
	}

	private static void textRight(GuiGraphics g, String s, float right, float y, int color) {
		JacText.draw(g, s, right - JacText.width(s, Theme.SMALL, JacText.Weight.REGULAR), y, color, Theme.SMALL, JacText.Weight.REGULAR);
	}
}
