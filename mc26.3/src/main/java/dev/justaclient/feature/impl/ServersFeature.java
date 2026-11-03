package dev.justaclient.feature.impl;

import dev.justaclient.common.ServerFolders;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;

/**
 * The client's server list: folders, favourites, search and live pings, plus a Servers button in the pause menu to hop
 * to another server without going through the title screen.
 */
public final class ServersFeature extends Feature {
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("servers.json");

	private final BoolSetting ownList = bool("own_list", "Multiplayer opens the client's list", true);
	private final BoolSetting pauseButton = bool("pause_button", "Servers button in the pause menu", true);
	private ServerFolders folders;

	public ServersFeature() {
		super("servers", "Server list", "Folders, favourites and search for your servers, and a Servers button in the pause menu", Category.SCREENS,
			Icons.SERVER, true);
	}

	public static ServersFeature get() {
		return Features.get(ServersFeature.class);
	}

	public ServerFolders folders() {
		if (folders == null) {
			folders = ServerFolders.load(FILE);
		}
		return folders;
	}

	public void saveFolders() {
		try {
			folders().save(FILE);
		} catch (IOException e) {
			dev.justaclient.Justaclient.LOGGER.warn("Could not save the server folders", e);
		}
	}

	/** Server Switcher has its own Servers button, so the client leaves the pause menu alone when it is installed. */
	public boolean pauseButton() {
		return isEnabled() && pauseButton.on() && !ModManager.serverSwitcherInstalled();
	}

	public boolean ownList() {
		return isEnabled() && ownList.on();
	}

	/** The multiplayer screen to open: the client's list, or vanilla's (after its first-time warning). */
	public static Screen multiplayer(Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		ServersFeature f = get();
		if (f != null && f.ownList()) {
			return new dev.justaclient.ui.ServersScreen(parent);
		}
		return mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(parent) : new SafetyScreen(parent);
	}
}
