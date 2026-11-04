package dev.justaclient.ui;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.DevConsoleFeature;
import dev.justaclient.feature.impl.KeybindsFeature;
import dev.justaclient.feature.impl.LabsFeature;
import dev.justaclient.feature.impl.ModsPanelFeature;
import dev.justaclient.feature.impl.ScreenshotsFeature;
import dev.justaclient.feature.impl.ServersFeature;
import dev.justaclient.feature.impl.WorldArchiveFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The Command Centre: tiles for the client's full screens (mods, servers, key binds, HUD editor and the rest), so each
 * one is a click away from the menu.
 */
public final class HubPage {
	/** One tile. {@code feature} is the feature that switches the screen on and off, or null when it is always there. */
	private record Tile(Icons icon, String title, String blurb, @Nullable Class<? extends Feature> feature, Function<Screen, Screen> open,
		Supplier<Boolean> available) {
	}

	private static final List<Tile> TILES = new ArrayList<>();
	private static final List<Animation> HOVER = new ArrayList<>();
	private static float tileW;
	private static float tileH;
	private static float originX;
	private static float originY;
	private static int columns = 1;
	private static final float GAP = 8f;
	private static final dev.justaclient.ui.widget.Scroller SCROLL = new dev.justaclient.ui.widget.Scroller();
	/** The visible band of the tile grid, for clicks and the scroll wheel. */
	private static float viewTop;
	private static float viewBottom;

	static {
		TILES.add(new Tile(Icons.PACKAGE, "Mods", "Details, settings and on/off for every mod", ModsPanelFeature.class, ModsScreen::new, () -> true));
		TILES.add(new Tile(Icons.SERVER, "Servers", "Folders, favourites and quick switching", ServersFeature.class, ServersScreen::new,
			() -> Minecraft.getInstance().allowsMultiplayer()));
		TILES.add(new Tile(Icons.KEYBOARD, "Key binds", "Search, clashes and key sets per server", KeybindsFeature.class, KeybindsScreen::new,
			() -> true));
		TILES.add(new Tile(Icons.CAMERA, "Screenshots", "Tagged gallery, viewer and side-by-side compare", ScreenshotsFeature.class,
			ScreenshotsScreen::new, () -> true));
		TILES.add(new Tile(Icons.MOVE, "HUD editor", "Move, scale and snap your widgets", null, HudEditorScreen::new, () -> true));
		TILES.add(new Tile(Icons.TERMINAL, "Dev Console", "Live log, diagnostics, pack audit and screen list", DevConsoleFeature.class,
			DevConsoleScreen::new, () -> true));
		TILES.add(new Tile(Icons.ARCHIVE, "World Archive", "Waypoints, sessions and screenshots per world", WorldArchiveFeature.class,
			WorldArchiveScreen::new, () -> true));
		TILES.add(new Tile(Icons.FLASK_ROUND, "Potion guide", "Every potion's brewing route, with the brewing stand helper",
			dev.justaclient.feature.impl.PotionGuideFeature.class, PotionGuideScreen::new, () -> true));
		TILES.add(new Tile(Icons.LANDMARK, "Landmarks", "Every waypoint as a card, by colour tag, with the ones you haven't visited",
			dev.justaclient.feature.impl.WaypointsFeature.class, LandmarksScreen::new, () -> true));
		TILES.add(new Tile(Icons.BACKPACK, "Kits", "Saved inventory layouts: given in singleplayer, checked slot by slot anywhere",
			dev.justaclient.feature.impl.KitsFeature.class, KitsScreen::new, () -> true));
		TILES.add(new Tile(Icons.PACKAGE_SEARCH, "Storage", "Search the chests you opened and find your way back",
			dev.justaclient.feature.impl.StorageMemoryFeature.class, StorageScreen::new, () -> true));
		TILES.add(new Tile(Icons.LEAF, "Biome effects", "Particles, tint, fog colour and ambience for each biome",
			dev.justaclient.feature.impl.BiomeEffectsFeature.class, BiomeEffectsScreen::new, () -> true));
		TILES.add(new Tile(Icons.FLASK, "Labs", "Experimental features and the self-test", LabsFeature.class, LabsScreen::new, () -> true));
	}

	private HubPage() {
	}

	/** Adds a tile; later milestones add their screens here. */
	public static void add(Icons icon, String title, String blurb, @Nullable Class<? extends Feature> feature, Function<Screen, Screen> open) {
		TILES.add(new Tile(icon, title, blurb, feature, open, () -> true));
	}

	private static boolean on(Tile tile) {
		if (!tile.available.get()) {
			return false;
		}
		if (tile.feature == null) {
			return true;
		}
		Feature f = Features.get(tile.feature);
		return f != null && f.isEnabled();
	}

