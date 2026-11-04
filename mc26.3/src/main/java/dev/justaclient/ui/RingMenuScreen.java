package dev.justaclient.ui;

import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The ring layout of the menu: a ring of light around the client's mark, the seven feature categories and the Command
 * Centre as panels on either side, the pages as buttons along the top of the ring, two gauges on it, and a strip along
 * the bottom. Clicking a panel opens the classic panel on that category. Everything about the ring can be changed from
 * the Customise card, and the Theme page switches back to the classic layout.
 */
public final class RingMenuScreen extends JacScreen {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private static final float PANEL_H = 30f;

	/** A panel: a category, or the Command Centre when {@code category} is null. */
	private record Panel(@Nullable Category category, Icons icon, String label) {
	}

	/** A page button above the ring. */
	private record PageButton(int page, Icons icon, String label) {
	}

	private static final List<Panel> PANELS = new ArrayList<>();
	private static final List<PageButton> PAGES = List.of(new PageButton(ClickGuiScreen.THEME, Icons.PALETTE, "Theme"),
		new PageButton(ClickGuiScreen.PERF, Icons.ACTIVITY, "Performance"), new PageButton(ClickGuiScreen.STATS, Icons.CHART_COLUMN, "Stats"),
		new PageButton(ClickGuiScreen.NOTES, Icons.PENCIL, "Notes"), new PageButton(ClickGuiScreen.FEEDBACK, Icons.FLAG, "Feedback"));

	static {
		for (Category c : Category.values()) {
			PANELS.add(new Panel(c, c.icon, c.label));
		}
		PANELS.add(new Panel(null, Icons.COMMAND, "Command Centre"));
	}

	private final List<Animation> hover = new ArrayList<>();
	private final Animation customise = new Animation(Easing.CUBIC_OUT, 180, 0f);
	private boolean customising;
	private final long born = System.nanoTime();
	private double spin;
	private long lastFrame = System.nanoTime();

	// Layout, worked out each frame.
	private float cx;
	private float cy;
	private float r;
	private float panelW;
	private final float[] panelX = new float[16];
	private final float[] panelY = new float[16];
	/** The panels on screen: categories that have features, then the Command Centre; the first half go on the left. */
	private final List<Panel> shown = new ArrayList<>();
	private int perSide = 4;
	private final float[] pageX = new float[5];
	private final float[] pageY = new float[5];
	private float stripY;
	private final List<float[]> stripButtons = new ArrayList<>();
	private final List<float[]> optionRows = new ArrayList<>();
	private float cardX;
	private float cardY;
	private float cardW;
	private float cardH;

	public RingMenuScreen(@Nullable Screen parent) {
		super("Justaclient", parent);
		while (hover.size() < PANELS.size() + PAGES.size()) {
			hover.add(new Animation(Easing.CUBIC_OUT, 140, 0f));
		}
	}

	private static StyleTokens.Ring ring() {
		return JacConfig.get().style.ring;
	}

