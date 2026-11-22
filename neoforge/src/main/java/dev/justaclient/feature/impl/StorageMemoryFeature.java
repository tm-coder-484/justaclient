package dev.justaclient.feature.impl;

import dev.justaclient.common.storage.StorageIndex;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import dev.justaclient.waypoint.Locate;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.world.WorldStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.justaclient.platform.events.ItemTooltipCallback;
import dev.justaclient.platform.events.ScreenEvents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Remembers what was in the chests, barrels, shulker boxes, hoppers and dispensers you opened, per world or server, plus
 * your ender chest. {@code /storage} searches it by item or amount and points the GPS at the container, which is outlined
 * when you get close. Only containers you opened yourself are known, as they were when you closed them.
 */
public final class StorageMemoryFeature extends Feature {
	public final BoolSetting outline = bool("outline", "Outline the container you look for", true);
	public final BoolSetting throughWalls = bool("through_walls", "Show the outline through walls", true).visibleWhen(outline::on);
	public final NumberSetting range = number("range", "Outline range", 96, 16, 512, 16, v -> String.format("%.0f m", v)).visibleWhen(outline::on);
	private final BoolSetting ender = bool("ender", "Remember your ender chest", true);
	private final BoolSetting forget = bool("forget", "Forget containers that are gone", true);
	private final BoolSetting shulkerTooltip = bool("shulker_tooltip", "Shulker box fill in tooltips", true);
	private final BoolSetting suggest = bool("suggest", "Say which chest has what you're missing", true);
	private final dev.justaclient.feature.setting.KeySetting gpsKey = key("gps_key", "GPS to the suggested chest",
		com.mojang.blaze3d.platform.InputConstants.KEY_G).visibleWhen(suggest::on);
	private boolean gpsWasDown;

	private StorageIndex index = new StorageIndex();
	private @Nullable String indexWorld;
	private @Nullable BlockPos clicked;
	private long clickedAt;
	private int ticks;

	public StorageMemoryFeature() {
		super("storage_memory", "Storage memory", "Remembers what's in the containers you open; search them and find your way back",
			Category.INVENTORY, Icons.PACKAGE_SEARCH, true);
	}

	public static StorageMemoryFeature get() {
		return Features.get(StorageMemoryFeature.class);
	}

