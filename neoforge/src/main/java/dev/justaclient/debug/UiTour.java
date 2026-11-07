package dev.justaclient.debug;

import dev.justaclient.Justaclient;
import dev.justaclient.feature.Category;
import dev.justaclient.ui.ClickGuiScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A developer tool for checking layouts: opens every client screen in turn at each GUI scale and saves a screenshot of
 * each one. Start the game with {@code -Djustaclient.uiTour=<folder>}; {@code -Djustaclient.uiTourScales=2,3,4} picks
 * the scales and {@code -Djustaclient.uiTourOnly=mods,servers} limits the screens. Screens that need a world are taken
 * only once a world is open, so launch with {@code --quickPlaySingleplayer <world>} to get all of them. The game quits
 * when the tour is done.
 */
public final class UiTour {
	private static final long SETTLE_MS = Long.getLong("justaclient.uiTourWait", 1800L);

	private record Stop(String name, boolean world, Supplier<@Nullable Screen> screen) {
	}

	private static final List<Stop> STOPS = new ArrayList<>();
	private static @Nullable Path out;
	private static int[] scales = {2, 3, 4};
	private static int scaleIndex;
	private static int stopIndex = -1;
	private static long due;
	private static boolean started;
	private static long readySince;
	private static int originalScale;

	private UiTour() {
	}

	public static boolean active() {
		return out != null;
	}

