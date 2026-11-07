package dev.justaclient.hud;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Writes one entry to {@code config/justaclient/sessions.json} when you leave a world: the Sessions tab of the Stats
 * page reads it. The inventory is sampled every few seconds while playing, so what you carried is known even after the
 * player is gone.
 */
public final class SessionHistory {
	public static final Path FILE = PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("sessions.json");
	private static @Nullable List<SessionLog.Session> sessions;
	private static @Nullable String world;
	private static LocalDateTime started = LocalDateTime.now();
	private static List<SessionLog.Carried> carried = new ArrayList<>();
	private static List<SessionLog.Slot> slots = new ArrayList<>();
	private static String worldKey = "";
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
		slots = new ArrayList<>();
		worldKey = "";
		sampleTicks = 0;
	}

	public static void tick(Minecraft mc) {
		if (world == null || mc.player == null || ++sampleTicks < 100) {
			return;
		}
		sampleTicks = 0;
		carried = snapshot(mc);
		slots = slots(mc);
		if (worldKey.isEmpty()) {
			String key = dev.justaclient.waypoint.Waypoints.worldKey();
			worldKey = key == null ? "" : key;
		}
	}

	/** The world key of the world being played, once known; empty in menus. */
	public static String currentWorldKey() {
		return world == null ? "" : worldKey;
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
		s.worldKey = worldKey;
		if (feature.recordsCarried()) {
			s.carried = carried;
			s.slots = slots;
		}
		try {
			SessionLog.add(FILE, all(), s, feature.keep());
			if (!worldKey.isEmpty()) {
				// A copy of this world's sessions goes in the world's own folder too.
				com.google.gson.JsonArray mine = new com.google.gson.JsonArray();
				Gson gson = new Gson();
				for (SessionLog.Session one : all()) {
					if (worldKey.equals(one.worldKey)) {
						mine.add(gson.toJsonTree(one));
					}
				}
				dev.justaclient.world.WorldStore.write(worldKey, "sessions", mine);
			}
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save the session history", e);
		}
	}

	private static List<SessionLog.Carried> snapshot(Minecraft mc) {
		Map<Item, Integer> counts = new HashMap<>();
		for (ItemStack stack : java.util.stream.IntStream.range(0,mc.player.getInventory().getContainerSize()).mapToObj(mc.player.getInventory()::getItem).toList()) {
			if (!stack.isEmpty()) {
				counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		List<SessionLog.Carried> out = new ArrayList<>();
		counts.entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(36)
			.forEach(e -> out.add(new SessionLog.Carried(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue())));
		return out;
	}

	/** Every filled slot with its exact stack, so the history can show the inventory as it was. */
	private static List<SessionLog.Slot> slots(Minecraft mc) {
		List<SessionLog.Slot> out = new ArrayList<>();
		Inventory inventory = mc.player.getInventory();
		RegistryOps<JsonElement> ops = mc.level == null ? null : mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String json = null;
			if (ops != null && !stack.getComponentsPatch().isEmpty()) {
				json = ItemStack.CODEC.encodeStart(ops, stack).result().map(JsonElement::toString).orElse(null);
			}
			out.add(new SessionLog.Slot(i, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), json));
		}
		return out;
	}

	/** The stack a recorded slot held, with its components when a world is loaded to read them, else the plain item. */
	public static ItemStack stackOf(SessionLog.Slot slot) {
		Minecraft mc = Minecraft.getInstance();
		if (slot.stack != null && mc.level != null) {
			try {
				RegistryOps<JsonElement> ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
				ItemStack decoded = ItemStack.CODEC.parse(ops, JsonParser.parseString(slot.stack)).result().orElse(null);
				if (decoded != null) {
					return decoded;
				}
			} catch (RuntimeException e) {
				// Fall back to the plain item.
			}
		}
		net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(slot.item);
		Item item = id == null ? net.minecraft.world.item.Items.BARRIER : BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.BARRIER);
		return new ItemStack(item, Math.max(1, slot.count));
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
