package dev.justaclient.hud;

import dev.justaclient.Justaclient;
import dev.justaclient.common.SessionLog;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.SessionHistoryFeature;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Writes one entry to {@code config/justaclient/sessions.json} when you leave a world: the Sessions tab of the Stats
 * page reads it. The inventory is sampled every few seconds while playing, so what you carried is known even after the
 * player is gone.
 */
public final class SessionHistory {
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("sessions.json");
	private static @Nullable List<SessionLog.Session> sessions;
	private static @Nullable String world;
	private static LocalDateTime started = LocalDateTime.now();
	private static List<SessionLog.Carried> carried = new ArrayList<>();
	private static int sampleTicks;

	private SessionHistory() {
	}

	public static List<SessionLog.Session> all() {
		if (sessions == null) {
			sessions = SessionLog.load(FILE);
		}
		return sessions;
	}

	public static void onJoin(Minecraft mc) {
		finish();
		world = worldName(mc);
		started = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		carried = new ArrayList<>();
		sampleTicks = 0;
	}

	public static void tick(Minecraft mc) {
		if (world == null || mc.player == null || ++sampleTicks < 100) {
			return;
		}
		sampleTicks = 0;
		carried = snapshot(mc);
	}

	/** Records the running session, if any. Called on leave and when the game closes. */
	public static void finish() {
		if (world == null) {
			return;
		}
		String name = world;
		world = null;
		SessionHistoryFeature feature = Features.get(SessionHistoryFeature.class);
		long seconds = SessionStats.playMillis() / 1000;
		if (!feature.isEnabled() || seconds < feature.minimumSeconds()) {
			return;
		}
		SessionLog.Session s = new SessionLog.Session();
		s.world = name;
		s.started = started.toString();
		s.seconds = seconds;
		s.kills = SessionStats.kills();
		s.deaths = SessionStats.deaths();
		s.blocks = SessionStats.blocks();
		s.distance = Math.round(SessionStats.distance());
		SessionStats.pickups().entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(12)
			.forEach(e -> s.pickedUp.put(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue()));
		if (feature.recordsCarried()) {
			s.carried = carried;
		}
		try {
			SessionLog.add(FILE, all(), s, feature.keep());
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save the session history", e);
		}
	}

	private static List<SessionLog.Carried> snapshot(Minecraft mc) {
		Map<Item, Integer> counts = new HashMap<>();
		for (ItemStack stack : mc.player.getInventory()) {
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		List<SessionLog.Carried> out = new ArrayList<>();
		counts.entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(16)
			.forEach(e -> out.add(new SessionLog.Carried(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue())));
		return out;
	}

	private static String worldName(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server != null) {
			return server.getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		return data != null ? (data.name == null || data.name.isBlank() ? data.ip : data.name) : "Unknown";
	}
}
