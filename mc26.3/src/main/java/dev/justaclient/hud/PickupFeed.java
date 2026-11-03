package dev.justaclient.hud;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Feeds the notification widget with pickups ("+12 Cobblestone") and an "Inventory full" toast when items lie at the
 * player's feet with nowhere to go. Both only use what the client already sees: the pickup packet and nearby items.
 */
public final class PickupFeed {
	/** How long an item has to sit in reach, unpicked, before it counts as "no room". */
	private static final long STUCK_NANOS = 1_200_000_000L;
	/** Don't repeat the toast for the same item more often than this. */
	private static final long REPEAT_NANOS = 8_000_000_000L;
	private static final Map<Integer, Long> firstSeen = new HashMap<>();
	private static final Map<Item, Long> lastWarned = new HashMap<>();

	private PickupFeed() {
	}

	/** The server says an entity was picked up. Only the local player's item pickups count. */
	public static void onTake(Entity taken, int playerId, int amount) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && playerId == mc.player.getId() && taken instanceof ItemEntity item) {
			Notifications.pickedUp(item.getItem(), amount);
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isSpectator()) {
			firstSeen.clear();
			return;
		}
		long now = System.nanoTime();
		AABB reach = player.getBoundingBox().inflate(1.0, 0.5, 1.0);
		Map<Integer, Long> still = new HashMap<>();
		for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, reach, e -> e.isAlive() && !e.getItem().isEmpty())) {
			long since = firstSeen.getOrDefault(item.getId(), now);
			still.put(item.getId(), since);
			ItemStack stack = item.getItem();
			if (now - since > STUCK_NANOS && !hasRoom(player.getInventory(), stack)
				&& now - lastWarned.getOrDefault(stack.getItem(), 0L) > REPEAT_NANOS) {
				lastWarned.put(stack.getItem(), now);
				Notifications.inventoryFull(stack);
			}
		}
		firstSeen.clear();
		firstSeen.putAll(still);
	}

	private static boolean hasRoom(Inventory inventory, ItemStack stack) {
		return inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0;
	}
}
