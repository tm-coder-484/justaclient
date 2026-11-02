package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Copies your block position to the clipboard with one key, for sharing in chat or notes. */
public final class CopyCoordinatesFeature extends Feature {
	public enum Format implements ChoiceSetting.Named {
		SPACES("x y z"), COMMAS("x, y, z");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final KeySetting key = key("key", "Copy key", KeySetting.NONE);
	private final ChoiceSetting<Format> format = choice("format", "Format", Format.SPACES);
	private boolean wasDown;

	public CopyCoordinatesFeature() {
		super("copy_coordinates", "Copy coordinates", "Copy your position with one key", Category.UTILITY, Icons.COPY, true);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.gui.screen() == null && key.isDown();
		if (down && !wasDown && mc.player != null) {
			Player p = mc.player;
			String sep = format.get() == Format.COMMAS ? ", " : " ";
			String text = p.getBlockX() + sep + p.getBlockY() + sep + p.getBlockZ();
			mc.keyboardHandler.setClipboard(text);
			Notifications.push(Icons.COPY, "Copied", text);
		}
		wasDown = down;
	}
}
