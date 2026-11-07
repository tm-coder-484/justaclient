package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.Justaclient;
import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.DevConsoleFeature;
import dev.justaclient.feature.impl.ScreenStyleFeature;
import dev.justaclient.media.NowPlaying;
import dev.justaclient.render.Draw;
import dev.justaclient.render.GpuInfo;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The Dev Console: the live game log with a filter, a diagnostics sheet you can copy into a bug report, what each resource
 * pack changes and when packs reloaded, and the screens the restyle has seen with a switch for each.
 */
public final class DevConsoleScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float LINE = 9f;
	private static final int MAX_LINES = 4000;
	private static final int WARN = 0xFFFBBF24;
	private static final int ERROR = 0xFFF87171;

	private enum Tab {
		LOG("Log", Icons.SCROLL_TEXT), DIAGNOSTICS("Diagnostics", Icons.ACTIVITY), PACKS("Resource packs", Icons.LAYERS), SCREENS("Screens",
			Icons.MONITOR);

		final String label;
		final Icons icon;

		Tab(String label, Icons icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private enum Level {
		ALL("All"), WARN("Warnings"), ERROR("Errors");

		final String label;

		Level(String label) {
			this.label = label;
		}
	}

	/** A log line and its level: 0 info, 1 warning, 2 error. Stack trace lines take the level of the line above. */
	private record Line(String text, int level) {
	}

	private static Tab tab = Tab.LOG;
	private Level level = Level.ALL;
	private final InputBox search = new InputBox("", 64, "Filter the log", Icons.FILTER);
	private final Scroller scroll = new Scroller();
	private final ArrayDeque<Line> lines = new ArrayDeque<>();
	private final Object lock = new Object();
	private List<Line> shown = List.of();
	private boolean dirty = true;
	private boolean follow = true;
	private final AtomicBoolean reading = new AtomicBoolean();
	private long lastRead;
	private long offset = -1;
	private final StringBuilder partial = new StringBuilder();
	private int lastLevel;
	private String status = "";
	private long statusUntil;
	private final List<String[]> staticFacts = new ArrayList<>();

	private float px;
	private float py;
	private float pw;
	private float ph;
	private float bx;
	private float by;
	private float bw;
	private float bh;

	public DevConsoleScreen(@Nullable Screen parent) {
		super("Dev Console", parent);
	}

	@Override
	protected void setup() {
		staticFacts.clear();
		staticFacts.add(new String[] {"Minecraft", SharedConstants.getCurrentVersion().getName()});
		staticFacts.add(new String[] {"Justaclient", version(Justaclient.MOD_ID)});
		staticFacts.add(new String[] {"Fabric Loader", version("fabricloader")});
		staticFacts.add(new String[] {"Mods loaded", String.valueOf(PlatformLoader.getInstance().getAllMods().size())});
		staticFacts.add(new String[] {"Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"});
		staticFacts.add(new String[] {"OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")});
		staticFacts.addAll(GpuInfo.describe());
		String shaders = PlatformLoader.getInstance().isModLoaded("iris") ? "Iris " + version("iris") : "none";
		staticFacts.add(new String[] {"Shader loader", shaders});
	}

	private static String version(String id) {
		return PlatformLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("not installed");
	}

	private static Path logFile() {
		return PlatformLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
	}

	// Log reading

	private void poll() {
		long now = System.currentTimeMillis();
		if (now - lastRead < 400 || !reading.compareAndSet(false, true)) {
			return;
		}
		lastRead = now;
		Thread t = new Thread(this::readLog, "Justaclient log tail");
		t.setDaemon(true);
		t.start();
	}

	private void readLog() {
		try (RandomAccessFile file = new RandomAccessFile(logFile().toFile(), "r")) {
			long size = file.length();
			if (offset < 0 || size < offset) {
				offset = Math.max(0, size - 256 * 1024);
				partial.setLength(0);
				synchronized (lock) {
					lines.clear();
				}
			}
			if (size == offset) {
				return;
			}
			int len = (int) Math.min(size - offset, 1024 * 1024);
			byte[] buf = new byte[len];
			file.seek(offset);
			file.readFully(buf);
			offset += len;
			partial.append(new String(buf, StandardCharsets.UTF_8));
			int nl;
			List<Line> fresh = new ArrayList<>();
			while ((nl = partial.indexOf("\n")) >= 0) {
				String raw = partial.substring(0, nl).replace("\r", "").replace("\t", "    ");
				partial.delete(0, nl + 1);
				fresh.add(new Line(raw, levelOf(raw)));
			}
			synchronized (lock) {
				lines.addAll(fresh);
				while (lines.size() > MAX_LINES) {
					lines.removeFirst();
				}
				dirty = true;
			}
		} catch (IOException e) {
			synchronized (lock) {
				if (lines.isEmpty()) {
					lines.add(new Line("Could not read " + logFile() + " (" + e.getClass().getSimpleName() + ")", 2));
					dirty = true;
				}
			}
		} finally {
			reading.set(false);
		}
	}

	private int levelOf(String raw) {
		if (raw.contains("/ERROR]") || raw.contains("/FATAL]")) {
			lastLevel = 2;
		} else if (raw.contains("/WARN]")) {
			lastLevel = 1;
		} else if (raw.startsWith("[") || raw.isBlank()) {
			lastLevel = 0;
		}
		// Anything else (stack traces, wrapped text) keeps the level of the line above.
		return lastLevel;
	}

	private void refilter() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		List<Line> out = new ArrayList<>();
		synchronized (lock) {
			for (Line l : lines) {
				if (level == Level.WARN && l.level < 1 || level == Level.ERROR && l.level < 2) {
					continue;
				}
				if (!q.isEmpty() && !l.text.toLowerCase(Locale.ROOT).contains(q)) {
					continue;
				}
				out.add(l);
			}
			dirty = false;
		}
		shown = out;
	}

	// Layout and drawing

	private void layout() {
		pw = Math.min(680f, width - 24f);
		ph = Math.min(400f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		bx = px + PAD;
		by = py + 58;
		bw = pw - PAD * 2;
		bh = py + ph - 34 - by;
		search.place(px + pw - PAD - 160 - 20, py + 10, 160);
	}

	private void say(String text) {
		status = text;
		statusUntil = System.currentTimeMillis() + 2500;
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		int accent = JacConfig.accent();
		JacText.icon(g, Icons.TERMINAL, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Dev Console", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, subtitle(), px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		boolean closeHot = closeHit(mx, my);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (tab == Tab.LOG) {
			search.draw(g, mx, my, a);
		}
		// Tabs.
		float cx = bx;
		float cy = py + 38;
		for (Tab tb : Tab.values()) {
			float w = JacText.width(tb.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 22;
			boolean on = tb == tab;
			boolean hot = mx >= cx && mx < cx + w && my >= cy && my < cy + 13;
			Draw.rect(g, cx, cy, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.icon(g, tb.icon, cx + 6, cy + 3.5f, 6f, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a));
			JacText.draw(g, tb.label, cx + 16, cy + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}
		switch (tab) {
			case LOG -> drawLog(g, mx, my, a);
			case DIAGNOSTICS -> drawDiagnostics(g, mx, my, a);
			case PACKS -> drawPacks(g, mx, my, a);
			case SCREENS -> drawScreens(g, mx, my, a);
		}
		// Footer.
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		float fx = px + PAD;
		for (String[] b : footer()) {
			float w = JacText.width(b[1], Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
			boolean hot = mx >= fx && mx < fx + w && my >= fy && my < fy + 16;
			Draw.rect(g, fx, fy, w, 16, 5f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.icon(g, Icons.valueOf(b[0]), fx + 8, fy + 4.5f, 7f, Colors.fade(Theme.TEXT, a));
			JacText.draw(g, b[1], fx + 20, fy + 4.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			fx += w + 6;
		}
		String hint = System.currentTimeMillis() < statusUntil ? status : hint();
		JacText.draw(g, fit(hint, Theme.SMALL, JacText.Weight.REGULAR, px + pw - PAD - fx - 4), fx + 4, fy + 5, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);
	}

	private String subtitle() {
		return switch (tab) {
			case LOG -> "logs/latest.log, live";
			case DIAGNOSTICS -> "Versions, hardware and media, ready to paste into a bug report";
			case PACKS -> "What each pack changes, and every reload this session";
			case SCREENS -> "Screens opened this session; switch off any you want left vanilla";
		};
	}

	private String hint() {
		return switch (tab) {
			case LOG -> follow ? "Following the end · click a line to copy it" : "Scroll to the bottom to follow · click a line to copy it";
			case DIAGNOSTICS -> "Nothing here leaves your computer unless you paste it";
			case PACKS -> "Hidden = files a higher pack replaces";
			case SCREENS -> "Same list as Screens > Screen style > Skip";
		};
	}

	/** Footer buttons as icon name and label. */
	private List<String[]> footer() {
		return switch (tab) {
			case LOG -> List.of(new String[] {"COPY", "Copy shown"}, new String[] {"FOLDER_OPEN", "Logs folder"});
			case DIAGNOSTICS -> List.<String[]>of(new String[] {"COPY", "Copy report"});
			case PACKS -> List.<String[]>of(new String[] {"REFRESH_CW", "Reload packs"});
			case SCREENS -> List.of();
		};
	}

	private void drawLog(GuiGraphics g, int mx, int my, float a) {
		poll();
		if (dirty) {
			refilter();
		}
		// Level chips on the right of the tab row.
		float cy = py + 38;
		float rx = bx + bw;
		for (int i = Level.values().length - 1; i >= 0; i--) {
			Level l = Level.values()[i];
			float w = JacText.width(l.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			rx -= w;
			boolean on = l == level;
			boolean hot = mx >= rx && mx < rx + w && my >= cy && my < cy + 13;
			int tint = l == Level.ERROR ? ERROR : l == Level.WARN ? WARN : JacConfig.accent();
			Draw.rect(g, rx, cy, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(tint, 0x50) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, l.label, rx + 6, cy + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			rx -= 4;
		}
		Draw.rect(g, bx, by, bw, bh, 5f, Colors.fade(0x40000000, a));
		float max = shown.size() * LINE + 8 - bh;
		scroll.setMax(max);
		if (follow) {
			scroll.scrollTo(Math.max(0, max));
			scroll.snap();
		}
		float s = scroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) bx, (int) by, (int) (bx + bw), (int) (by + bh));
		int first = Math.max(0, (int) ((s - 4) / LINE));
		for (int i = first; i < shown.size(); i++) {
			float y = by + 4 + i * LINE - s;
			if (y > by + bh) {
				break;
			}
			Line l = shown.get(i);
			boolean hot = mx >= bx && mx < bx + bw && my >= y && my < y + LINE && my >= by && my < by + bh;
			if (hot) {
				Draw.rect(g, bx + 2, y - 0.5f, bw - 4, LINE, 2f, Colors.fade(Theme.CONTROL, a));
			}
			int color = l.level == 2 ? ERROR : l.level == 1 ? WARN : Theme.TEXT_DIM;
			JacText.draw(g, fit(l.text, Theme.SMALL, JacText.Weight.REGULAR, bw - 12), bx + 6, y + 1.5f, Colors.fade(color, a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
		g.disableScissor();
		if (shown.isEmpty()) {
			JacText.drawCentered(g, lines.isEmpty() ? "Reading the log..." : "No lines match", bx + bw / 2f, by + 30, Colors.fade(Theme.TEXT_DIM, a),
				Theme.LABEL, JacText.Weight.REGULAR);
		}
		scrollbar(g, s, a);
	}

	private void scrollbar(GuiGraphics g, float s, float a) {
		if (scroll.max() > 0) {
			float barH = Math.max(16f, bh * bh / (bh + scroll.max()));
			float barY = by + (bh - barH) * (s / scroll.max());
			Draw.rect(g, bx + bw + 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
	}

	/** The diagnostics sheet as sections of label and value rows. */
	private List<Object[]> diagnostics() {
		List<Object[]> out = new ArrayList<>();
		out.add(new Object[] {"Game", staticFacts});
		List<String[]> live = new ArrayList<>();
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
		live.add(new String[] {"Memory", used + " MB of " + (rt.maxMemory() >> 20) + " MB (" + (rt.totalMemory() >> 20) + " MB allocated)"});
		live.add(new String[] {"Frame rate", minecraft.getFps() + " fps"});
		live.add(new String[] {"Window", minecraft.getWindow().getWidth() + " x " + minecraft.getWindow().getHeight() + ", GUI scale "
			+ minecraft.getWindow().getGuiScale()});
		live.add(new String[] {"Render distance", minecraft.options.getEffectiveRenderDistance() + " chunks"});
		live.add(new String[] {"Up for", duration(ManagementFactory.getRuntimeMXBean().getUptime())});
		live.add(new String[] {"Features on", Features.all().stream().filter(f -> f.isEnabled()).count() + " of " + Features.all().size()});
		out.add(new Object[] {"Right now", live});
		List<String[]> media = new ArrayList<>();
		for (String line : NowPlaying.describe()) {
			int colon = line.indexOf(": ");
			media.add(colon > 0 ? new String[] {line.substring(0, colon), line.substring(colon + 2)} : new String[] {"", line});
		}
		out.add(new Object[] {"Media (Spotify card)", media});
		return out;
	}

	private static String duration(long millis) {
		long m = millis / 60000;
		return m >= 60 ? m / 60 + " h " + m % 60 + " min" : m + " min";
	}

	@SuppressWarnings("unchecked")
	private void drawDiagnostics(GuiGraphics g, int mx, int my, float a) {
		List<Object[]> sections = diagnostics();
		float content = 0;
		for (Object[] sec : sections) {
			content += 16 + ((List<String[]>) sec[1]).size() * 11 + 6;
		}
		scroll.setMax(content - bh);
		float s = scroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) bx, (int) by, (int) (bx + bw), (int) (by + bh));
		float y = by - s;
		float labelW = 90;
		for (Object[] sec : sections) {
			JacText.draw(g, (String) sec[0], bx, y + 3, Colors.fade(JacConfig.accent(), a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			y += 16;
			for (String[] row : (List<String[]>) sec[1]) {
				JacText.draw(g, row[0], bx + 4, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				JacText.draw(g, fit(row[1], Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD, bw - labelW - 8), bx + labelW, y, Colors.fade(Theme.TEXT, a),
					Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
				if ("Memory".equals(row[0])) {
					Runtime rt = Runtime.getRuntime();
					float frac = (float) (rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
					float mw = JacText.width(row[1], Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 8;
					float barW = Math.min(120, bw - labelW - mw - 8);
					if (barW > 30) {
						Draw.rect(g, bx + labelW + mw, y + 1.5f, barW, 4, 2f, Colors.fade(Theme.CONTROL, a));
						Draw.rect(g, bx + labelW + mw, y + 1.5f, barW * frac, 4, 2f, Colors.fade(frac > 0.85f ? ERROR : JacConfig.accent(), a));
					}
				}
				y += 11;
			}
			y += 6;
		}
		g.disableScissor();
		scrollbar(g, s, a);
	}

	private String report() {
		StringBuilder out = new StringBuilder("Justaclient diagnostics, ").append(new Date()).append('\n');
		for (Object[] sec : diagnostics()) {
			out.append('\n').append(sec[0]).append('\n');
			@SuppressWarnings("unchecked")
			List<String[]> rows = (List<String[]>) sec[1];
			for (String[] row : rows) {
				out.append("  ").append(row[0].isEmpty() ? "" : row[0] + ": ").append(row[1]).append('\n');
			}
		}
		return out.toString();
	}

	private void drawPacks(GuiGraphics g, int mx, int my, float a) {
		List<DevConsoleFeature.PackAudit> all = DevConsoleFeature.audit();
		// Every mod is a pack; the ones without client files would only bury the rest.
		List<DevConsoleFeature.PackAudit> audit = all == null ? null : all.stream().filter(p -> p.total() > 0).toList();
		int empty = all == null ? 0 : all.size() - audit.size();
		List<DevConsoleFeature.Reload> history = DevConsoleFeature.history();
		float rowH = 30;
		float auditH = audit == null ? 30 : audit.size() * (rowH + 4) + (empty > 0 ? 14 : 0);
		float content = 16 + auditH + 10 + 16 + Math.max(1, history.size()) * 22;
		scroll.setMax(content - bh);
		float s = scroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) bx, (int) by, (int) (bx + bw), (int) (by + bh));
		float y = by - s;
		int accent = JacConfig.accent();
		JacText.draw(g, "Active packs, top one wins", bx, y + 3, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 16;
		if (audit == null) {
			JacText.draw(g, "Counting files in each pack...", bx + 4, y + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			y += 30;
		} else {
			for (DevConsoleFeature.PackAudit p : audit) {
				Draw.rect(g, bx, y, bw, rowH, 5f, Colors.fade(Theme.CONTROL, a * 0.6f));
				JacText.draw(g, fit(p.id(), Theme.LABEL, JacText.Weight.SEMIBOLD, bw * 0.45f), bx + 8, y + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL,
					JacText.Weight.SEMIBOLD);
				String sum = p.total() + (p.total() == 1 ? " file" : " files") + (p.vanilla() > 0 ? " · " + p.vanilla() + " replace vanilla" : "") + (p.hidden() > 0 ? " · "
					+ p.hidden() + " hidden" : "");
				JacText.draw(g, sum, bx + 8, y + 17, Colors.fade(p.hidden() > 0 ? WARN : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
				// Kind chips, biggest first, as many as fit, right aligned.
				List<java.util.Map.Entry<String, Integer>> kinds = new ArrayList<>(p.kinds().entrySet());
				kinds.sort(java.util.Map.Entry.<String, Integer>comparingByValue().reversed());
				List<String> labels = new ArrayList<>();
				float room = bw * 0.5f - 6;
				for (java.util.Map.Entry<String, Integer> k : kinds) {
					String label = k.getKey() + " " + k.getValue();
					float w = JacText.width(label, Theme.SMALL, JacText.Weight.REGULAR) + 14;
					if (w > room) {
						break;
					}
					room -= w;
					labels.add(label);
				}
				float cx = bx + bw - 6;
				for (int i = labels.size() - 1; i >= 0; i--) {
					float w = JacText.width(labels.get(i), Theme.SMALL, JacText.Weight.REGULAR) + 10;
					cx -= w;
					Draw.rect(g, cx, y + 9, w, 12, 6f, Colors.fade(Theme.CONTROL, a));
					JacText.draw(g, labels.get(i), cx + 5, y + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
					cx -= 4;
				}
				y += rowH + 4;
			}
			if (empty > 0) {
				JacText.draw(g, "+ " + empty + " mod packs with no client files", bx + 4, y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
					JacText.Weight.REGULAR);
				y += 14;
			}
		}
		y += 10;
		JacText.draw(g, "Reloads this session", bx, y + 3, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 16;
		if (history.isEmpty()) {
			JacText.draw(g, "None recorded yet", bx + 4, y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);
		for (DevConsoleFeature.Reload r : history) {
			JacText.icon(g, r.failed() ? Icons.TRIANGLE_ALERT : Icons.REFRESH_CW, bx + 4, y + 2, 7f, Colors.fade(r.failed() ? ERROR : Theme.TEXT_DIM, a));
			JacText.draw(g, time.format(new Date(r.at())) + "  ·  " + (r.millis() >= 1000 ? String.format(Locale.ENGLISH, "%.1f s", r.millis() / 1000f)
				: r.millis() + " ms") + "  ·  " + r.packs().size() + " packs" + (r.failed() ? "  ·  failed" : ""), bx + 16, y + 1.5f,
				Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			StringBuilder change = new StringBuilder();
			r.added().forEach(id -> change.append("+ ").append(id).append("   "));
			r.removed().forEach(id -> change.append("- ").append(id).append("   "));
			JacText.draw(g, fit(change.isEmpty() ? "Same packs as before" : change.toString().trim(), Theme.SMALL, JacText.Weight.REGULAR, bw - 20),
				bx + 16, y + 11, Colors.fade(change.isEmpty() ? Theme.TEXT_DIM : 0xFF86EFAC, a), Theme.SMALL, JacText.Weight.REGULAR);
			y += 22;
		}
		g.disableScissor();
		scrollbar(g, s, a);
	}

	private @Nullable ScreenStyleFeature style() {
		return Features.get(ScreenStyleFeature.class);
	}

	private void drawScreens(GuiGraphics g, int mx, int my, float a) {
		List<String> seen = ScreenStyleFeature.seenScreens();
		ScreenStyleFeature style = style();
		float rowH = 20;
		scroll.setMax(seen.size() * rowH - bh);
		float s = scroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) bx, (int) by, (int) (bx + bw), (int) (by + bh));
		for (int i = 0; i < seen.size(); i++) {
			String name = seen.get(seen.size() - 1 - i);
			float y = by + i * rowH - s;
			boolean on = style != null && !style.isSkipped(name);
			boolean hot = mx >= bx && mx < bx + bw && my >= y && my < y + rowH && my >= by && my < by + bh;
			if (hot) {
				Draw.rect(g, bx, y, bw, rowH - 2, 4f, Colors.fade(Theme.CONTROL, a));
			}
			JacText.icon(g, Icons.MONITOR, bx + 6, y + 5.5f, 7f, Colors.fade(Theme.TEXT_DIM, a));
			JacText.draw(g, name, bx + 18, y + 5.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			String label = on ? "Restyled" : "Left vanilla";
			JacText.draw(g, label, bx + bw - 32 - JacText.width(label, Theme.SMALL, JacText.Weight.REGULAR), y + 6.5f, Colors.fade(Theme.TEXT_DIM, a),
				Theme.SMALL, JacText.Weight.REGULAR);
			float sx = bx + bw - 26;
			float sy = y + 4;
			Draw.rect(g, sx, sy, 20, 10, 5f, Colors.fade(on ? JacConfig.accent() : 0x30FFFFFF, a));
			Draw.circle(g, on ? sx + 15 : sx + 5, sy + 5, 3.5f, Colors.fade(Theme.KNOB, a));
		}
		g.disableScissor();
		if (seen.isEmpty()) {
			JacText.drawCentered(g, "Open a few game screens and they show up here", bx + bw / 2f, by + 30, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL,
				JacText.Weight.REGULAR);
		} else if (style == null || !style.isEnabled()) {
			JacText.drawCentered(g, "Screen style is off, so these switches do nothing yet", bx + bw / 2f, by + bh - 12, Colors.fade(WARN, a),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
		scrollbar(g, s, a);
	}

	// Input

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
		if (tab == Tab.LOG && search.click(mx, my)) {
			return true;
		}
		search.focused = false;
		float cx = bx;
		float cy = py + 38;
		if (my >= cy && my < cy + 13) {
			for (Tab tb : Tab.values()) {
				float w = JacText.width(tb.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 22;
				if (mx >= cx && mx < cx + w) {
					tab = tb;
					scroll.scrollTo(0);
					scroll.snap();
					follow = true;
					return true;
				}
				cx += w + 4;
			}
			if (tab == Tab.LOG) {
				float rx = bx + bw;
				for (int i = Level.values().length - 1; i >= 0; i--) {
					Level l = Level.values()[i];
					float w = JacText.width(l.label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
					rx -= w;
					if (mx >= rx && mx < rx + w) {
						level = l;
						dirty = true;
						follow = true;
						return true;
					}
					rx -= 4;
				}
			}
		}
		float fy = py + ph - 26;
		if (my >= fy && my < fy + 16) {
			float fx = px + PAD;
			for (String[] b : footer()) {
				float w = JacText.width(b[1], Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
				if (mx >= fx && mx < fx + w) {
					footerAction(b[1]);
					return true;
				}
				fx += w + 6;
			}
		}
		if (mx < bx || mx >= bx + bw || my < by || my >= by + bh) {
			return false;
		}
		float s = scroll.get();
		if (tab == Tab.LOG) {
			int i = (int) ((my - by - 4 + s) / LINE);
			if (i >= 0 && i < shown.size()) {
				minecraft.keyboardHandler.setClipboard(shown.get(i).text);
				say("Copied that line");
			}
			return true;
		}
		if (tab == Tab.SCREENS) {
			List<String> seen = ScreenStyleFeature.seenScreens();
			int i = (int) ((my - by + s) / 20);
			ScreenStyleFeature style = style();
			if (i >= 0 && i < seen.size() && style != null) {
				String name = seen.get(seen.size() - 1 - i);
				style.setSkipped(name, !style.isSkipped(name));
			}
			return true;
		}
		return false;
	}

	private void footerAction(String label) {
		switch (label) {
			case "Copy shown" -> {
				StringBuilder out = new StringBuilder();
				List<Line> list = shown;
				for (int i = Math.max(0, list.size() - 500); i < list.size(); i++) {
					out.append(list.get(i).text).append('\n');
				}
				minecraft.keyboardHandler.setClipboard(out.toString());
				say("Copied " + Math.min(500, list.size()) + " lines");
			}
			case "Logs folder" -> {
				Path dir = logFile().getParent();
				if (Files.isDirectory(dir)) {
					openPath(dir);
				}
			}
			case "Copy report" -> {
				minecraft.keyboardHandler.setClipboard(report());
				say("Copied the report");
			}
			case "Reload packs" -> {
				minecraft.reloadResourcePacks();
				say("Reloading resource packs...");
			}
			default -> {
			}
		}
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, tab == Tab.LOG ? LINE * 3 : 22);
		if (tab == Tab.LOG) {
			follow = scroll.target() >= scroll.max() - 1;
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused && tab == Tab.LOG) {
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			String before = search.get();
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				dirty = true;
				follow = true;
			}
			if (used) {
				return true;
			}
		}
		if (ctrl && key == InputConstants.KEY_F && tab == Tab.LOG) {
			search.focused = true;
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (search.focused && tab == Tab.LOG) {
			search.typed(text);
			dirty = true;
			follow = true;
			return true;
		}
		return false;
	}
}
