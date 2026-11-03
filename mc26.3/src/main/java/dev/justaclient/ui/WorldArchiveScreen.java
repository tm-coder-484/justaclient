package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.ScreenshotIndex;
import dev.justaclient.common.SessionLog;
import dev.justaclient.common.WorldKeys;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.ScreenshotsFeature;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.hud.SessionHistory;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Scroller;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.world.WorldStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The World Archive: every world the client remembers something about, and for each one its waypoints, play sessions,
 * screenshots and (for the world you are in) the map.
 */
public final class WorldArchiveScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float LIST_W = 150f;
	private static final float ROW = 26f;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);

	/** Everything known about one world. */
	private static final class World {
		final String key;
		final List<Waypoints.Waypoint> waypoints = new ArrayList<>();
		final List<SessionLog.Session> sessions = new ArrayList<>();
		int shots;
		String name;
		@Nullable LocalDateTime last;
		long seconds;

		World(String key) {
			this.key = key;
			this.name = key.length() > 3 ? key.substring(3) : key;
		}

		boolean server() {
			return key.startsWith("mp:");
		}
	}

	private final List<World> worlds = new ArrayList<>();
	private final @Nullable String here = Waypoints.worldKey();
	private @Nullable World selected;
	private final Scroller listScroll = new Scroller();
	private final Scroller waypointScroll = new Scroller();
	private final Scroller sessionScroll = new Scroller();
	private final List<String[]> buttons = new ArrayList<>();

	private float px;
	private float py;
	private float pw;
	private float ph;
	private float rx;
	private float rw;
	private float colTop;
	private float colBottom;
	private float buttonY;

	public WorldArchiveScreen(@Nullable Screen parent) {
		super("World Archive", parent);
		gather();
	}

	private void gather() {
		Map<String, World> byKey = new LinkedHashMap<>();
		for (Map.Entry<String, List<Waypoints.Waypoint>> e : Waypoints.byWorld().entrySet()) {
			if (!e.getValue().isEmpty()) {
				byKey.computeIfAbsent(e.getKey(), World::new).waypoints.addAll(e.getValue());
			}
		}
		for (SessionLog.Session s : SessionHistory.all()) {
			if (s.worldKey == null || s.worldKey.isEmpty()) {
				continue;
			}
			World w = byKey.computeIfAbsent(s.worldKey, World::new);
			w.sessions.add(s);
			w.seconds += s.seconds;
			if (s.world != null && !s.world.isBlank()) {
				w.name = s.world;
			}
			LocalDateTime at = parse(s.started);
			if (at != null && (w.last == null || at.isAfter(w.last))) {
				w.last = at;
			}
		}
		for (ScreenshotIndex.Shot shot : ScreenshotsFeature.get().index().shots.values()) {
			if (shot.worldKey != null) {
				World w = byKey.computeIfAbsent(shot.worldKey, World::new);
				w.shots++;
				if (shot.world != null && w.sessions.isEmpty()) {
					w.name = shot.world;
				}
			}
		}
		if (here != null) {
			byKey.computeIfAbsent(here, World::new);
		}
		worlds.addAll(byKey.values());
		for (World w : worlds) {
			w.sessions.sort(Comparator.comparing((SessionLog.Session s) -> s.started == null ? "" : s.started).reversed());
		}
		// The world you are in first, then the most recently played.
		worlds.sort(Comparator.comparing((World w) -> !w.key.equals(here))
			.thenComparing(w -> w.last, Comparator.nullsLast(Comparator.reverseOrder())));
		selected = worlds.isEmpty() ? null : worlds.get(0);
	}

	private static @Nullable LocalDateTime parse(@Nullable String iso) {
		if (iso == null || iso.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(iso);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String hours(long seconds) {
		long m = seconds / 60;
		return m >= 60 ? m / 60 + " h " + m % 60 + " min" : m + " min";
	}

	private void layout() {
		pw = Math.min(680f, width - 24f);
		ph = Math.min(400f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		rx = px + PAD + LIST_W + PAD;
		rw = px + pw - PAD - rx;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.ARCHIVE, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "World Archive", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, worlds.size() + (worlds.size() == 1 ? " world" : " worlds") + " remembered", px + PAD + 15, py + PAD + 12,
			Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		boolean closeHot = closeHit(mx, my);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		drawList(g, mx, my, a);
		Draw.rect(g, rx - PAD / 2f, py + 38, 0.75f, ph - 38 - PAD, 0, Colors.fade(Theme.DIVIDER, a));
		if (selected != null) {
			drawWorld(g, selected, mx, my, a);
		} else {
			JacText.drawCentered(g, "Play a world and it shows up here", rx + rw / 2f, py + ph / 2f, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL,
				JacText.Weight.REGULAR);
		}
	}

	private void drawList(GuiGraphicsExtractor g, int mx, int my, float a) {
		float lx = px + PAD;
		float top = py + 38;
		float bottom = py + ph - PAD;
		listScroll.setMax(worlds.size() * (ROW + 2) - (bottom - top));
		float s = listScroll.get();
		int accent = JacConfig.accent();
		g.enableScissor((int) lx, (int) top, (int) (lx + LIST_W), (int) bottom);
		for (int i = 0; i < worlds.size(); i++) {
			World w = worlds.get(i);
			float y = top + i * (ROW + 2) - s;
			boolean sel = w == selected;
			boolean hot = mx >= lx && mx < lx + LIST_W && my >= y && my < y + ROW && my >= top && my < bottom;
			if (sel || hot) {
				Draw.rect(g, lx, y, LIST_W, ROW, 5f, Colors.fade(sel ? Colors.withAlpha(accent, 0x40) : Theme.CONTROL, a));
			}
			JacText.icon(g, w.server() ? Icons.SERVER : Icons.HOUSE, lx + 7, y + 9.5f, 7f, Colors.fade(sel ? accent : Theme.TEXT_DIM, a));
			String name = StreamModeFeature.hidesCoordinates() && w.server() ? "Server" : w.name;
			JacText.draw(g, fit(name, Theme.LABEL, JacText.Weight.SEMIBOLD, LIST_W - 26), lx + 20, y + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			String sub = w.key.equals(here) ? "Playing now" : w.last != null ? w.last.format(DAY) : w.waypoints.size() + " waypoints";
			JacText.draw(g, fit(sub, Theme.SMALL, JacText.Weight.REGULAR, LIST_W - 26), lx + 20, y + 15, Colors.fade(w.key.equals(here) ? 0xFF86EFAC
				: Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		g.disableScissor();
	}

	private void drawWorld(GuiGraphicsExtractor g, World w, int mx, int my, float a) {
		int accent = JacConfig.accent();
		float y = py + 38;
		JacText.draw(g, fit(w.name, Theme.TITLE, JacText.Weight.SEMIBOLD, rw), rx, y, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String kind = w.server() ? "Server" : "Singleplayer";
		JacText.draw(g, kind + (w.last != null ? " · last played " + w.last.format(WHEN) : ""), rx, y + 12, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL, JacText.Weight.REGULAR);
		y += 26;
		// Stat tiles.
		String[][] stats = {{"Play time", hours(w.seconds)}, {"Sessions", String.valueOf(w.sessions.size())}, {"Waypoints",
			String.valueOf(w.waypoints.size())}, {"Screenshots", String.valueOf(w.shots)}};
		float tw = (rw - 3 * 6) / 4f;
		for (int i = 0; i < stats.length; i++) {
			float tx = rx + i * (tw + 6);
			Draw.rect(g, tx, y, tw, 30, 5f, Colors.fade(Theme.CONTROL, a));
			JacText.draw(g, stats[i][0], tx + 7, y + 5, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			JacText.draw(g, fit(stats[i][1], Theme.TITLE - 1, JacText.Weight.SEMIBOLD, tw - 12), tx + 7, y + 15, Colors.fade(Theme.TEXT, a),
				Theme.TITLE - 1, JacText.Weight.SEMIBOLD);
		}
		y += 38;
		// Buttons.
		buttons.clear();
		buttons.add(new String[] {"CAMERA", "Screenshots"});
		if (w.key.equals(here)) {
			buttons.add(new String[] {"MAP", "Open map"});
		}
		buttons.add(new String[] {"FOLDER_OPEN", "World data"});
		buttonY = y;
		float bx = rx;
		for (String[] b : buttons) {
			float bw = JacText.width(b[1], Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
			boolean hot = mx >= bx && mx < bx + bw && my >= y && my < y + 16;
			Draw.rect(g, bx, y, bw, 16, 5f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.icon(g, Icons.valueOf(b[0]), bx + 8, y + 4.5f, 7f, Colors.fade(Theme.TEXT, a));
			JacText.draw(g, b[1], bx + 20, y + 4.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			bx += bw + 6;
		}
		y += 26;
		// Two columns: waypoints and sessions.
		float cw = (rw - PAD) / 2f;
		float sx = rx + cw + PAD;
		JacText.draw(g, "Waypoints", rx, y, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Sessions", sx, y, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 12;
		colTop = y;
		colBottom = py + ph - PAD;
		boolean hide = StreamModeFeature.hidesCoordinates();
		waypointScroll.setMax(w.waypoints.size() * 20 - (colBottom - colTop));
		float s = waypointScroll.get();
		g.enableScissor((int) rx, (int) colTop, (int) (rx + cw), (int) colBottom);
		for (int i = 0; i < w.waypoints.size(); i++) {
			Waypoints.Waypoint wp = w.waypoints.get(i);
			float ry = colTop + i * 20 - s;
			Draw.circle(g, rx + 5, ry + 8, 3f, Colors.fade(wp.death ? 0xFFF87171 : wp.color | 0xFF000000, a));
			JacText.draw(g, fit(wp.name, Theme.LABEL, JacText.Weight.SEMIBOLD, cw - 16), rx + 13, ry + 2, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			String where = dimension(wp.dimension) + (hide ? "" : " · " + wp.x + ", " + wp.y + ", " + wp.z);
			JacText.draw(g, fit(where, Theme.SMALL, JacText.Weight.REGULAR, cw - 16), rx + 13, ry + 11, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
		g.disableScissor();
		if (w.waypoints.isEmpty()) {
			JacText.draw(g, "None saved", rx, colTop + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
		}
		sessionScroll.setMax(w.sessions.size() * 20 - (colBottom - colTop));
		float ss = sessionScroll.get();
		g.enableScissor((int) sx, (int) colTop, (int) (sx + cw), (int) colBottom);
		for (int i = 0; i < w.sessions.size(); i++) {
			SessionLog.Session session = w.sessions.get(i);
			float ry = colTop + i * 20 - ss;
			LocalDateTime at = parse(session.started);
			JacText.draw(g, (at != null ? at.format(WHEN) : "?") + " · " + hours(session.seconds), sx, ry + 2, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			String line = session.blocks + " blocks · " + Math.round(session.distance) + " m · " + session.kills + " kills · " + session.deaths
				+ " deaths";
			JacText.draw(g, fit(line, Theme.SMALL, JacText.Weight.REGULAR, cw), sx, ry + 11, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
		g.disableScissor();
		if (w.sessions.isEmpty()) {
			JacText.draw(g, "None recorded", sx, colTop + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
		}
	}

	private static String dimension(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		};
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
		float lx = px + PAD;
		float top = py + 38;
		if (mx >= lx && mx < lx + LIST_W && my >= top && my < py + ph - PAD) {
			int i = (int) ((my - top + listScroll.get()) / (ROW + 2));
			if (i >= 0 && i < worlds.size()) {
				selected = worlds.get(i);
				waypointScroll.scrollTo(0);
				sessionScroll.scrollTo(0);
			}
			return true;
		}
		World w = selected;
		if (w != null && my >= buttonY && my < buttonY + 16) {
			float bx = rx;
			for (String[] b : buttons) {
				float bw = JacText.width(b[1], Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
				if (mx >= bx && mx < bx + bw) {
					switch (b[1]) {
						case "Screenshots" -> open(ScreenshotsScreen.forWorld(this, w.key));
						case "Open map" -> open(new WorldMapScreen());
						default -> {
							Path dir = WorldStore.DIR.resolve(WorldKeys.folder(w.key));
							try {
								Files.createDirectories(dir);
							} catch (java.io.IOException e) {
								// The opener says so if it is missing.
							}
							openPath(dir);
						}
					}
					return true;
				}
				bx += bw + 6;
			}
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx < rx) {
			listScroll.scroll(amount, ROW + 2);
		} else if (mx < rx + (rw - PAD) / 2f) {
			waypointScroll.scroll(amount, 20);
		} else {
			sessionScroll.scroll(amount, 20);
		}
		return true;
	}
}