	public static void draw(GuiGraphics g, float x, float y, float w, float h, float a, double mx, double my) {
		JacText.draw(g, "Command Centre", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "The client's full screens, one click away", x, y + 13, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		columns = w >= 300 ? 3 : 2;
		tileW = (w - GAP * (columns - 1)) / columns;
		tileH = 54;
		int rows = (TILES.size() + columns - 1) / columns;
		viewTop = y + 30;
		viewBottom = y + h - 8;
		// More tiles than room (a short window, or screens added later): the grid scrolls instead of running off the panel.
		SCROLL.setMax(rows * (tileH + GAP) - GAP - (viewBottom - viewTop));
		float scroll = SCROLL.get();
		originX = x;
		originY = viewTop - scroll;
		while (HOVER.size() < TILES.size()) {
			HOVER.add(new Animation(Easing.CUBIC_OUT, 140, 0f));
		}
		int accent = JacConfig.accent();
		g.enableScissor((int) Math.floor(x - 2), (int) Math.floor(viewTop - 2), (int) Math.ceil(x + w + 2), (int) Math.ceil(viewBottom));
		for (int i = 0; i < TILES.size(); i++) {
			Tile tile = TILES.get(i);
			float tx = originX + (i % columns) * (tileW + GAP);
			float ty = originY + (i / columns) * (tileH + GAP);
			if (ty > viewBottom || ty + tileH < viewTop - 2) {
				continue;
			}
			boolean on = on(tile);
			boolean hot = on && my >= viewTop && my < viewBottom && mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH;
			float hv = HOVER.get(i).animateTo(hot ? 1f : 0f).get();
			float fade = on ? 1f : 0.45f;
			Draw.rect(g, tx, ty - hv * 1.5f, tileW, tileH, 8f, Colors.fade(Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, hv), a));
			if (hv > 0.01f) {
				Draw.outline(g, tx, ty - hv * 1.5f, tileW, tileH, 8f, 0.75f, Colors.fade(Colors.withAlpha(accent, 0x90), a * hv));
			}
			float iy = ty - hv * 1.5f;
			Draw.rect(g, tx + 9, iy + 9, 18, 18, 5f, Colors.fade(Colors.withAlpha(accent, 0x30), a * fade));
			JacText.icon(g, tile.icon, tx + 13, iy + 13, 10f, Colors.fade(accent, a * fade));
			JacText.draw(g, JacScreen.fit(tile.title, Theme.LABEL + 1f, JacText.Weight.SEMIBOLD, tileW - 40), tx + 33, iy + 13,
				Colors.fade(Theme.TEXT, a * fade), Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
			String blurb = on ? tile.blurb : tile.available.get() ? "Off: turn it on under " + Features.get(tile.feature).category.label
				: "Not available here";
			List<String> lines = JacScreen.wrap(blurb, Theme.SMALL, JacText.Weight.REGULAR, tileW - 18);
			if (lines.size() > 2) {
				lines = List.of(lines.get(0), JacScreen.fit(lines.get(1) + " " + lines.get(2), Theme.SMALL, JacText.Weight.REGULAR, tileW - 18));
			}
			for (int l = 0; l < lines.size(); l++) {
				JacText.draw(g, lines.get(l), tx + 9, iy + 32 + l * 8.5f, Colors.fade(Theme.TEXT_DIM, a * fade), Theme.SMALL, JacText.Weight.REGULAR);
			}
		}
		g.disableScissor();
		if (SCROLL.max() > 0) {
			// A thin bar on the right shows there is more below.
			float trackH = viewBottom - viewTop;
			float barH = Math.max(14, trackH * trackH / (trackH + SCROLL.max()));
			float barY = viewTop + (trackH - barH) * (scroll / SCROLL.max());
			Draw.rect(g, x + w + 4, barY, 2, barH, 1, Colors.fade(Theme.TEXT_DIM & 0x60FFFFFF, a));
		}
	}

	/** The scroll wheel over the tiles. Returns true if it scrolled. */
	public static boolean scroll(double mx, double my, double amount) {
		if (SCROLL.max() <= 0 || my < viewTop || my >= viewBottom) {
			return false;
		}
		SCROLL.scroll(amount, 30f);
		return true;
	}

	/** The screen to open for a click, or null. */
	public static @Nullable Screen click(double mx, double my, Screen parent) {
		for (int i = 0; i < TILES.size(); i++) {
			float tx = originX + (i % columns) * (tileW + GAP);
			float ty = originY + (i / columns) * (tileH + GAP);
			if (my >= viewTop && my < viewBottom && mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH) {
				Tile tile = TILES.get(i);
				return on(tile) ? tile.open.apply(parent) : null;
			}
		}
		return null;
	}
}
