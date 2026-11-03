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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	public static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float a, double mx, double my) {
		JacText.draw(g, "Command Centre", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "The client's full screens, one click away", x, y + 13, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		columns = w >= 300 ? 3 : 2;
		float gap = 8;
		tileW = (w - gap * (columns - 1)) / columns;
		tileH = 58;
		originX = x;
		originY = y + 30;
		while (HOVER.size() < TILES.size()) {
			HOVER.add(new Animation(Easing.CUBIC_OUT, 140, 0f));
		}
		int accent = JacConfig.accent();
		for (int i = 0; i < TILES.size(); i++) {
			Tile tile = TILES.get(i);
			float tx = originX + (i % columns) * (tileW + gap);
			float ty = originY + (i / columns) * (tileH + gap);
			boolean on = on(tile);
			boolean hot = on && mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH;
			float hv = HOVER.get(i).animateTo(hot ? 1f : 0f).get();
			float fade = on ? 1f : 0.45f;
			Draw.rect(g, tx, ty - hv * 1.5f, tileW, tileH, 8f, Colors.fade(Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, hv), a));
			if (hv > 0.01f) {
				Draw.outline(g, tx, ty - hv * 1.5f, tileW, tileH, 8f, 0.75f, Colors.fade(Colors.withAlpha(accent, 0x90), a * hv));
			}
			float iy = ty - hv * 1.5f;
			Draw.rect(g, tx + 9, iy + 9, 18, 18, 5f, Colors.fade(Colors.withAlpha(accent, 0x30), a * fade));
			JacText.icon(g, tile.icon, tx + 13, iy + 13, 10f, Colors.fade(accent, a * fade));
			JacText.draw(g, tile.title, tx + 33, iy + 13, Colors.fade(Theme.TEXT, a * fade), Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
			String blurb = on ? tile.blurb : tile.available.get() ? "Off: turn it on under Screens" : "Not available here";
			List<String> lines = JacScreen.wrap(blurb, Theme.SMALL, JacText.Weight.REGULAR, tileW - 18);
			for (int l = 0; l < Math.min(2, lines.size()); l++) {
				JacText.draw(g, lines.get(l), tx + 9, iy + 33 + l * 8.5f, Colors.fade(Theme.TEXT_DIM, a * fade), Theme.SMALL, JacText.Weight.REGULAR);
			}
		}
	}

	/** The screen to open for a click, or null. */
	public static @Nullable Screen click(double mx, double my, Screen parent) {
		for (int i = 0; i < TILES.size(); i++) {
			float tx = originX + (i % columns) * (tileW + 8);
			float ty = originY + (i / columns) * (tileH + 8);
			if (mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH) {
				Tile tile = TILES.get(i);
				return on(tile) ? tile.open.apply(parent) : null;
			}
		}
		return null;
	}
}
