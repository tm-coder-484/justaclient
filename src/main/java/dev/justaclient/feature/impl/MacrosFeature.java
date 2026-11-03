package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Command macros: each press of a macro's key sends its command (or chat line) once, exactly as if you had typed it.
 * One press, one message, so it never plays for you.
 */
public final class MacrosFeature extends Feature {
	private static final int SLOTS = 8;
	private final KeySetting[] keys = new KeySetting[SLOTS];
	private final TextSetting[] commands = new TextSetting[SLOTS];
	private final boolean[] wasDown = new boolean[SLOTS];

	public MacrosFeature() {
		super("macros", "Command macros", "A key (or /cmd 1 to 8) sends one command or chat line per press", Category.CHAT, Icons.KEYBOARD, false);
		markExperimental();
		for (int i = 0; i < SLOTS; i++) {
			int n = i + 1;
			TextSetting command = add(new TextSetting("command_" + n, "Macro " + n, "", 120, n == 1 ? "/home" : "/command or chat"));
			commands[i] = command;
			// Only show the next empty slot, so the list grows as you fill it.
			if (i > 0) {
				TextSetting previous = commands[i - 1];
				command.visibleWhen(() -> !previous.get().isBlank() || !command.get().isBlank());
			}
			keys[i] = key("key_" + n, "Macro " + n + " key", KeySetting.NONE).visibleWhen(() -> !command.get().isBlank());
		}
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < SLOTS; i++) {
			boolean down = mc.screen == null && keys[i].isDown();
			if (down && !wasDown[i]) {
				run(mc, commands[i].get().trim());
			}
			wasDown[i] = down;
		}
	}

	public static int slots() {
		return SLOTS;
	}

	/** Macro {@code n}'s command (1-based), or "" if that slot is empty. */
	public static String command(int n) {
		MacrosFeature f = dev.justaclient.feature.Features.get(MacrosFeature.class);
		return n < 1 || n > SLOTS ? "" : f.commands[n - 1].get().trim();
	}

	/** {@code /cmd n}: sends macro {@code n} once. False if the slot is empty. */
	public static boolean runSlot(int n) {
		String line = command(n);
		if (line.isEmpty()) {
			return false;
		}
		run(Minecraft.getInstance(), line);
		return true;
	}

	private static void run(Minecraft mc, String line) {
		ClientPacketListener connection = mc.getConnection();
		if (line.isEmpty() || connection == null) {
			return;
		}
		if (line.startsWith("/")) {
			connection.sendCommand(line.substring(1));
		} else {
			connection.sendChat(line);
		}
		Notifications.push(Icons.KEYBOARD, "Macro", line.length() > 32 ? line.substring(0, 32) + "..." : line);
	}
}
