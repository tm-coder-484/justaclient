package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.profile.Profiles;
import dev.justaclient.debug.SelfTest;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Scroller;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Labs: the features still marked experimental, each with its switch, and the client's self-test, which checks profiles,
 * mod switching and key lists against throwaway files and reports what it found.
 */
public final class LabsScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 30f;
	private static final int PASS = 0xFF86EFAC;
	private static final int FAIL = 0xFFF87171;
	private static @Nullable List<String> results;
	private static long ranAt;

	private final Scroller featureScroll = new Scroller();
	private final Scroller resultScroll = new Scroller();
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

	public LabsScreen(@Nullable Screen parent) {
		super("Labs", parent);
	}

	private static List<Feature> experimental() {
		return Features.all().stream().filter(Feature::isExperimental).toList();
	}

	private void layout() {
		pw = Math.min(640f, width - 24f);
		ph = Math.min(380f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		lx = px + PAD;
		lw = (pw - PAD * 3) * 0.55f;
		rx = lx + lw + PAD;
		rw = px + pw - PAD - rx;
		top = py + 52;
		bottom = py + ph - PAD;
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.FLASK, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Labs", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Features still being finished, and a check-up for the client", px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);
		boolean closeHot = closeHit(mx, my);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));

		// Experimental features.
		JacText.draw(g, "Experimental features", lx, py + 38, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		List<Feature> list = experimental();
		featureScroll.setMax(list.size() * (ROW + 4) - (bottom - top));
		float s = featureScroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) lx, (int) top, (int) (lx + lw), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			Feature f = list.get(i);
			float y = top + i * (ROW + 4) - s;
			boolean hot = mx >= lx && mx < lx + lw && my >= y && my < y + ROW && my >= top && my < bottom;
			Draw.rect(g, lx, y, lw, ROW, 5f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.icon(g, f.icon, lx + 8, y + 11, 8f, Colors.fade(Theme.TEXT_DIM, a));
			JacText.draw(g, fit(f.name, Theme.LABEL, JacText.Weight.SEMIBOLD, lw - 60), lx + 22, y + 6, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			JacText.draw(g, fit(f.category.label + " · " + f.description, Theme.SMALL, JacText.Weight.REGULAR, lw - 60), lx + 22, y + 17,
				Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			float sx = lx + lw - 28;
			float sy = y + (ROW - 10) / 2f;
			boolean on = f.isEnabled();
			Draw.rect(g, sx, sy, 20, 10, 5f, Colors.fade(on ? accent : 0x30FFFFFF, a));
			Draw.circle(g, on ? sx + 15 : sx + 5, sy + 5, 3.5f, Colors.fade(Theme.KNOB, a));
		}
		g.disableScissor();
		if (list.isEmpty()) {
			JacText.draw(g, "Nothing experimental right now", lx + 4, top + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}

		// Self-test.
		JacText.draw(g, "Self-test", rx, py + 38, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		float y = top;
		for (String line : wrap("Checks profile saving and backups, HUD layouts, mod switching and key lists using throwaway files, then puts "
			+ "everything back. Your settings are not changed.", Theme.SMALL + 0.5f, JacText.Weight.REGULAR, rw)) {
			JacText.draw(g, line, rx, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			y += 9;
		}
		y += 6;
		boolean runHot = mx >= rx && mx < rx + rw && my >= y && my < y + 18;
		Draw.rect(g, rx, y, rw, 18, 5f, Colors.fade(runHot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent, a));
		String label = results == null ? "Run self-test" : "Run again";
		float labelW = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 12;
		JacText.icon(g, Icons.PLAY, rx + (rw - labelW) / 2f, y + 5.5f, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, label, rx + (rw - labelW) / 2f + 12, y + 5.5f, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		runY = y;
		y += 26;
		List<String> res = results;
		if (res != null && !res.isEmpty()) {
			String last = res.get(res.size() - 1);
			boolean ok = "ALL PASSED".equals(last);
			long checks = res.stream().filter(l -> l.startsWith("PASS") || l.startsWith("FAIL")).count();
			Draw.rect(g, rx, y, rw, 20, 5f, Colors.fade(Colors.withAlpha(ok ? PASS : FAIL, 0x30), a));
			JacText.icon(g, ok ? Icons.CHECK : Icons.TRIANGLE_ALERT, rx + 7, y + 6.5f, 7f, Colors.fade(ok ? PASS : FAIL, a));
			JacText.draw(g, (ok ? "All " + checks + " checks passed" : last + " of " + checks) + " · " + ago(), rx + 19, y + 6.5f,
				Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			y += 26;
			float listTop = y;
			resultScroll.setMax(res.size() * 9 - (bottom - listTop));
			float rs = resultScroll.get();
			dev.justaclient.render.LegacyImages.enableScissor(g, (int) rx, (int) listTop, (int) (rx + rw), (int) bottom);
			for (int i = 0; i < res.size(); i++) {
				float ly = listTop + i * 9 - rs;
				if (ly > bottom) {
					break;
				}
				String l = res.get(i);
				int color = l.startsWith("FAIL") ? FAIL : l.startsWith("PASS") ? Theme.TEXT_DIM : Theme.TEXT;
				JacText.draw(g, fit(l, Theme.SMALL, JacText.Weight.REGULAR, rw), rx, ly, Colors.fade(color, a), Theme.SMALL, JacText.Weight.REGULAR);
			}
			g.disableScissor();
		}
	}

	private float runY;

	private static String ago() {
		long s = (System.currentTimeMillis() - ranAt) / 1000;
		return s < 60 ? "just now" : s / 60 + " min ago";
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		if (mx >= rx && mx < rx + rw && my >= runY && my < runY + 18) {
			results = SelfTest.run();
			ranAt = System.currentTimeMillis();
			resultScroll.scrollTo(0);
			return true;
		}
		if (mx >= lx && mx < lx + lw && my >= top && my < bottom) {
			List<Feature> list = experimental();
			int i = (int) ((my - top + featureScroll.get()) / (ROW + 4));
			if (i >= 0 && i < list.size()) {
				Feature f = list.get(i);
				f.toggle();
				Profiles.save();
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx >= rx) {
			resultScroll.scroll(amount, 27);
		} else {
			featureScroll.scroll(amount, ROW + 4);
		}
		return true;
	}
}
