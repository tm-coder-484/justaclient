package dev.justaclient.feature.impl;

import dev.justaclient.common.mods.ModManager;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;

/**
 * The Mods panel: see every mod with its details and settings, and turn mods off or on (applied after a restart). Opens
 * from the title screen, the pause menu's Command Centre and the client menu.
 */
public final class ModsPanelFeature extends Feature {
	private final BoolSetting titleButton = bool("title_button", "Mods on the title screen", true);
	private final BoolSetting libraries = bool("libraries", "Show library mods", true);
	private final BoolSetting relaunch = bool("relaunch", "Restart starts the game again", true);
	private final BoolSetting keepKeybinds = bool("keep_keybinds", "Keep key binds of mods that are off", true);

	public ModsPanelFeature() {
		super("mods_panel", "Mods panel", "See your mods, open their settings, and turn them off or on", Category.SCREENS, Icons.PACKAGE, true);
	}

	public boolean titleButton() {
		return isEnabled() && titleButton.on();
	}

	public boolean showLibraries() {
		return libraries.on();
	}

	public boolean relaunch() {
		return relaunch.on();
	}

	/** Server Switcher keeps key binds itself, so the client steps aside when it is installed. */
	public static boolean keepKeybinds() {
		ModsPanelFeature f = Features.get(ModsPanelFeature.class);
		return f != null && f.isEnabled() && f.keepKeybinds.on() && !ModManager.serverSwitcherInstalled();
	}
}
