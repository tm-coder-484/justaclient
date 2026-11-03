package dev.justaclient.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.justaclient.Justaclient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Saved waypoints, kept per world (singleplayer save or server address) in {@code config/justaclient/waypoints.json}.
 */
public final class Waypoints {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("waypoints.json");
	/** Colours handed out to new waypoints in turn. */
	public static final int[] PALETTE = {0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFF472B6, 0xFFA78BFA, 0xFFF87171, 0xFF2DD4BF};
	private static Map<String, List<Waypoint>> byWorld = new LinkedHashMap<>();
	private static boolean loaded;
	/** Name of the waypoint the GPS points at, in the current world. */
	private static @Nullable String tracking;

	public static final class Waypoint {
		public String name;
		public int x;
		public int y;
		public int z;
		public String dimension;
		public int color;
		public boolean visible = true;
		public boolean death;
		/** False for a place saved from afar (the map, typed coordinates) until you get there. Older saves count as visited. */
		public boolean visited = true;
		/** When it was saved, in milliseconds; 0 for older saves. */
		public long created;
		/** The structure it was saved for automatically, such as {@code minecraft:village_plains}, or null. */
		public @Nullable String structure;

		/** For Gson: fields missing from older saves keep the defaults above. */
		private Waypoint() {
		}

		public Waypoint(String name, int x, int y, int z, String dimension, int color) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.dimension = dimension;
			this.color = color;
		}
	}

	private Waypoints() {
	}

	/** A key for the world being played, or null in menus. */
	public static @Nullable String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		MinecraftServer server = mc.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).normalize();
			return "sp:" + (root.getFileName() != null ? root.getFileName().toString() : "world");
		}
		ServerData data = mc.getCurrentServer();
		return data != null ? "mp:" + data.ip.toLowerCase(Locale.ROOT) : "unknown";
	}

	public static String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	/** Every waypoint of the current world, in all dimensions. The list is live; call {@link #save} after changing it. */
	public static List<Waypoint> all() {
		load();
		String key = worldKey();
		if (key == null) {
			return new ArrayList<>();
		}
		return byWorld.computeIfAbsent(key, k -> new ArrayList<>());
	}

	/** Every world that has waypoints, by world key. The map is live; call {@link #save} after changing it. */
	public static Map<String, List<Waypoint>> byWorld() {
		load();
		return byWorld;
	}

	/** Waypoints of the current world in the dimension the player is in. */
	public static List<Waypoint> here() {
		String dim = dimension();
		return all().stream().filter(w -> w.dimension.equals(dim)).toList();
	}

	public static @Nullable Waypoint find(String name) {
		for (Waypoint w : all()) {
			if (w.name.equalsIgnoreCase(name)) {
				return w;
			}
		}
		return null;
	}

	public static Waypoint add(String name, int x, int y, int z) {
		List<Waypoint> list = all();
		Waypoint existing = find(name);
		if (existing != null) {
			list.remove(existing);
		}
		Waypoint w = new Waypoint(name, x, y, z, dimension(), PALETTE[list.size() % PALETTE.length]);
		w.created = System.currentTimeMillis();
		net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
		w.visited = player == null || player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) < VISIT_RANGE * VISIT_RANGE;
		list.add(w);
		save();
		return w;
	}

	/** Renames a waypoint, keeping the GPS on it if it was being tracked. Returns false if the name is taken. */
	public static boolean rename(Waypoint w, String name) {
		Waypoint other = find(name);
		if (other != null && other != w) {
			return false;
		}
		if (w.name.equalsIgnoreCase(tracking)) {
			tracking = name;
		}
		w.name = name;
		return true;
	}

	/** True if the GPS points at this waypoint. */
	public static boolean isTracked(Waypoint w) {
		return w.name.equalsIgnoreCase(tracking);
	}

	/** How close you have to get for a place to count as visited, in blocks. */
	public static final double VISIT_RANGE = 16;

	/** The colour tag a colour belongs to (its place in {@link #PALETTE}), or -1 for a custom colour. */
	public static int tag(int color) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == color) {
				return i;
			}
		}
		return -1;
	}

	/** {@code base}, or "base 2", "base 3"... whichever is free. */
	public static String freeName(String base) {
		if (find(base) == null) {
			return base;
		}
		int n = 2;
		while (find(base + " " + n) != null) {
			n++;
		}
		return base + " " + n;
	}

	/** "minecraft:village_plains" as "Village plains". */
	public static String structureName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').trim();
		return path.isEmpty() ? "Structure" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	/** The next free "Waypoint N" name. */
	public static String nextName() {
		int n = 1;
		while (find("Waypoint " + n) != null) {
			n++;
		}
		return "Waypoint " + n;
	}

	public static boolean remove(String name) {
		Waypoint w = find(name);
		if (w == null) {
			return false;
		}
		all().remove(w);
		if (name.equalsIgnoreCase(tracking)) {
			tracking = null;
		}
		save();
		return true;
	}

	/** Replaces the last death point of this world with a new one. */
	public static void death(int x, int y, int z) {
		all().removeIf(w -> w.death);
		Waypoint w = new Waypoint("Death", x, y, z, dimension(), 0xFFF87171);
		w.death = true;
		all().add(w);
		save();
	}

	public static @Nullable Waypoint tracking() {
		if (tracking == null) {
			return null;
		}
		Waypoint w = find(tracking);
		return w != null && w.dimension.equals(dimension()) ? w : null;
	}

	public static void track(@Nullable String name) {
		tracking = name;
		if (name != null) {
			Locate.clear();
		}
	}

	public static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, List<Waypoint>> read = parse(reader);
			if (read != null) {
				byWorld = read;
			}
		} catch (IOException | JsonParseException e) {
			Justaclient.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	/** Reads a waypoints file; entries without a name or dimension are dropped. */
	public static @Nullable Map<String, List<Waypoint>> parse(Reader reader) {
		Map<String, List<Waypoint>> read = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, List<Waypoint>>>() {
		}.getType());
		if (read != null) {
			read.values().removeIf(java.util.Objects::isNull);
			read.values().forEach(list -> list.removeIf(w -> w == null || w.name == null || w.dimension == null));
		}
		return read;
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				byWorld.values().removeIf(List::isEmpty);
				GSON.toJson(byWorld, writer);
			}
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}
}
