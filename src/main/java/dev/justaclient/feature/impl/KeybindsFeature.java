package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.Justaclient;
import dev.justaclient.common.KeybindProfiles;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * The key bind manager: search, conflicts, mouse buttons, saved sets of binds, and a set per server that is put on when
 * you join and taken off when you leave.
 */
public final class KeybindsFeature extends Feature {
	public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("keybinds.json");

	private final BoolSetting perServer = bool("per_server", "Use each server's key set", true);
	private @Nullable KeybindProfiles data;

	public KeybindsFeature() {
		super("keybind_manager", "Key bind manager", "Search key binds, see clashes, and keep sets of binds, also per server", Category.SCREENS,
			Icons.KEYBOARD, true);
	}

	public static KeybindsFeature get() {
		return Features.get(KeybindsFeature.class);
	}

	public KeybindProfiles data() {
		if (data == null) {
			data = KeybindProfiles.load(FILE);
		}
		return data;
	}

	public void save() {
		try {
			data().save(FILE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save the key bind sets", e);
		}
	}

	/** The current binds, by name. */
	public static Map<String, String> snapshot() {
		Map<String, String> map = new LinkedHashMap<>();
		for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
			map.put(k.getName(), k.saveString());
		}
		return map;
	}

	/** Puts {@code binds} on; bindings it does not mention keep their key. */
	public static void apply(Map<String, String> binds) {
		Minecraft mc = Minecraft.getInstance();
		for (KeyMapping k : mc.options.keyMappings) {
			String key = binds.get(k.getName());
			if (key != null) {
				try {
					k.setKey(InputConstants.getKey(key));
				} catch (RuntimeException e) {
					// A key this version does not know.
				}
			}
		}
		KeyMapping.resetMapping();
		mc.options.save();
	}

	/** Called at start: if the game closed while a server's set was on, the player's own binds come back. */
	public void startup() {
		KeybindProfiles d = data();
		if (d.restore != null) {
			apply(d.restore);
			d.restore = null;
			save();
		}
	}

	public void onJoin(@Nullable ServerData server) {
		if (!isEnabled() || !perServer.on() || server == null) {
			return;
		}
		KeybindProfiles d = data();
		String profile = d.profileFor(server.ip);
		Map<String, String> binds = profile == null ? null : d.profiles.get(profile);
		if (binds == null) {
			return;
		}
		if (d.restore == null) {
			d.restore = snapshot();
		}
		save();
		apply(binds);
		Notifications.push(Icons.KEYBOARD, profile, "key set on for " + server.name);
	}

	public void onLeave() {
		KeybindProfiles d = data();
		if (d.restore != null) {
			apply(d.restore);
			d.restore = null;
			save();
		}
	}
}
