package dev.justaclient.inventory;

import dev.justaclient.common.storage.StorageIndex;
import dev.justaclient.feature.impl.StorageMemoryFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import dev.justaclient.waypoint.Locate;
import dev.justaclient.waypoint.Waypoints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Ties the helpers to storage memory: when a feature finds you are missing something (ingredients for the potion you
 * picked, fuel for a furnace), it asks here. If a chest you opened holds it, a notification says where, and the GPS key
 * points the way. Nothing happens when you already carry enough or no remembered container has it.
 */
public final class StorageFinder {
	private static final long OFFER_MILLIS = 20_000L;
	private static Locate.@Nullable Target offer;
	private static long offerUntil;
	private static String offerLine = "";

	private StorageFinder() {
	}

	/** How many of an item you carry. */
	public static int carried(String id) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (!s.isEmpty() && InvItems.id(s.getItem()).equals(id)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** What you still need of {@code wanted} (item id to amount) after what you carry. */
	public static Map<String, Integer> missing(Map<String, Integer> wanted) {
		Map<String, Integer> out = new LinkedHashMap<>();
		wanted.forEach((id, n) -> {
			int short_ = n - carried(id);
			if (short_ > 0) {
				out.put(id, short_);
			}
		});
		return out;
	}

	/**
	 * Looks for any of {@code items} in the containers you opened in this world and offers the nearest one that has
	 * some. {@code why} starts the notification, such as "For Swiftness".
	 */
	public static void offer(List<String> items, String why) {
		StorageMemoryFeature storage = StorageMemoryFeature.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (items.isEmpty() || player == null || !storage.isEnabled() || !storage.suggests()) {
			return;
		}
		String dim = Waypoints.dimension();
		StorageIndex.Container best = null;
		StorageIndex.Container ender = null;
		double bestDist = Double.MAX_VALUE;
		for (StorageIndex.Container c : storage.index().all()) {
			if (items.stream().noneMatch(id -> c.count(id) > 0)) {
				continue;
			}
			if (c.isEnder()) {
				ender = c;
				continue;
			}
			if (!c.dimension.equals(dim)) {
				continue;
			}
			double d = player.distanceToSqr(c.x + 0.5, c.y + 0.5, c.z + 0.5);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		StorageIndex.Container found = best != null ? best : ender;
		if (found == null) {
			return;
		}
		List<String> names = new ArrayList<>();
		for (String id : items) {
			if (found.count(id) > 0) {
				names.add(InvItems.itemName(id));
			}
		}
		String what = String.join(", ", names.subList(0, Math.min(3, names.size()))) + (names.size() > 3 ? "..." : "");
		if (found.isEnder()) {
			Notifications.push(Icons.PACKAGE_SEARCH, why + ": " + what, "in your ender chest");
			return;
		}
		String where = InvItems.itemName(found.block) + " · " + Math.round(Math.sqrt(bestDist)) + " m";
		offer = StorageMemoryFeature.target(found, what + " · " + InvItems.itemName(found.block));
		offerUntil = System.currentTimeMillis() + OFFER_MILLIS;
		offerLine = why + ": " + what + " · " + where;
		Notifications.push(Icons.PACKAGE_SEARCH, why + ": " + what, "in " + where + " · " + storage.gpsKeyName() + " for GPS");
	}

	/** Called when the GPS key is pressed: takes up the latest offer if it is still fresh. */
	public static boolean accept() {
		Locate.Target t = offer;
		if (t == null || System.currentTimeMillis() > offerUntil) {
			offer = null;
			return false;
		}
		offer = null;
		Locate.track(t);
		Locate.highlight(t, 45f);
		Notifications.push(Icons.NAVIGATION, "GPS set", t.label());
		return true;
	}

	/**
	 * The open offer in one line ("To repair Iron Pickaxe: Iron Ingot · Chest · 5 m"), or null. Container screens show
	 * it under the container, since panels there can cover the notification.
	 */
	public static @Nullable String offerLine() {
		return hasOffer() ? offerLine : null;
	}

	public static boolean hasOffer() {
		return offer != null && System.currentTimeMillis() <= offerUntil;
	}
}
