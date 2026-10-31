package dev.justaclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.JacPipelines;
import dev.justaclient.ui.StyleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Justaclient implements ClientModInitializer {
	public static final String MOD_ID = "justaclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(id("main"));
	public static final KeyMapping OPEN_MENU = new KeyMapping("key.justaclient.menu", InputConstants.KEY_RSHIFT, CATEGORY);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		JacConfig.load();
		JacPipelines.init();
		KeyBindingHelper.registerKeyBinding(OPEN_MENU);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MENU.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new StyleScreen(null));
				}
			}
		});
		// Menus swallow key bindings, so listen on the title screen directly to let the menu open there too.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((current, event) -> {
					if (OPEN_MENU.matches(event) && client.screen == current) {
						client.setScreen(new StyleScreen(current));
					}
				});
			}
		});
		LOGGER.info("Justaclient ready");
	}
}