	/** Reads the launch flags; called once at client start. */
	public static void init() {
		String dir = System.getProperty("justaclient.uiTour");
		if (dir == null || dir.isBlank()) {
			return;
		}
		out = Path.of(dir).toAbsolutePath();
		String s = System.getProperty("justaclient.uiTourScales");
		if (s != null && !s.isBlank()) {
			scales = Arrays.stream(s.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
		}
		buildStops();
		String only = System.getProperty("justaclient.uiTourOnly");
		if (only != null && !only.isBlank()) {
			Set<String> keep = Arrays.stream(only.split(",")).map(String::trim).collect(Collectors.toSet());
			STOPS.removeIf(stop -> keep.stream().noneMatch(k -> stop.name.equals(k) || stop.name.startsWith(k + "-")));
		}
		Justaclient.LOGGER.info("UI tour: {} screens at scales {} into {}", STOPS.size(), Arrays.toString(scales), out);
	}

	private static void buildStops() {
		Minecraft mc = Minecraft.getInstance();
		STOPS.add(new Stop("title", false, dev.justaclient.ui.JacTitleScreen::new));
		STOPS.add(new Stop("menu-theme", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("theme"))));
		STOPS.add(new Stop("menu-performance", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("performance"))));
		STOPS.add(new Stop("menu-stats", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("stats"))));
		STOPS.add(new Stop("menu-notes", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("notes"))));
		STOPS.add(new Stop("menu-feedback", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("feedback"))));
		STOPS.add(new Stop("menu-hub", false, () -> ClickGuiScreen.tourAt(null, ClickGuiScreen.tourPage("hub"))));
		for (Category c : Category.values()) {
			STOPS.add(new Stop("menu-" + c.name().toLowerCase(java.util.Locale.ROOT), false, () -> ClickGuiScreen.tourCategory(null, c)));
		}
		STOPS.add(new Stop("ring", false, () -> new dev.justaclient.ui.RingMenuScreen(null)));
		STOPS.add(new Stop("mods", false, () -> new dev.justaclient.ui.ModsScreen(null)));
		STOPS.add(new Stop("modrinth", false, () -> new dev.justaclient.ui.ModrinthScreen(null)));
		STOPS.add(new Stop("servers", false, () -> new dev.justaclient.ui.ServersScreen(null)));
		STOPS.add(new Stop("keybinds", false, () -> new dev.justaclient.ui.KeybindsScreen(null)));
		STOPS.add(new Stop("screenshots", false, () -> new dev.justaclient.ui.ScreenshotsScreen(null)));
		STOPS.add(new Stop("devconsole", false, () -> new dev.justaclient.ui.DevConsoleScreen(null)));
		STOPS.add(new Stop("worldarchive", false, () -> new dev.justaclient.ui.WorldArchiveScreen(null)));
		STOPS.add(new Stop("potions", false, () -> new dev.justaclient.ui.PotionGuideScreen(null)));
		STOPS.add(new Stop("labs", false, () -> new dev.justaclient.ui.LabsScreen(null)));
		STOPS.add(new Stop("biomes", false, () -> new dev.justaclient.ui.BiomeEffectsScreen(null)));
		STOPS.add(new Stop("messages", false, () -> new dev.justaclient.ui.MessagesScreen(null, null)));
		STOPS.add(new Stop("confirm", false, () -> new dev.justaclient.ui.JacConfirmScreen(null, dev.justaclient.render.Icons.TRASH, "Delete this world?",
			"It will be gone for good, along with its waypoints, map and screenshots index.", "Delete", () -> {
			})));
		STOPS.add(new Stop("vanilla-options", false, () -> new net.minecraft.client.gui.screens.options.OptionsScreen(null, mc.options)));
		STOPS.add(new Stop("vanilla-worlds", false, () -> new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(null)));
		STOPS.add(new Stop("vanilla-multiplayer", false, () -> new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(null)));
		// Screens that need a world.
		STOPS.add(new Stop("hud", true, () -> null));
		STOPS.add(new Stop("hudeditor", true, () -> new dev.justaclient.ui.HudEditorScreen(null)));
		STOPS.add(new Stop("map", true, dev.justaclient.ui.WorldMapScreen::new));
		STOPS.add(new Stop("landmarks", true, () -> new dev.justaclient.ui.LandmarksScreen(null)));
		STOPS.add(new Stop("kits", true, () -> new dev.justaclient.ui.KitsScreen(null)));
		STOPS.add(new Stop("storage", true, () -> new dev.justaclient.ui.StorageScreen(null)));
		STOPS.add(new Stop("chatlog", true, () -> new dev.justaclient.ui.ChatLogScreen(null, "chat")));
		STOPS.add(new Stop("deathrecap", true, () -> new dev.justaclient.ui.DeathRecapScreen(null)));
		STOPS.add(new Stop("pause", true, () -> new net.minecraft.client.gui.screens.PauseScreen(true)));
		STOPS.add(new Stop("chat", true, () -> new net.minecraft.client.gui.screens.ChatScreen("")));
		STOPS.add(new Stop("inventory", true, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
		STOPS.add(new Stop("crafting", true, () -> new net.minecraft.client.gui.screens.inventory.CraftingScreen(
			new net.minecraft.world.inventory.CraftingMenu(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Crafting"))));
		STOPS.add(new Stop("furnace", true, () -> new net.minecraft.client.gui.screens.inventory.FurnaceScreen(
			new net.minecraft.world.inventory.FurnaceMenu(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Furnace"))));
		STOPS.add(new Stop("brewing", true, () -> new net.minecraft.client.gui.screens.inventory.BrewingStandScreen(
			new net.minecraft.world.inventory.BrewingStandMenu(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Brewing Stand"))));
		STOPS.add(new Stop("anvil", true, () -> new net.minecraft.client.gui.screens.inventory.AnvilScreen(
			new net.minecraft.world.inventory.AnvilMenu(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Repair & Name"))));
		STOPS.add(new Stop("chest", true, () -> new net.minecraft.client.gui.screens.inventory.ContainerScreen(
			net.minecraft.world.inventory.ChestMenu.sixRows(0, mc.player.getInventory()), mc.player.getInventory(), Component.literal("Large Chest"))));
	}

	/** Runs at the end of every client tick. */
	public static void tick(Minecraft mc) {
        if (!NativeSmoke.ready()) return;
		if (out == null) {
			return;
		}
		if (!started) {
			// Wait for the title screen (or the world, when launched straight into one) before starting.
			if (mc.getOverlay() != null || mc.screen == null && mc.level == null) {
				return;
			}
			if (mc.level != null && mc.player == null) {
				return;
			}
			if (readySince == 0) {
				// Let the loading overlay finish fading out.
				readySince = System.currentTimeMillis();
			}
			if (System.currentTimeMillis() - readySince < 4000) {
				return;
			}
			started = true;
			originalScale = mc.options.guiScale().get();
			applyScale(mc);
			next(mc);
			return;
		}
		if (System.currentTimeMillis() < due) {
			return;
		}
		Stop stop = STOPS.get(stopIndex);
		String name = "scale" + scales[scaleIndex] + "-" + stop.name + ".png";
		try {
			Files.createDirectories(out);
			Screenshot.grab(out.toFile(), name, mc.getMainRenderTarget(), msg -> {
			});
		} catch (Exception e) {
			Justaclient.LOGGER.warn("UI tour: could not save {}", name, e);
		}
		next(mc);
	}

	private static void next(Minecraft mc) {
		while (true) {
			stopIndex++;
			if (stopIndex >= STOPS.size()) {
				stopIndex = 0;
				scaleIndex++;
				if (scaleIndex >= scales.length) {
					finish(mc);
					return;
				}
				applyScale(mc);
			}
			Stop stop = STOPS.get(stopIndex);
			if (stop.world && mc.level == null) {
				continue;
			}
			if (!stop.world && mc.level != null && stop.name.equals("title")) {
				continue;
			}
			Screen screen;
			try {
				screen = stop.screen.get();
			} catch (Throwable t) {
				Justaclient.LOGGER.warn("UI tour: could not open {}", stop.name, t);
				continue;
			}
			if (screen == null && mc.level == null) {
				continue;
			}
			mc.setScreen(screen);
			due = System.currentTimeMillis() + SETTLE_MS;
			Justaclient.LOGGER.info("UI tour: scale {} {}", scales[scaleIndex], stop.name);
			return;
		}
	}

	private static void applyScale(Minecraft mc) {
		mc.options.guiScale().set(scales[scaleIndex]);
		mc.resizeDisplay();
	}

	private static void finish(Minecraft mc) {
		Justaclient.LOGGER.info("UI tour: done, screenshots in {}", out);
		mc.options.guiScale().set(originalScale);
		mc.options.save();
		out = null;
		mc.stop();
	}

	/** Lets tests list the stop names. */
	static Map<String, Boolean> stops() {
		Map<String, Boolean> m = new LinkedHashMap<>();
		STOPS.forEach(s -> m.put(s.name, s.world));
		return m;
	}
}