	private void layout() {
		StyleTokens.Ring opt = ring();
		float bottom = opt.strip ? height - 26 : height - 8;
		cx = width / 2f;
		cy = (bottom + 10) / 2f + (opt.pageIcons ? 6 : 0);
		r = Math.max(34f, Math.min((bottom - 10) * 0.30f, width * 0.15f));
		panelW = Math.max(96f, Math.min(160f, (width / 2f - r - 28) * 0.92f));
		float gap = 12f;
		shown.clear();
		for (Panel p : PANELS) {
			if (p.category == null || !Features.in(p.category).isEmpty()) {
				shown.add(p);
			}
		}
		perSide = (shown.size() + 1) / 2;
		float span = Math.min(bottom - 18, perSide * (PANEL_H + 10));
		float step = span / perSide;
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < perSide; i++) {
				int idx = side * perSide + i;
				if (idx >= shown.size()) {
					break;
				}
				float y = cy - span / 2f + i * step + (step - PANEL_H) / 2f;
				// Panels near the ring's middle sit a touch further out, so the column follows the curve.
				float rel = (y + PANEL_H / 2f - cy) / (span / 2f);
				float curve = (1f - rel * rel) * 10f;
				panelY[idx] = y;
				panelX[idx] = side == 0 ? cx - r - gap - panelW - curve : cx + r + gap + curve;
			}
		}
		for (int i = 0; i < PAGES.size(); i++) {
			double angle = Math.toRadians(-90 + (i - 2) * 24);
			pageX[i] = cx + (float) Math.cos(angle) * (r + 16);
			pageY[i] = cy + (float) Math.sin(angle) * (r + 16);
		}
		stripY = height - 22;
	}

	/** Twelve panels round the ring need more room than a plain screen, so small GUIs draw the ring a little smaller. */
	@Override
	protected float minWidth() {
		return 560f;
	}

	@Override
	protected float minHeight() {
		return 330f;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float t) {
		layout();
		StyleTokens.Ring opt = ring();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean still = JacConfig.get().style.motion == StyleTokens.Motion.OFF;
		spin += dt * (still ? 0 : opt.speed * 0.18);
		float time = (now - born) / 1e9f;
		int accent = JacConfig.accent();

		drawRing(g, opt, t, time, accent);
		drawCore(g, t, time, accent);
		drawGauges(g, opt, t, accent);
		if (opt.pageIcons) {
			drawPages(g, mx, my, t, accent);
		}
		drawPanels(g, mx, my, t, opt, accent);
		if (opt.strip) {
			drawStrip(g, mx, my, t, accent);
		} else {
			stripButtons.clear();
			float gx = width - 20;
			float gy = height - 20;
			boolean hot = mx >= gx && mx < gx + 14 && my >= gy && my < gy + 14;
			Draw.rect(g, gx, gy, 14, 14, 7f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
			JacText.icon(g, Icons.SETTINGS, gx + 3.5f, gy + 3.5f, 7f, Colors.fade(Theme.TEXT, t));
			stripButtons.add(new float[] {gx, gy, 14, 14, 1});
		}
		float c = customise.animateTo(customising ? 1f : 0f).get();
		if (c > 0.01f) {
			g.nextStratum();
			drawCustomise(g, mx, my, t * c, opt, accent);
		}
	}

	// The ring and the mark in its middle

	private void drawRing(GuiGraphicsExtractor g, StyleTokens.Ring opt, float t, float time, int accent) {
		float rr = r * (0.82f + 0.18f * t);
		float glow = opt.glow * 0.5f;
		if (glow > 0) {
			Draw.shadow(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, rr * 0.55f, Colors.fade(Colors.withAlpha(accent, Math.round(70 * glow)), t));
		}
		// A faint guide ring and a slower counter-rotating outer arc for depth.
		Draw.arc(g, cx, cy, rr, 0.75f, 0f, 1f, Colors.fade(Colors.withAlpha(accent, 0x30), t));
		Draw.arc(g, cx, cy, rr + 7, 0.6f, (float) (-spin * 0.6), 0.32f, Colors.fade(Colors.withAlpha(accent, 0x50), t));
		Draw.arc(g, cx, cy, rr + 7, 0.6f, (float) (-spin * 0.6) + 0.5f, 0.18f, Colors.fade(Colors.withAlpha(accent, 0x38), t));
		Draw.arc(g, cx, cy, rr - 6, 0.5f, (float) (spin * 1.4), 0.12f, Colors.fade(Colors.withAlpha(accent, 0x60), t));
		switch (opt.look) {
			case SOLID -> {
				Draw.arc(g, cx, cy, rr, 2.2f, 0f, 1f, Colors.fade(Colors.withAlpha(accent, 0xC0), t));
				Draw.arc(g, cx, cy, rr, 2.2f, (float) spin, 0.08f, Colors.fade(0xE0FFFFFF, t));
			}
			case DASHES -> {
				int n = 24 + opt.density * 12;
				for (int i = 0; i < n; i++) {
					float start = (float) (spin + i / (double) n);
					float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2 + i * 0.9);
					Draw.arc(g, cx, cy, rr, 1.8f, start, 0.6f / n, Colors.fade(Colors.withAlpha(accent, 0x70 + Math.round(0x8F * pulse)), t));
				}
			}
			default -> {
				int n = 50 + opt.density * 45;
				for (int i = 0; i < n; i++) {
					// Each particle has its own orbit offset and twinkle, seeded by its index so the ring is stable.
					double seed = i * 12.9898;
					double jitter = Math.sin(seed) * 43758.5453;
					jitter -= Math.floor(jitter);
					double angle = (spin * (0.8 + jitter * 0.5) + i / (double) n) * Math.PI * 2;
					float wobble = (float) Math.sin(time * (1.2 + jitter) + seed) * 2.6f;
					float pr = rr + wobble + (float) (jitter - 0.5) * 4f;
					float px = cx + (float) Math.cos(angle) * pr;
					float py = cy + (float) Math.sin(angle) * pr;
					float twinkle = 0.45f + 0.55f * (float) Math.abs(Math.sin(time * (1.5 + jitter * 2) + seed));
					float size = 0.7f + (float) jitter * 1.2f;
					Draw.circle(g, px, py, size, Colors.fade(Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, (float) jitter * 0.35f),
						Math.round(255 * twinkle)), t));
				}
			}
		}
	}

	private void drawCore(GuiGraphicsExtractor g, float t, float time, int accent) {
		float core = r * 0.42f;
		Draw.circle(g, cx, cy, core + 3, Colors.fade(Colors.withAlpha(accent, 0x22), t));
		Draw.circle(g, cx, cy, core, Colors.fade(Colors.withAlpha(Colors.lerp(JacConfig.surface(), 0xFF000000, 0.35f), 0xE0), t));
		Draw.arc(g, cx, cy, core, 0.9f, 0f, 1f, Colors.fade(Colors.withAlpha(accent, 0x90), t));
		float breathe = 0.85f + 0.15f * (float) Math.sin(time * 1.6);
		float size = core * 0.95f;
		float w = JacText.width("J", size, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "J", cx - w / 2f, cy - size * 0.52f, Colors.fade(Colors.withAlpha(accent, Math.round(255 * breathe)), t), size,
			JacText.Weight.SEMIBOLD);
		String word = "JUSTACLIENT";
		float ws = Math.max(5f, r * 0.11f);
		JacText.drawCentered(g, word, cx, cy + core + 9, Colors.fade(Theme.TEXT_DIM, t), ws, JacText.Weight.SEMIBOLD);
	}

	// Gauges

	private void drawGauges(GuiGraphicsExtractor g, StyleTokens.Ring opt, float t, int accent) {
		StyleTokens.Gauge[] which = {opt.leftGauge, opt.rightGauge};
		for (int i = 0; i < 2; i++) {
			if (which[i] == StyleTokens.Gauge.OFF) {
				continue;
			}
			double angle = Math.toRadians(i == 0 ? 135 : 45);
			float gx = cx + (float) Math.cos(angle) * (r + 2);
			float gy = cy + (float) Math.sin(angle) * (r + 2);
			float gr = Math.max(11f, Math.min(18f, r * 0.2f));
			float[] reading = gauge(which[i]);
			Draw.circle(g, gx, gy, gr + 1.5f, Colors.fade(Colors.withAlpha(accent, 0x30), t));
			Draw.circle(g, gx, gy, gr, Colors.fade(Colors.withAlpha(Colors.lerp(JacConfig.surface(), 0xFF000000, 0.4f), 0xF0), t));
			Draw.arc(g, gx, gy, gr - 2, 1.6f, 0f, 1f, Colors.fade(Theme.CONTROL, t));
			int tint = reading[0] > 0.85f && which[i] == StyleTokens.Gauge.MEMORY ? 0xFFF87171 : accent;
			Draw.arc(g, gx, gy, gr - 2, 1.6f, 0f, Math.max(0.001f, reading[0]), Colors.fade(tint, t));
			String value = gaugeText(which[i], reading[1]);
			float vs = gr * 0.5f;
			JacText.drawCentered(g, value, gx, gy - vs * 0.75f, Colors.fade(Theme.TEXT, t), vs, JacText.Weight.SEMIBOLD);
			JacText.drawCentered(g, gaugeLabel(which[i]), gx, gy + vs * 0.45f, Colors.fade(Theme.TEXT_DIM, t), Math.max(4.5f, vs * 0.62f),
				JacText.Weight.REGULAR);
		}
	}

	/** Fill (0..1) and value of a gauge. */
	private float[] gauge(StyleTokens.Gauge gauge) {
		return switch (gauge) {
			case FPS -> {
				int fps = minecraft.getFps();
				yield new float[] {Math.min(1f, fps / 144f), fps};
			}
			case MEMORY -> {
				Runtime rt = Runtime.getRuntime();
				float used = rt.totalMemory() - rt.freeMemory();
				yield new float[] {used / rt.maxMemory(), used / rt.maxMemory() * 100f};
			}
			case PING -> {
				int ping = ping();
				yield new float[] {ping < 0 ? 0f : Math.min(1f, ping / 300f), ping};
			}
			case FEATURES -> {
				List<Feature> all = Features.all();
				long on = all.stream().filter(Feature::isEnabled).count();
				yield new float[] {on / (float) Math.max(1, all.size()), on};
			}
			case OFF -> new float[] {0f, 0f};
		};
	}

	private int ping() {
		if (minecraft.hasSingleplayerServer() || minecraft.getConnection() == null || minecraft.player == null) {
			return -1;
		}
		PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	private static String gaugeText(StyleTokens.Gauge gauge, float value) {
		return switch (gauge) {
			case MEMORY -> Math.round(value) + "%";
			case PING -> value < 0 ? "-" : String.valueOf(Math.round(value));
			default -> String.valueOf(Math.round(value));
		};
	}

	private static String gaugeLabel(StyleTokens.Gauge gauge) {
		return switch (gauge) {
			case FPS -> "fps";
			case MEMORY -> "memory";
			case PING -> "ping ms";
			case FEATURES -> "features";
			case OFF -> "";
		};
	}

	// Pages and panels

	private void drawPages(GuiGraphicsExtractor g, int mx, int my, float t, int accent) {
		for (int i = 0; i < PAGES.size(); i++) {
			PageButton p = PAGES.get(i);
			boolean hot = !customising && Math.hypot(mx - pageX[i], my - pageY[i]) < 9;
			float h = hover.get(PANELS.size() + i).animateTo(hot ? 1f : 0f).get();
			float rad = 8f + h * 1.5f;
			Draw.circle(g, pageX[i], pageY[i], rad + 1f, Colors.fade(Colors.withAlpha(accent, 0x30 + Math.round(0x60 * h)), t));
			Draw.circle(g, pageX[i], pageY[i], rad, Colors.fade(Colors.withAlpha(Colors.lerp(JacConfig.surface(), 0xFF000000, 0.3f), 0xF0), t));
			JacText.icon(g, p.icon, pageX[i] - 3.5f, pageY[i] - 3.5f, 7f, Colors.fade(Colors.lerp(Theme.TEXT_DIM, accent, h), t));
			if (h > 0.05f) {
				JacText.drawCentered(g, p.label, pageX[i], pageY[i] - rad - 9, Colors.fade(Theme.TEXT, t * h), Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}
	}

	private void drawPanels(GuiGraphicsExtractor g, int mx, int my, float t, StyleTokens.Ring opt, int accent) {
		for (int i = 0; i < shown.size(); i++) {
			Panel p = shown.get(i);
			// Panels slide in from their side, one after another.
			float stagger = Math.max(0f, Math.min(1f, t * 1.6f - (i % perSide) * 0.12f));
			float slide = (1f - stagger) * 18f * (i < perSide ? -1 : 1);
			float x = panelX[i] + slide;
			float y = panelY[i];
			float a = t * stagger;
			boolean hot = !customising && mx >= x && mx < x + panelW && my >= y && my < y + PANEL_H;
			float h = hover.get(i).animateTo(hot ? 1f : 0f).get();
			if (opt.connectors) {
				// A line from the panel to the ring, ending in a node where it meets it.
				float ly = y + PANEL_H / 2f;
				float dy = ly - cy;
				float rr = r * (0.82f + 0.18f * t);
				float ringX = Math.abs(dy) < rr ? (float) Math.sqrt(rr * rr - dy * dy) : 0f;
				float from = i < perSide ? x + panelW : cx + ringX;
				float to = i < perSide ? cx - ringX : x;
				if (to > from) {
					Draw.rect(g, from, ly - 0.35f, to - from, 0.7f, 0, Colors.fade(Colors.withAlpha(accent, 0x40 + Math.round(0x90 * h)), a));
					Draw.circle(g, i < perSide ? to : from, ly, 1.6f + h, Colors.fade(Colors.withAlpha(accent, 0xA0 + Math.round(0x5F * h)), a));
				}
			}
			if (h > 0.01f) {
				Draw.shadow(g, x, y, panelW, PANEL_H, 6f, 10f, Colors.fade(Colors.withAlpha(accent, Math.round(0x50 * h)), a));
			}
			Draw.panel(g, x, y, panelW, PANEL_H, a);
			Draw.outline(g, x, y, panelW, PANEL_H, 6f, 0.75f, Colors.fade(Colors.withAlpha(Colors.lerp(0xFFFFFFFF, accent, h), 0x30 + Math.round(0x80 * h)),
				a));
			// An accent tick on the side facing the ring.
			float tick = 10f + 8f * h;
			Draw.rect(g, i < perSide ? x + panelW - 2.5f : x + 1f, y + (PANEL_H - tick) / 2f, 1.5f, tick, 0.75f, Colors.fade(accent, a));
			float ix = i < perSide ? x + 8 : x + 9;
			Draw.rect(g, ix, y + 7, 16, 16, 4f, Colors.fade(Colors.withAlpha(accent, 0x30 + Math.round(0x30 * h)), a));
			JacText.icon(g, p.icon, ix + 4.5f, y + 11.5f, 7f, Colors.fade(Colors.lerp(Theme.TEXT, accent, 0.5f), a));
			float tx = ix + 22;
			float maxW = panelW - (tx - x) - 8;
			JacText.draw(g, fit(p.label, Theme.LABEL, JacText.Weight.SEMIBOLD, maxW), tx, y + 7, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			String sub;
			if (p.category == null) {
				sub = "Mods, servers, tools";
			} else {
				List<Feature> in = Features.in(p.category);
				long on = in.stream().filter(Feature::isEnabled).count();
				sub = on + " of " + in.size() + " on";
				// A tiny bar of how much of the category is on.
				float bw = Math.min(30f, maxW - JacText.width(sub, Theme.SMALL, JacText.Weight.REGULAR) - 6);
				if (bw > 8) {
					float bx = x + panelW - 10 - bw;
					Draw.rect(g, bx, y + 19, bw, 2.5f, 1.25f, Colors.fade(Theme.CONTROL, a));
					Draw.rect(g, bx, y + 19, bw * on / Math.max(1, in.size()), 2.5f, 1.25f, Colors.fade(Colors.withAlpha(accent, 0xC0), a));
				}
			}
			JacText.draw(g, fit(sub, Theme.SMALL, JacText.Weight.REGULAR, maxW), tx, y + 17, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
	}

	// Bottom strip

	private void drawStrip(GuiGraphicsExtractor g, int mx, int my, float t, int accent) {
		float sx = 10;
		float sw = width - 20;
		float y = stripY;
		Draw.panel(g, sx, y, sw, 16, t);
		JacText.icon(g, Icons.USER, sx + 7, y + 4.5f, 7f, Colors.fade(accent, t));
		JacText.draw(g, Profiles.active(), sx + 18, y + 4.5f, Colors.fade(Theme.TEXT, t), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		String where = minecraft.level == null ? "In the menus" : minecraft.hasSingleplayerServer() ? "Singleplayer"
			: minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().name : "Multiplayer";
		float px = sx + 24 + JacText.width(Profiles.active(), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 8;
		JacText.draw(g, "·  " + where + "  ·  " + LocalTime.now().format(CLOCK), px, y + 4.5f, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL + 0.5f,
			JacText.Weight.REGULAR);
		stripButtons.clear();
		String[][] buttons = {{"SETTINGS", "Customise"}, {"LAYOUT_DASHBOARD", "Classic view"}};
		float bx = sx + sw - 3;
		for (int i = buttons.length - 1; i >= 0; i--) {
			float w = JacText.width(buttons[i][1], Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 22;
			bx -= w;
			boolean hot = mx >= bx && mx < bx + w && my >= y + 2 && my < y + 14;
			boolean on = i == 0 && customising;
			Draw.rect(g, bx, y + 2, w, 12, 6f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
			JacText.icon(g, Icons.valueOf(buttons[i][0]), bx + 6, y + 4.5f, 6.5f, Colors.fade(Theme.TEXT, t));
			JacText.draw(g, buttons[i][1], bx + 16, y + 5, Colors.fade(Theme.TEXT, t), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			stripButtons.add(new float[] {bx, y + 2, w, 12, i == 0 ? 1 : 2});
			bx -= 4;
		}
		String hint = "Pick a panel, or a page on the ring";
		float hw = JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR);
		if (cx - hw / 2f > px + 120 && cx + hw / 2f < bx - 10) {
			JacText.drawCentered(g, hint, cx, y + 5, Colors.fade(Theme.TEXT_DIM, t * 0.8f), Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	// The Customise card

	/** One option row: its label, current value, and how a click changes it. */
	private record Option(String label, String value, Runnable next) {
	}

	private List<Option> options() {
		StyleTokens.Ring o = ring();
		String[] three = {"Off", "Soft", "Strong"};
		return List.of(
			new Option("Ring", switch (o.look) {
				case PARTICLES -> "Particles";
				case DASHES -> "Dashes";
				case SOLID -> "Solid";
			}, () -> o.look = StyleTokens.RingLook.values()[(o.look.ordinal() + 1) % StyleTokens.RingLook.values().length]),
			new Option("Spin", new String[] {"Still", "Normal", "Fast"}[o.speed], () -> o.speed = (o.speed + 1) % 3),
			new Option("Density", new String[] {"Light", "Normal", "Dense"}[o.density], () -> o.density = (o.density + 1) % 3),
			new Option("Glow", three[o.glow], () -> o.glow = (o.glow + 1) % 3),
			new Option("Left gauge", gaugeName(o.leftGauge), () -> o.leftGauge = nextGauge(o.leftGauge)),
			new Option("Right gauge", gaugeName(o.rightGauge), () -> o.rightGauge = nextGauge(o.rightGauge)),
			new Option("Page buttons", o.pageIcons ? "On" : "Off", () -> o.pageIcons = !o.pageIcons),
			new Option("Lines to panels", o.connectors ? "On" : "Off", () -> o.connectors = !o.connectors),
			new Option("Bottom strip", o.strip ? "On" : "Off", () -> o.strip = !o.strip));
	}

	private static String gaugeName(StyleTokens.Gauge gauge) {
		return switch (gauge) {
			case FPS -> "Frame rate";
			case MEMORY -> "Memory";
			case PING -> "Ping";
			case FEATURES -> "Features on";
			case OFF -> "Off";
		};
	}

	private static StyleTokens.Gauge nextGauge(StyleTokens.Gauge gauge) {
		return StyleTokens.Gauge.values()[(gauge.ordinal() + 1) % StyleTokens.Gauge.values().length];
	}

	private void drawCustomise(GuiGraphicsExtractor g, int mx, int my, float a, StyleTokens.Ring opt, int accent) {
		List<Option> list = options();
		// On the right, over the panels, so the ring stays in view while you change it.
		cardW = 160;
		cardH = 30 + list.size() * 15 + 24;
		cardX = width - cardW - 8;
		cardY = Math.max(6, Math.min(cy - cardH / 2f, height - cardH - 28));
		Draw.shadow(g, cardX, cardY + 3, cardW, cardH, 8f, 14f, Colors.fade(0x80000000, a));
		Draw.rect(g, cardX, cardY, cardW, cardH, 8f, Colors.fade(Colors.withAlpha(Colors.lerp(Colors.withAlpha(JacConfig.surface(), 0xFF), 0xFF000000,
			0.3f), 0xF6), a));
		Draw.outline(g, cardX, cardY, cardW, cardH, 8f, 0.75f, Colors.fade(Colors.withAlpha(accent, 0x60), a));
		JacText.draw(g, "Customise the ring", cardX + 10, cardY + 9, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Click a value to change it", cardX + 10, cardY + 19, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		optionRows.clear();
		float y = cardY + 30;
		for (Option o : list) {
			boolean hot = mx >= cardX + 4 && mx < cardX + cardW - 4 && my >= y && my < y + 14;
			if (hot) {
				Draw.rect(g, cardX + 4, y, cardW - 8, 14, 4f, Colors.fade(Theme.CONTROL, a));
			}
			JacText.draw(g, o.label, cardX + 10, y + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			float vw = JacText.width(o.value, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 12;
			Draw.rect(g, cardX + cardW - 10 - vw, y + 1.5f, vw, 11, 5.5f, Colors.fade(Colors.withAlpha(accent, hot ? 0x60 : 0x38), a));
			JacText.draw(g, o.value, cardX + cardW - 10 - vw + 6, y + 4, Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			optionRows.add(new float[] {y});
			y += 15;
		}
		float by = cardY + cardH - 20;
		boolean doneHot = mx >= cardX + 10 && mx < cardX + cardW - 10 && my >= by && my < by + 14;
		Draw.rect(g, cardX + 10, by, cardW - 20, 14, 5f, Colors.fade(doneHot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent, a));
		JacText.drawCentered(g, "Done", cardX + cardW / 2f, by + 4, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
	}

	// Input

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (customising) {
			List<Option> list = options();
			for (int i = 0; i < optionRows.size() && i < list.size(); i++) {
				float y = optionRows.get(i)[0];
				if (mx >= cardX + 4 && mx < cardX + cardW - 4 && my >= y && my < y + 14) {
					list.get(i).next.run();
					JacConfig.save();
					return true;
				}
			}
			float by = cardY + cardH - 20;
			boolean inside = mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + cardH;
			if (!inside || my >= by) {
				customising = false;
			}
			if (inside) {
				return true;
			}
		}
		for (float[] b : stripButtons) {
			if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
				if (b[4] == 1) {
					customising = !customising;
				} else {
					JacConfig.get().style.layout = StyleTokens.Layout.CLASSIC;
					JacConfig.save();
					open(new ClickGuiScreen(parent));
				}
				return true;
			}
		}
		if (ring().pageIcons) {
			for (int i = 0; i < PAGES.size(); i++) {
				if (Math.hypot(mx - pageX[i], my - pageY[i]) < 9) {
					open(ClickGuiScreen.at(this, null, PAGES.get(i).page));
					return true;
				}
			}
		}
		for (int i = 0; i < shown.size(); i++) {
			if (mx >= panelX[i] && mx < panelX[i] + panelW && my >= panelY[i] && my < panelY[i] + PANEL_H) {
				Panel p = shown.get(i);
				open(p.category == null ? ClickGuiScreen.at(this, null, ClickGuiScreen.HUB) : ClickGuiScreen.at(this, p.category, ClickGuiScreen.THEME));
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (customising && key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			customising = false;
			return true;
		}
		if (keyboardKey(key).getName().equals(Justaclient.OPEN_MENU.saveString())) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
