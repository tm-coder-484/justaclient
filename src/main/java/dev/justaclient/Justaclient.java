package dev.justaclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.command.JacCommands;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.AmbientParticlesFeature;
import dev.justaclient.feature.impl.AutoReconnectFeature;
import dev.justaclient.feature.impl.BlockHighlightFeature;
import dev.justaclient.feature.impl.CombatTracker;
import dev.justaclient.feature.impl.CosmeticsFeature;
import dev.justaclient.feature.impl.EndCrystalFeature;
import dev.justaclient.feature.impl.SlashTrailFeature;
import dev.justaclient.feature.impl.HotbarFeature;
import dev.justaclient.feature.impl.TargetMarkerFeature;
import dev.justaclient.feature.impl.TrailsFeature;
import dev.justaclient.feature.impl.WaypointsFeature;
import dev.justaclient.hud.HudRenderer;
import dev.justaclient.hud.SessionStats;
import dev.justaclient.hud.widgets.NowPlayingHud;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.JacPipelines;
import dev.justaclient.render.WorldFx;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Justaclient implements ClientModInitializer {
	public static final String MOD_ID = "justaclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(id("main"));
	public static final KeyMapping OPEN_MENU = new KeyMapping("key.justaclient.menu", InputConstants.KEY_RSHIFT, CATEGORY);
	public static final KeyMapping OPEN_MAP = new KeyMapping("key.justaclient.map", InputConstants.KEY_M, CATEGORY);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		JacConfig.load();
		JacPipelines.init();
		Features.init();
		Profiles.load();
		HudRenderer.init();
		HotbarFeature.init();
		BlockHighlightFeature.init();
		WorldFx.init();
		TargetMarkerFeature.init();
		EndCrystalFeature.init();
		SlashTrailFeature.init();
		TrailsFeature.init();
		AmbientParticlesFeature.init();
		CosmeticsFeature.init();
		WaypointsFeature.init();
		AutoReconnectFeature.init();
		JacCommands.init();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SessionStats.reset());
		KeyBindingHelper.registerKeyBinding(OPEN_MENU);
		KeyBindingHelper.registerKeyBinding(OPEN_MAP);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CombatTracker.tick();
			SessionStats.tick();
			Features.tick(client);
			while (OPEN_MENU.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new ClickGuiScreen(null));
				}
			}
			while (OPEN_MAP.consumeClick()) {
				if (client.screen == null && client.level != null) {
					client.setScreen(new WorldMapScreen());
				}
			}
		});
		// Menus swallow key bindings, so listen on the title screen directly to let the menu open there too.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				// With the chat open the mouse is free, so the Spotify card's buttons can be clicked.
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> !NowPlayingHud.clickFromChat(event.x(), event.y(), current.width,
					current.height));
			}
			if (screen instanceof TitleScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((current, event) -> {
					if (OPEN_MENU.matches(event) && client.screen == current) {
						client.setScreen(new ClickGuiScreen(current));
					}
				});
			}
		});
		LOGGER.info("Justaclient ready");
	}
}
