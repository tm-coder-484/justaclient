package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.HudEditorScreen;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.RingMenuScreen;
import dev.justaclient.ui.WorldMapScreen;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jspecify.annotations.Nullable;

/**
 * Spatial menus: menus float in the world in front of you on a panel you can lean back, turn and curve, instead of lying
 * flat on the window. Off by default; one switch turns every menu spatial, and kinds of screens (or single screens by
 * name) can stay flat. Visual only: the menu, its buttons and the mouse work exactly as before.
 */
public final class SpatialMenusFeature extends Feature {
	public final NumberSetting distance = number("distance", "Distance", 2.4, 1.0, 4.0, 0.1, NumberSetting.decimal(" blocks"));
	public final NumberSetting size = number("size", "Size", 0.8, 0.5, 1.3, 0.05, NumberSetting.percent());
	public final NumberSetting lean = number("lean", "Lean back", 6, -25, 25, 1, NumberSetting.unit("°"));
	public final NumberSetting turn = number("turn", "Turn", 0, -35, 35, 1, NumberSetting.unit("°"));
	public final NumberSetting bend = number("bend", "Curve", 0.25, 0, 1, 0.05, NumberSetting.percent());
	public final NumberSetting lift = number("lift", "Height", 0, -1, 1, 0.05, NumberSetting.decimal(" blocks"));
	public final BoolSetting followMouse = bool("follow_mouse", "Tilt away from the mouse", true);
	public final BoolSetting drift = bool("drift", "Float gently", true);
	public final BoolSetting anchor = bool("anchor", "Stay where it opened (instead of in front of the camera)", false);
	public final BoolSetting flyIn = bool("fly_in", "Fly in when opening", true);
	public final NumberSetting dim = number("dim", "Dim the world behind", 0.35, 0, 0.8, 0.05, NumberSetting.percent());
	public final BoolSetting blur = bool("blur", "Blur the world behind", false);
	private final BoolSetting clientMenus = bool("client_menus", "Spatial: settings, panels and hubs", true);
	private final BoolSetting ring = bool("ring", "Spatial: ring menu", true);
	private final BoolSetting containers = bool("containers", "Spatial: inventory and containers", true);
	private final BoolSetting game = bool("game", "Spatial: pause menu, options and other game screens", true);
	private final BoolSetting chat = bool("chat", "Spatial: chat", false);
	private final BoolSetting map = bool("map", "Spatial: world map", false);
	private final BoolSetting death = bool("death", "Spatial: death screen", true);
	private final BoolSetting others = bool("others", "Spatial: other mods' screens", false);
	private final ListSetting flat = add(new ListSetting("flat", "Keep these screens flat", List.of(), 100, 96, "Screen class name"));

	public SpatialMenusFeature() {
		super("spatial_menus", "Spatial menus", "Menus float in the world in front of you, leaned back and curved, instead of lying flat",
			Category.SCREENS, Icons.BOX, false);
	}

	public static SpatialMenusFeature get() {
		return Features.get(SpatialMenusFeature.class);
	}

	/** True if {@code screen} should float right now: the feature is on, a world is showing and the screen is not kept flat. */
	public boolean wants(@Nullable Screen screen) {
		if (!isEnabled() || screen == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return false;
		}
		// The HUD editor places things on the real window, so it always stays flat.
		if (screen instanceof HudEditorScreen || keptFlat(screen.getClass())) {
			return false;
		}
		// Loading, connecting and message screens cover the world on purpose; they always stay flat.
		if (screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen || screen instanceof GenericMessageScreen
			|| screen instanceof GenericWaitingScreen || screen instanceof ConnectScreen || screen instanceof DisconnectedScreen) {
			return false;
		}
		if (screen instanceof RingMenuScreen) {
			return ring.on();
		}
		if (screen instanceof WorldMapScreen) {
			return map.on();
		}
		if (screen instanceof ClickGuiScreen || screen instanceof JacScreen) {
			return clientMenus.on();
		}
		if (screen instanceof ChatScreen) {
			return chat.on();
		}
		if (screen instanceof DeathScreen) {
			return death.on();
		}
		if (screen instanceof AbstractContainerScreen<?>) {
			return containers.on();
		}
		String name = screen.getClass().getName();
		if (name.startsWith("dev.justaclient.")) {
			return clientMenus.on();
		}
		if (name.startsWith("net.minecraft.")) {
			return game.on();
		}
		return others.on();
	}

	private boolean keptFlat(Class<?> type) {
		String simple = type.getSimpleName().toLowerCase(Locale.ROOT);
		String full = type.getName().toLowerCase(Locale.ROOT);
		for (String part : flat.get()) {
			String name = part.trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && (simple.equals(name) || full.equals(name) || (name.contains(".") && full.startsWith(name)))) {
				return true;
			}
		}
		return false;
	}

	/** Adds or removes {@code screen}'s class from the keep-flat list. */
	public void setFlat(Screen screen, boolean keepFlat) {
		String name = screen.getClass().getName();
		List<String> next = new java.util.ArrayList<>();
		for (String s : flat.get()) {
			if (!s.equalsIgnoreCase(name)) {
				next.add(s);
			}
		}
		if (keepFlat) {
			next.add(name);
		}
		flat.set(next);
	}
}
