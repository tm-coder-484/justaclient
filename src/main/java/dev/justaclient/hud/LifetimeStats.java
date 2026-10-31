package dev.justaclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.justaclient.Justaclient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Totals across every session, kept in {@code config/justaclient/stats.json} for the Stats page: play time (with a
 * per-day history for the chart), kills, deaths, blocks, distance and a few records. Only things you did are counted.
 */
public final class LifetimeStats {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("stats.json");
	private static final int DAYS_KEPT = 120;

	/** The saved form. Field names are the JSON keys. */
	public static final class Data {
		public String since = LocalDate.now().toString();
		public long playSeconds;
		public int sessions;
		public int kills;
		public int deaths;
		public long blocks;
		public double distance;
		public int diamonds;
		public int debris;
		public int bestSessionKills;
		public long longestSessionSeconds;
		public int bestKillStreak;
		/** ISO date to seconds played that day. */
		public Map<String, Long> days = new LinkedHashMap<>();
	}

	private static Data data = new Data();
	private static boolean loaded;
	private static long sessionStart = -1;
	private static long lastTick = -1;
	private static int streak;
	private static int sessionKills;
	private static long dirtyAt = -1;

	private LifetimeStats() {
	}

	public static Data data() {
		load();
		return data;
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Data read = GSON.fromJson(reader, Data.class);
			if (read != null) {
				data = read;
				if (data.days == null) {
					data.days = new LinkedHashMap<>();
				}
			}
		} catch (IOException | RuntimeException e) {
			Justaclient.LOGGER.warn("Could not read {}, starting fresh", FILE, e);
		}
	}

	public static void save() {
		if (!loaded) {
			return;
		}
		dirtyAt = -1;
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("stats.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(data, writer);
			}
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	private static void dirty() {
		if (dirtyAt < 0) {
			dirtyAt = System.currentTimeMillis();
		}
	}

	public static void onJoin() {
		load();
		data.sessions++;
		sessionStart = System.currentTimeMillis();
		lastTick = sessionStart;
		sessionKills = 0;
		streak = 0;
		dirty();
	}

	public static void onLeave() {
		sessionStart = -1;
		lastTick = -1;
		save();
	}

	public static void onKill() {
		load();
		data.kills++;
		sessionKills++;
		streak++;
		data.bestSessionKills = Math.max(data.bestSessionKills, sessionKills);
		data.bestKillStreak = Math.max(data.bestKillStreak, streak);
		dirty();
	}

	public static void onDeath() {
		load();
		data.deaths++;
		streak = 0;
		dirty();
	}

	public static void onBlockBroken() {
		load();
		data.blocks++;
		dirty();
	}

	public static void onRareMined(boolean diamond) {
		load();
		if (diamond) {
			data.diamonds++;
		} else {
			data.debris++;
		}
		dirty();
	}

	public static void onMoved(double blocks) {
		load();
		data.distance += blocks;
	}

	/** Adds play time once a second while in a world (paused single player does not count) and saves a minute after a change. */
	public static void tick() {
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (sessionStart >= 0 && mc.player != null && !mc.isPaused()) {
			long elapsed = now - lastTick;
			if (elapsed >= 1000) {
				long seconds = elapsed / 1000;
				lastTick += seconds * 1000;
				data.playSeconds += seconds;
				data.days.merge(LocalDate.now().toString(), seconds, Long::sum);
				data.longestSessionSeconds = Math.max(data.longestSessionSeconds, (now - sessionStart) / 1000);
				trimDays();
				dirty();
			}
		} else {
			lastTick = now;
		}
		if (dirtyAt >= 0 && now - dirtyAt > 60_000) {
			save();
		}
	}

	private static void trimDays() {
		if (data.days.size() <= DAYS_KEPT) {
			return;
		}
		String oldest = LocalDate.now().minusDays(DAYS_KEPT).toString();
		data.days.keySet().removeIf(day -> day.compareTo(oldest) < 0);
	}

	/** Seconds played on {@code day}. */
	public static long secondsOn(LocalDate day) {
		return data().days.getOrDefault(day.toString(), 0L);
	}

	/** Clears every total and saves, from the Stats page's reset button. */
	public static void reset() {
		load();
		data = new Data();
		streak = 0;
		sessionKills = 0;
		save();
	}
}
