package dev.justaclient.feature.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.justaclient.Justaclient;
import dev.justaclient.common.kits.Kit;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.inventory.ContainerOverlays;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.inventory.StorageFinder;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Kits: save your inventory layout ({@code /ck save}, adjusted in an editor first), look at it ({@code /ck preview}),
 * and use it ({@code /ck load}). In singleplayer with cheats on the kit is given straight into its slots; anywhere else
 * a check over your inventory marks each slot green when it holds the right item, and storage memory says which chest
 * has what is missing.
 */
public final class KitsFeature extends Feature {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("kits.json");
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;
	private static final int RED = 0xFFF87171;

	private final BoolSetting check = bool("check", "Check grid over your inventory", true);
	private final TextSetting active = add(new TextSetting("active", "Checking", "", Kit.MAX_NAME)).visibleWhen(() -> false);
	private @Nullable List<Kit> kits;

	public KitsFeature() {
		super("kits", "Kits", "Save inventory layouts and refill them: given in singleplayer, checked slot by slot anywhere",
			Category.INVENTORY, Icons.BACKPACK, true);
	}

	public static KitsFeature get() {
		return Features.get(KitsFeature.class);
	}

	public static void init() {
		ContainerOverlays.register(get().new Checker());
	}

	public List<Kit> kits() {
		if (kits == null) {
			kits = new ArrayList<>();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					kits.addAll(Kit.fromJson(JsonParser.parseReader(reader)));
				} catch (IOException | RuntimeException e) {
					Justaclient.LOGGER.warn("Could not read {}", FILE, e);
				}
			}
		}
		return kits;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("kits.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				writer.write(Kit.toJson(kits()).toString());
			}
			Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	/** Saves or replaces a kit by name. */
	public void put(Kit kit) {
		Kit old = Kit.find(kits(), kit.name);
		if (old != null) {
			kits().set(kits().indexOf(old), kit);
		} else {
			kits().add(kit);
		}
		save();
	}

	public void delete(Kit kit) {
		kits().remove(kit);
		if (kit.name.equalsIgnoreCase(active.get())) {
			active.set("");
		}
		save();
	}

	/** The kit being checked over your inventory, or null. */
	public @Nullable Kit checking() {
		String name = active.get();
		return name.isEmpty() ? null : Kit.find(kits(), name);
	}

	public void stopChecking() {
		active.set("");
		dev.justaclient.profile.Profiles.save();
	}

	/** Your inventory now, as a kit called {@code name}. Empty slots are left out. */
	public static Kit capture(String name) {
		Minecraft mc = Minecraft.getInstance();
		List<Kit.Slot> slots = new ArrayList<>();
		if (mc.player != null && mc.level != null) {
			var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			for (int i = 0; i < Kit.SLOTS; i++) {
				ItemStack s = mc.player.getInventory().getItem(i);
				if (s.isEmpty()) {
					continue;
				}
				JsonElement json = ItemStack.CODEC.encodeStart(ops, s).result().orElse(null);
				slots.add(new Kit.Slot(i, InvItems.id(s.getItem()), s.getCount(), s.getHoverName().getString(), json));
			}
		}
		return new Kit(name, System.currentTimeMillis(), slots);
	}

	/** The item a kit slot holds, for drawing; falls back to the plain item if its saved data can't be read. */
	public static ItemStack stack(Kit.Slot s) {
		Minecraft mc = Minecraft.getInstance();
		if (s.stack() != null && mc.level != null) {
			var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			ItemStack read = ItemStack.CODEC.parse(ops, s.stack()).result().orElse(null);
			if (read != null) {
				return read;
			}
		}
		ItemStack plain = InvItems.stack(s.id());
		plain.setCount(Math.max(1, Math.min(s.count(), plain.getMaxStackSize())));
		return plain;
	}

	/**
	 * Uses a kit: given in singleplayer when cheats are on, otherwise checked over your inventory. Either way, storage
	 * memory is asked about anything you're missing.
	 */
	public void use(Kit kit) {
		Minecraft mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		if (server != null && server.getWorldData().isAllowCommands() && mc.player != null) {
			give(server, mc.player.getUUID(), kit);
			Notifications.push(Icons.BACKPACK, "Kit given", kit.name);
			return;
		}
		check(kit, server != null);
	}

	/** Checks a kit over your inventory (never gives it), and asks storage memory about what's missing. */
	public void check(Kit kit, boolean noCheats) {
		Minecraft mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		active.set(kit.name);
		dev.justaclient.profile.Profiles.save();
		Notifications.push(Icons.BACKPACK, "Checking kit " + kit.name, noCheats ? "Giving kits needs cheats on; open your inventory to check"
			: "Open your inventory to see each slot");
		offerMissing(kit);
	}

	/** Asks storage memory about kit items you don't carry. */
	public static void offerMissing(Kit kit) {
		Map<String, Integer> need = new LinkedHashMap<>();
		for (Kit.Slot s : kit.slots) {
			need.merge(s.id(), s.count(), Integer::sum);
		}
		StorageFinder.offer(List.copyOf(StorageFinder.missing(need).keySet()), "For " + kit.name);
	}

	/**
	 * Singleplayer only: puts each kit item in its slot on your own world. Whatever was in those slots moves to a free
	 * slot the kit doesn't use, or drops at your feet if there is none, so nothing is lost.
	 */
	private static void give(net.minecraft.server.MinecraftServer server, java.util.UUID id, Kit kit) {
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				return;
			}
			var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
			var inv = player.getInventory();
			for (Kit.Slot s : kit.slots) {
				ItemStack want = s.stack() == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, s.stack()).result().orElse(ItemStack.EMPTY);
				if (want.isEmpty()) {
					var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(s.id()));
					want = new ItemStack(item, s.count());
				}
				ItemStack there = inv.getItem(s.slot());
				if (!there.isEmpty()) {
					int free = -1;
					for (int i = 0; i < 36 && free < 0; i++) {
						if (inv.getItem(i).isEmpty() && kit.at(i) == null) {
							free = i;
						}
					}
					if (free >= 0) {
						inv.setItem(free, there);
					} else {
						player.drop(there, false);
					}
				}
				inv.setItem(s.slot(), want);
			}
			player.containerMenu.broadcastChanges();
			player.inventoryMenu.broadcastChanges();
		});
	}

	/** The check: a small panel beside any screen showing your inventory, and a mark on each kit slot. */
	private final class Checker implements ContainerOverlays.Overlay {
		@Override
		public boolean applies(AbstractContainerScreen<?> screen) {
			return isEnabled() && check.on() && checking() != null && Minecraft.getInstance().player != null;
		}

		@Override
		public float width(ContainerOverlays.Box box) {
			return 130;
		}

		@Override
		public float height(ContainerOverlays.Box box) {
			return 62;
		}

		private int[] tally(ContainerOverlays.Box box, Kit kit) {
			LocalPlayer player = Minecraft.getInstance().player;
			int right = 0;
			int total = 0;
			if (player == null) {
				return new int[] {0, 0};
			}
			for (Kit.Slot s : kit.slots) {
				ItemStack have = player.getInventory().getItem(s.slot());
				total++;
				if (Kit.check(s, have.isEmpty() ? "" : InvItems.id(have.getItem()), have.getCount()) == Kit.State.RIGHT) {
					right++;
				}
			}
			return new int[] {right, total};
		}

		@Override
		public void draw(ContainerOverlays.Box box, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			Kit kit = checking();
			if (kit == null) {
				return;
			}
			float ly = ContainerOverlays.header(g, Icons.BACKPACK, "Kit " + kit.name, x, y, w);
			int[] t = tally(box, kit);
			ly = ContainerOverlays.row(g, "Right", t[0] + " of " + t[1], t[0] == t[1] ? GREEN : Theme.TEXT, x, ly, w);
			ContainerOverlays.bar(g, t[1] == 0 ? 0 : t[0] / (float) t[1], t[0] == t[1] ? GREEN : AMBER, x, ly, w);
			float by = y + h - 18;
			boolean hot = mx >= x + 6 && mx < x + w - 6 && my >= by && my < by + 13;
			Draw.rect(g, x + 6, by, w - 12, 13, 4f, hot ? Theme.CONTROL_HOVER : Theme.CONTROL);
			String label = t[0] == t[1] ? "Done: stop checking" : "Stop checking";
			JacText.draw(g, label, x + (w - JacText.width(label, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD)) / 2f, by + 3.5f, Theme.TEXT,
				Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		}

		@Override
		public boolean click(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, int button) {
			float by = y + h - 18;
			if (my >= by && my < by + 13) {
				GuiSoundsFeature.menuClick();
				stopChecking();
				return true;
			}
			return false;
		}

		@Override
		public void decorate(ContainerOverlays.Box box, GuiGraphics g, double mx, double my) {
			Kit kit = checking();
			LocalPlayer player = Minecraft.getInstance().player;
			if (kit == null || player == null) {
				return;
			}
			for (Slot slot : box.screen().getMenu().slots) {
				if (slot.container != player.getInventory()) {
					continue;
				}
				Kit.Slot want = kit.at(slot.getContainerSlot());
				if (want == null) {
					continue;
				}
				ItemStack have = slot.getItem();
				Kit.State state = Kit.check(want, have.isEmpty() ? "" : InvItems.id(have.getItem()), have.getCount());
				if (state == Kit.State.EMPTY) {
					// A faded picture of what belongs here.
					InvItems.draw(g, stack(want), box.left() + slot.x, box.top() + slot.y, 16);
					Draw.rect(g, box.left() + slot.x, box.top() + slot.y, 16, 16, 0f, 0xA08B8B8B);
					Draw.outline(g, box.left() + slot.x - 1, box.top() + slot.y - 1, 18, 18, 2f, 1f, RED);
				} else {
					box.outline(g, slot, state == Kit.State.RIGHT ? GREEN : state == Kit.State.SHORT ? AMBER : RED);
				}
			}
		}
	}
}
