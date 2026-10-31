package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Adds a Reconnect button to the disconnect screen that counts down and rejoins the last server by itself. Press it to
 * rejoin straight away, or leave the screen to cancel.
 */
public final class AutoReconnectFeature extends Feature {
	private final NumberSetting delay = number("delay", "Wait before rejoining", 5, 1, 60, 1, NumberSetting.unit(" s"));
	private static @Nullable ServerData lastServer;

	public AutoReconnectFeature() {
		super("auto_reconnect", "Auto reconnect", "Rejoin the last server after a disconnect, with a countdown", Category.UTILITY, Icons.ROTATE_CCW, false);
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData data = client.getCurrentServer();
			if (data != null && !client.isLocalServer()) {
				lastServer = data;
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			AutoReconnectFeature feature = Features.get(AutoReconnectFeature.class);
			ServerData server = lastServer;
			if (!feature.isEnabled() || !(screen instanceof DisconnectedScreen) || server == null) {
				return;
			}
			long deadline = System.currentTimeMillis() + feature.delay.i() * 1000L;
			Button button = Button.builder(label(deadline), b -> reconnect(client, server))
				.bounds(width / 2 - 100, height - 30, 200, 20).build();
			Screens.getButtons(screen).add(button);
			ScreenEvents.afterTick(screen).register(s -> {
				button.setMessage(label(deadline));
				if (System.currentTimeMillis() >= deadline && client.screen == s) {
					reconnect(client, server);
				}
			});
		});
	}

	private static Component label(long deadline) {
		long left = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
		return Component.literal("Reconnect (" + left + ")");
	}

	private static void reconnect(Minecraft client, ServerData server) {
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, false, null);
	}
}
