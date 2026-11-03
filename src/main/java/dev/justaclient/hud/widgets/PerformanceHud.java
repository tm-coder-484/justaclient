package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.common.FrameStats;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.FrameCost;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One overlay for performance: FPS, frame time, the 1% low, a stability meter, a frame-time graph, the client's own
 * frame cost, and entity, chunk and particle counts. Numbers refresh four times a second so they can be read.
 */
public final class PerformanceHud extends HudFeature {
	private static final float GRAPH_W = 110f;
	private static final float GRAPH_H = 26f;
	private static final float GAP = 12f;
	private final BoolSetting graph = bool("graph", "Frame-time graph", true);
	private final BoolSetting lows = bool("lows", "1% low and stability", true);
	private final BoolSetting cost = bool("cost", "Client frame cost", true);
	private final BoolSetting world = bool("world", "Entities, chunks and particles", true);
	private final List<String[]> rows = new ArrayList<>();
	private long refreshed;
	private float stability = 1f;

	public PerformanceHud() {
		super("performance", "Performance overlay", "FPS, 1% lows, frame-time graph, stability and world counts", Icons.CPU, false, 1f, 0.2f);
	}

	@Override
	public void layout(boolean preview) {
		long now = System.currentTimeMillis();
		if (preview || now - refreshed > 250 || rows.isEmpty()) {
			refreshed = now;
			collect(preview);
		}
		float w = graph.on() ? GRAPH_W : 70f;
		for (String[] r : rows) {
			w = Math.max(w, JacText.width(r[0], TEXT, JacText.Weight.REGULAR) + GAP + JacText.width(r[1], TEXT, JacText.Weight.SEMIBOLD));
		}
		width = PAD * 2 + w;
		height = PAD * 2 + rows.size() * ROW - (ROW - JacText.lineHeight(TEXT)) + (graph.on() ? GRAPH_H + 5f : 0f) + (lows.on() ? 6f : 0f);
	}

	private void collect(boolean preview) {
		rows.clear();
		Minecraft mc = Minecraft.getInstance();
		FrameStats f = FrameCost.FRAMES;
		float fps = preview ? 144f : f.averageFps();
		float ms = preview ? 6.9f : f.averageMillis();
		rows.add(new String[] {"FPS", String.format(Locale.ROOT, "%.0f  (%.1f ms)", fps, ms)});
		if (lows.on()) {
			rows.add(new String[] {"1% low", String.format(Locale.ROOT, "%.0f", preview ? 98f : f.onePercentLowFps())});
			stability = preview ? 0.86f : f.stability();
			rows.add(new String[] {"Stability", Math.round(stability * 100) + "%"});
		}
		if (cost.on()) {
			rows.add(new String[] {"Client cost", String.format(Locale.ROOT, "%.1f%%", (preview ? 0.021f : FrameCost.share()) * 100f)});
		}
		if (world.on()) {
			boolean live = !preview && mc.level != null;
			rows.add(new String[] {"Entities", live ? String.valueOf(mc.level.getEntityCount()) : "214"});
			rows.add(new String[] {"Chunks", live ? String.valueOf(mc.levelRenderer.countRenderedSections()) : "812"});
			rows.add(new String[] {"Particles", live ? mc.particleEngine.countParticles() : "1180"});
		}
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float y = PAD;
		float inner = width - PAD * 2;
		for (String[] r : rows) {
			text(g, r[0], PAD, y, Theme.TEXT_DIM, TEXT, JacText.Weight.REGULAR);
			textRight(g, r[1], width - PAD, y, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
			y += ROW;
		}
		y -= ROW - JacText.lineHeight(TEXT);
		if (lows.on()) {
			// The stability meter: green when frames arrive evenly, red when they stutter.
			y += 3f;
			Draw.rect(g, PAD, y, inner, 2f, 1f, Theme.CONTROL);
			Draw.rect(g, PAD, y, Math.max(2f, inner * stability), 2f, 1f, ArmorHud.healthColor(stability));
			y += 3f;
		}
		if (graph.on()) {
			y += 5f;
			drawGraph(g, PAD, y, inner, GRAPH_H, preview);
		}
	}

	private void drawGraph(GuiGraphics g, float x, float y, float w, float h, boolean preview) {
		Draw.rect(g, x, y, w, h, 3f, 0x30000000);
		FrameStats f = FrameCost.FRAMES;
		int bars = (int) (w / 1f);
		float avg = preview ? 7f : Math.max(1f, f.averageMillis());
		// Scale to twice the average frame so spikes stand out but normal frames sit in the middle.
		float top = avg * 2.5f;
		// A guide line at the average.
		Draw.rect(g, x, y + h - h * (avg / top), w, 0.5f, 0, 0x40FFFFFF);
		int accent = JacConfig.accent();
		for (int i = 0; i < bars; i++) {
			float ms = preview ? 7f + (float) (Math.sin(i * 0.7) * 1.5 + (i % 37 == 0 ? 9 : 0)) : f.millisAgo(bars - 1 - i);
			if (ms <= 0f) {
				continue;
			}
			float bh = Math.min(h - 1f, h * (ms / top));
			int color = ms > avg * 2f ? 0xFFF43F5E : ms > avg * 1.4f ? 0xFFF5C542 : Colors.withAlpha(accent, 0xC0);
			Draw.rect(g, x + i, y + h - bh, 1f, bh, 0, color);
		}
	}
}
