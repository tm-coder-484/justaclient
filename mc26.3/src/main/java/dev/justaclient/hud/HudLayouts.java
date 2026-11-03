package dev.justaclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Features;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Saved HUD layouts: which widgets are on, where they sit and how big they are. A few come built in (Combat among
 * them); your own are kept in {@code config/justaclient/hud-layouts.json}. Switch in the HUD editor or with a key.
 */
public final class HudLayouts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("hud-layouts.json");

	public static final class Slot {
		public boolean on;
		public float x;
		public float y;
		public @Nullable Float ax;
		public @Nullable Float ay;
		public double scale = 1.0;
	}

	public static final class Layout {
		public String name = "";
		public Map<String, Slot> slots = new LinkedHashMap<>();
		transient boolean builtIn;
	}

	private static final class Saved {
		String active = "Default";
		List<Layout> layouts = new ArrayList<>();
	}

	private static @Nullable Saved saved;

	private HudLayouts() {
	}

	private static Saved saved() {
		if (saved == null) {
			saved = new Saved();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Saved read = GSON.fromJson(reader, Saved.class);
					if (read != null && read.layouts != null) {
						read.layouts.removeIf(l -> l == null || l.name == null || l.slots == null);
						saved = read;
					}
				} catch (IOException | JsonParseException e) {
					Justaclient.LOGGER.warn("Could not read {}", FILE, e);
					// Set aside so the next save (any layout change) does not write over the layouts in it.
					try {
						Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + "." + System.currentTimeMillis() + ".broken"));
					} catch (IOException moveFailed) {
						Justaclient.LOGGER.warn("Could not set aside {}", FILE, moveFailed);
					}
				}
			}
		}
		return saved;
	}

	private static void write() {
		try {
			Files.createDirectories(FILE.getParent());
			// Written beside it and moved over it, so a crash mid-write cannot leave half a file.
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(saved(), writer);
			}
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	/** Built-in layouts first, then yours. */
	public static List<Layout> all() {
		List<Layout> list = new ArrayList<>(builtIns());
		list.addAll(saved().layouts);
		return list;
	}

	public static String active() {
		return saved().active == null ? "Default" : saved().active;
	}

	private static List<Layout> builtIns() {
		List<Layout> list = new ArrayList<>();
		Layout def = new Layout();
		def.name = "Default";
		for (HudFeature hud : Features.hud()) {
			def.slots.put(hud.id, slot(hud.enabledByDefault(), hud.defaultX(), hud.defaultY()));
		}
		list.add(def);
		list.add(builtIn("Combat", new Object[][] {
			{"player_info", 0f, 0f}, {"potions", 1f, 0f}, {"target_hud", 0.66f, 0.6f}, {"combat_info", 0.3f, 0.6f}, {"armor", 0.86f, 1f},
			{"cooldowns", 0.5f, 0.76f}, {"consumables", 0.5f, 0.68f}, {"notifications", 1f, 0.62f}, {"keybinds", 0f, 0.5f},
		}));
		list.add(builtIn("Explore", new Object[][] {
			{"minimap", 1f, 0f}, {"coordinates", 0f, 0f}, {"biome", 0.5f, 0.06f}, {"day_cycle", 0f, 0.14f}, {"gps", 0.5f, 0.14f}, {"speed", 0f, 0.26f},
			{"potions", 1f, 0.5f}, {"notifications", 1f, 0.78f}, {"todos", 0f, 0.9f},
		}));
		list.add(builtIn("Minimal", new Object[][] {
			{"coordinates", 0f, 0f}, {"potions", 1f, 0f}, {"notifications", 1f, 0.62f},
		}));
		list.forEach(l -> l.builtIn = true);
		return list;
	}

	private static Layout builtIn(String name, Object[][] on) {
		Layout layout = new Layout();
		layout.name = name;
		for (HudFeature hud : Features.hud()) {
			layout.slots.put(hud.id, slot(false, hud.defaultX(), hud.defaultY()));
		}
		for (Object[] o : on) {
			layout.slots.put((String) o[0], slot(true, (float) o[1], (float) o[2]));
		}
		return layout;
	}

	private static Slot slot(boolean on, float x, float y) {
		Slot s = new Slot();
		s.on = on;
		s.x = x;
		s.y = y;
		return s;
	}

	public static boolean isBuiltIn(Layout layout) {
		return layout.builtIn;
	}

	public static void apply(Layout layout) {
		for (HudFeature hud : Features.hud()) {
			Slot s = layout.slots.get(hud.id);
			if (s == null) {
				continue;
			}
			hud.setEnabled(s.on);
			hud.relX = Math.max(0f, Math.min(1f, s.x));
			hud.relY = Math.max(0f, Math.min(1f, s.y));
			hud.anchorX = s.ax == null ? Float.NaN : s.ax;
			hud.anchorY = s.ay == null ? Float.NaN : s.ay;
			hud.scale.set(s.scale);
		}
		if (layout.builtIn) {
			// Built-in layouts are written for any screen size, so nudge apart whatever overlaps on this one.
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			float sw = mc.getWindow().getGuiScaledWidth();
			float sh = mc.getWindow().getGuiScaledHeight();
			float gap = Features.get(dev.justaclient.feature.impl.HudEditorFeature.class).gap();
			for (HudFeature hud : Features.hud()) {
				if (hud.isEnabled()) {
					HudPlacer.place(hud, sw, sh, gap);
				}
			}
		}
		saved().active = layout.name;
		write();
		Profiles.save();
	}

	/** Saves the current widgets as a new layout and returns its name. */
	public static String saveCurrent() {
		List<String> names = all().stream().map(l -> l.name).toList();
		int n = 1;
		while (names.contains("My layout " + n)) {
			n++;
		}
		Layout layout = new Layout();
		layout.name = "My layout " + n;
		capture(layout);
		saved().layouts.add(layout);
		saved().active = layout.name;
		write();
		return layout.name;
	}

	/** Writes the current widgets into one of your layouts. */
	public static void overwrite(Layout layout) {
		if (layout.builtIn) {
			return;
		}
		layout.slots.clear();
		capture(layout);
		write();
	}

	private static void capture(Layout layout) {
		for (HudFeature hud : Features.hud()) {
			Slot s = slot(hud.isEnabled(), hud.relX, hud.relY);
			s.ax = Float.isNaN(hud.anchorX) ? null : hud.anchorX;
			s.ay = Float.isNaN(hud.anchorY) ? null : hud.anchorY;
			s.scale = hud.scale.get();
			layout.slots.put(hud.id, s);
		}
	}

	public static void delete(Layout layout) {
		if (layout.builtIn) {
			return;
		}
		saved().layouts.removeIf(l -> l.name.equals(layout.name));
		if (layout.name.equals(saved().active)) {
			saved().active = "Default";
		}
		write();
	}

	/** Applies the next layout in the list, from the key in HUD editor settings. */
	public static void cycle() {
		List<Layout> list = all();
		int i = 0;
		for (int k = 0; k < list.size(); k++) {
			if (list.get(k).name.equals(active())) {
				i = k;
			}
		}
		Layout next = list.get((i + 1) % list.size());
		apply(next);
		Notifications.push(Icons.LAYOUT_DASHBOARD, next.name, "HUD layout");
	}
}