	public static void init() {
		StorageMemoryFeature feature = get();
		WorldFx.layer(feature, f -> {
			if (feature.outline.on() && !feature.throughWalls.on()) {
				Locate.render(f, feature.range.get());
			}
		});
		WorldFx.xrayLayer(feature, f -> {
			if (feature.outline.on() && feature.throughWalls.on()) {
				Locate.render(f, feature.range.get());
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            var level=event.getLevel(); var hit=event.getHitVec();
			if (level.isClientSide()) {
				feature.clicked = hit.getBlockPos();
				feature.clickedAt = System.currentTimeMillis();
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!feature.isEnabled() || !(screen instanceof AbstractContainerScreen<?> acs)) {
				return;
			}
			if (!remembers(acs.getMenu())) {
				// A furnace or the like used the click; a server menu opened by command a moment later must not take it.
				feature.clicked = null;
				return;
			}
			Opened opened = feature.opened();
			if (opened != null && fits(acs.getMenu(), opened)) {
				ScreenEvents.remove(screen).register(s -> feature.snapshot(acs.getMenu(), opened));
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!feature.isEnabled() || !feature.shulkerTooltip.on() || lines.isEmpty() || !InvItems.id(stack.getItem()).endsWith("shulker_box")) {
				return;
			}
			List<ItemStack> inside = InvCompat.contents(stack);
			int items = inside.stream().mapToInt(ItemStack::getCount).sum();
			lines.add(Math.min(1, lines.size()), Component.literal(inside.size() + "/27 slots used · " + items + (items == 1 ? " item" : " items"))
				.withStyle(ChatFormatting.GRAY));
		});
	}

	/** A chest menu of the wrong size for the block (a server's own menu, a shop plugin) is not that chest's contents. */
	private static boolean fits(AbstractContainerMenu menu, Opened opened) {
		// Servers often give the ender chest more rows, so any size counts there.
		if (!(menu instanceof ChestMenu chest) || StorageIndex.ENDER.equals(opened.kind())) {
			return true;
		}
		return chest.getRowCount() * 9 == (opened.other() != null ? 54 : 27);
	}

	private static boolean remembers(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof DispenserMenu || menu instanceof HopperMenu;
	}

	/** Where the container that just opened is: the block you used a moment ago. */
	private record Opened(String kind, BlockPos pos, @Nullable BlockPos other, String block, String dimension) {
	}

	private @Nullable Opened opened() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || clicked == null || System.currentTimeMillis() - clickedAt > 3000) {
			return null;
		}
		BlockPos pos = clicked;
		clicked = null;
		BlockState state = mc.level.getBlockState(pos);
		String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (state.getBlock() instanceof EnderChestBlock) {
			return ender.on() ? new Opened(StorageIndex.ENDER, BlockPos.ZERO, null, block, "") : null;
		}
		BlockEntity be = mc.level.getBlockEntity(pos);
		if (!(be instanceof net.minecraft.world.Container)) {
			return null;
		}
		BlockPos other = null;
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			other = pos.relative(ChestBlock.getConnectedDirection(state));
			// Both halves of a double chest are one entry, kept under the lower corner.
			if (other.compareTo(pos) < 0) {
				BlockPos swap = pos;
				pos = other;
				other = swap;
			}
		}
		return new Opened("block", pos, other, block, Waypoints.dimension());
	}

	private void snapshot(AbstractContainerMenu menu, Opened at) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !isEnabled()) {
			return;
		}
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, ItemStack> first = new LinkedHashMap<>();
		Map<String, List<StorageIndex.Item>> inside = new LinkedHashMap<>();
		int slots = 0;
		int used = 0;
		for (Slot slot : menu.slots) {
			if (slot.container == mc.player.getInventory()) {
				continue;
			}
			slots++;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			used++;
			String name = stack.getHoverName().getString();
			String id = InvItems.id(stack.getItem());
			List<ItemStack> contents = InvCompat.contents(stack);
			if (!contents.isEmpty()) {
				// Each filled shulker box is its own entry so its contents stay with it.
				String key = id + "#" + slot.index;
				List<StorageIndex.Item> in = new ArrayList<>();
				for (ItemStack c : contents) {
					in.add(new StorageIndex.Item(InvItems.id(c.getItem()), c.getCount(), c.getHoverName().getString()));
				}
				counts.put(key, new int[] {stack.getCount()});
				first.put(key, stack);
				inside.put(key, in);
				continue;
			}
			String key = id + "|" + name;
			counts.computeIfAbsent(key, k -> new int[1])[0] += stack.getCount();
			first.putIfAbsent(key, stack);
		}
		List<StorageIndex.Item> items = new ArrayList<>();
		counts.forEach((key, n) -> {
			ItemStack s = first.get(key);
			items.add(new StorageIndex.Item(InvItems.id(s.getItem()), n[0], s.getHoverName().getString(), inside.getOrDefault(key, List.of())));
		});
		StorageIndex.Container c = new StorageIndex.Container(at.kind, at.dimension, at.pos.getX(), at.pos.getY(), at.pos.getZ(), at.block, slots, used,
			items, System.currentTimeMillis());
		if (at.other != null) {
			c.other = new int[] {at.other.getX(), at.other.getY(), at.other.getZ()};
		}
		StorageIndex idx = index();
		idx.put(c);
		save();
	}

	/** The index of the world you are in, loaded on first use. */
	public StorageIndex index() {
		String world = Waypoints.worldKey();
		if (world == null) {
			return new StorageIndex();
		}
		if (!world.equals(indexWorld)) {
			indexWorld = world;
			index = StorageIndex.fromJson(WorldStore.read("storage"));
		}
		return index;
	}

	public void save() {
		if (indexWorld != null && indexWorld.equals(Waypoints.worldKey())) {
			WorldStore.write("storage", index.toJson());
		}
	}

	public void forget(StorageIndex.Container c) {
		if (index().remove(c.key())) {
			save();
		}
	}

	/** A GPS and outline target for a remembered container, covering both halves of a double chest. */
	public static Locate.Target target(StorageIndex.Container c, String label) {
		int[] o = c.other;
		int color = dev.justaclient.config.JacConfig.accent();
		return o == null ? new Locate.Target(label, Icons.PACKAGE, c.x, c.y, c.z, c.dimension, color)
			: new Locate.Target(label, Icons.PACKAGE, c.x, c.y, c.z, c.dimension, color, o[0], o[1], o[2]);
	}

	/** True if helpers such as the potion guide and furnace info may point you at a chest. */
	public boolean suggests() {
		return suggest.on();
	}

	public String gpsKeyName() {
		return gpsKey.isBound() ? gpsKey.keyName() : "the GPS key (unbound)";
	}

	/** Containers seen in place in {@link #seenLevel}, so only those can be forgotten there (see {@link #tick}). */
	private final java.util.Set<String> seen = new java.util.HashSet<>();
	private @Nullable Object seenLevel;

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		// The GPS key works in the world and in container screens, not where you type.
		boolean down = (mc.screen == null || mc.screen instanceof AbstractContainerScreen<?>) && gpsKey.isBound() && gpsKey.isDown();
		if (down && !gpsWasDown && dev.justaclient.inventory.StorageFinder.hasOffer()) {
			dev.justaclient.inventory.StorageFinder.accept();
		}
		gpsWasDown = down;
		if (mc.level == null || !forget.on() || ++ticks % 40 != 0) {
			return;
		}
		// A container whose block is loaded but no longer holds one was broken or moved: drop it. On a server it must first
		// have been seen there in this world: behind a proxy every backend (lobby, survival) shares one address, so the same
		// spot in another backend's overworld says nothing about your chest.
		if (seenLevel != mc.level) {
			seenLevel = mc.level;
			seen.clear();
		}
		boolean strict = !mc.isLocalServer();
		String dim = Waypoints.dimension();
		boolean changed = false;
		for (StorageIndex.Container c : new ArrayList<>(index().all())) {
			if (c.isEnder() || !c.dimension.equals(dim)) {
				continue;
			}
			BlockPos pos = new BlockPos(c.x, c.y, c.z);
			if (!mc.level.isLoaded(pos)) {
				continue;
			}
			boolean there = mc.level.getBlockEntity(pos) instanceof net.minecraft.world.Container
				&& BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString().equals(c.block);
			if (there) {
				seen.add(c.key());
			} else if (!strict || seen.contains(c.key())) {
				changed |= index.remove(c.key());
			}
		}
		if (changed) {
			save();
		}
	}
}
