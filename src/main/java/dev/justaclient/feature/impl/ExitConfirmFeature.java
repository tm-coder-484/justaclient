package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.JacConfirmScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks before the pause menu's Save and Quit or Disconnect button leaves the world. */
public final class ExitConfirmFeature extends Feature {
	private final BoolSetting singleplayer = bool("singleplayer", "Ask in singleplayer", true);
	private final BoolSetting servers = bool("servers", "Ask on servers", true);

	public ExitConfirmFeature() {
		super("exit_confirm", "Exit confirmation", "Asks before Save and Quit or Disconnect", Category.UTILITY, Icons.LOG_OUT, true);
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof PauseScreen pause)) {
				return;
			}
			ExitConfirmFeature feature = Features.get(ExitConfirmFeature.class);
			boolean local = client.isLocalServer();
			if (!feature.isEnabled() || !(local ? feature.singleplayer.on() : feature.servers.on())) {
				return;
			}
			Component label = CommonComponents.disconnectButtonLabel(local);
			for (AbstractWidget widget : Screens.getButtons(screen)) {
				if (widget instanceof Button original && original.getMessage().equals(label) && original.visible) {
					// Hide the vanilla button and put one that asks first in its place.
					original.visible = false;
					original.active = false;
					Button ask = Button.builder(label, b -> ask(client, pause))
						.bounds(original.getX(), original.getY(), original.getWidth(), original.getHeight()).build();
					Screens.getButtons(screen).add(ask);
					return;
				}
			}
		});
	}

	private static void ask(Minecraft client, PauseScreen pause) {
		boolean local = client.isLocalServer();
		ServerData server = client.getCurrentServer();
		String title = local ? "Save and quit?" : "Disconnect?";
		String message = local ? "Your world is saved and you go back to the title screen."
			: "You leave " + (server == null ? "this server" : server.name) + " and go back to the server list.";
		client.setScreen(new JacConfirmScreen(pause, Icons.LOG_OUT, title, message, local ? "Save and quit" : "Disconnect", () -> {
			client.setScreen(pause);
			client.getReportingContext().draftReportHandled(client, pause, () -> client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
		}));
	}
}
