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
import dev.justaclient.feature.impl.ExitConfirmFeature;
import dev.justaclient.feature.impl.WeaponCompareFeature;
import dev.justaclient.feature.impl.HatsFeature;
import dev.justaclient.feature.impl.SlashTrailFeature;
import dev.justaclient.feature.impl.HotbarFeature;
import dev.justaclient.feature.impl.TargetMarkerFeature;
import dev.justaclient.feature.impl.TrailsFeature;
import dev.justaclient.feature.impl.WaypointsFeature;
import dev.justaclient.hud.HudRenderer;
import dev.justaclient.hud.PickupFeed;
import dev.justaclient.hud.LifetimeStats;
import dev.justaclient.hud.SessionHistory;
import dev.justaclient.notes.NotesStore;
import dev.justaclient.world.WorldStore;
import dev.justaclient.feature.impl.ScreenBackgroundFeature;
import dev.justaclient.hud.SessionStats;
import dev.justaclient.hud.widgets.NowPlayingHud;
import dev.justaclient.hud.widgets.TimerHud;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.ExploredMap;
import dev.justaclient.render.JacPipelines;
import dev.justaclient.render.WorldFx;
import dev.justaclient.feature.impl.TrinketFeature;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.JacTitleScreen;
import dev.justaclient.ui.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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

	private static boolean crashChecked;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		// Mod renames that could not happen last time (the jar was in use) are done first.
		dev.justaclient.common.mods.DeferredRenames.load();
		JacConfig.load();
		JacPipelines.init();
		Features.init();
		Profiles.load();
		Features.get(dev.justaclient.feature.impl.ProfileBackupFeature.class).launched();
		dev.justaclient.debug.SelfTest.maybeRunAtStartup();
		HudRenderer.init();
		HotbarFeature.init();
		BlockHighlightFeature.init();
		WorldFx.init();
		TargetMarkerFeature.init();
		EndCrystalFeature.init();
		SlashTrailFeature.init();
		HatsFeature.init();
		TrailsFeature.init();
		AmbientParticlesFeature.init();
		dev.justaclient.feature.impl.BiomeEffectsFeature.init();
		CosmeticsFeature.init();
		WaypointsFeature.init();
		AutoReconnectFeature.init();
		ExitConfirmFeature.init();
		WeaponCompareFeature.init();
		dev.justaclient.inventory.ContainerOverlays.init();
		dev.justaclient.feature.impl.FurnaceInfoFeature.init();
		dev.justaclient.feature.impl.FoodInfoFeature.init();
		dev.justaclient.feature.impl.PotionGuideFeature.init();
		dev.justaclient.feature.impl.AnvilHelperFeature.init();
		dev.justaclient.feature.impl.CraftingHelperFeature.init();
		dev.justaclient.chat.ChatHook.init();
		dev.justaclient.chat.ChatScreens.init();
		dev.justaclient.feature.impl.StorageMemoryFeature.init();
		dev.justaclient.feature.impl.DeathRecapFeature.init();
		dev.justaclient.feature.impl.KitsFeature.init();
		JacCommands.init();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			SessionHistory.onJoin(client);
			SessionStats.reset();
			CombatTracker.resetSession();
			Features.get(TimerHud.class).onJoin();
			LifetimeStats.onJoin();
			JacTitleScreen.freeBackground();
			Features.get(ScreenBackgroundFeature.class).unload();
			dev.justaclient.feature.impl.KeybindsFeature.get().onJoin(client.getCurrentServer());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			dev.justaclient.inventory.StorageFinder.clear();
			SessionHistory.finish();
			LifetimeStats.onLeave();
			NotesStore.saveAll();
			client.execute(() -> dev.justaclient.feature.impl.KeybindsFeature.get().onLeave());
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> dev.justaclient.feature.impl.KeybindsFeature.get().startup());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			SessionHistory.finish();
			LifetimeStats.save();
			NotesStore.saveAll();
			// Writes still queued for the world folders finish before the game exits.
			WorldStore.flush();
			dev.justaclient.feature.impl.KeybindsFeature.get().onLeave();
			// Mods switched while their jar was locked are renamed once the game has closed, and the game restarts if asked.
			dev.justaclient.common.mods.DeferredRenames.applyAfterExit();
		});
		ExploredMap.init();
		KeyBindingHelper.registerKeyBinding(OPEN_MENU);
		KeyBindingHelper.registerKeyBinding(OPEN_MAP);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CombatTracker.tick();
			dev.justaclient.waypoint.Locate.tick();
			ExploredMap.tick(client);
			SessionStats.tick();
			SessionHistory.tick(client);
			PickupFeed.tick(client);
			LifetimeStats.tick();
			Features.tick(client);
			while (OPEN_MENU.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(ClickGuiScreen.open(null));
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
				// The physics trinket can be picked up and thrown while the chat frees the mouse.
				ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> event.button() != 0
					|| !Features.get(TrinketFeature.class).grab(event.x(), event.y()));
				ScreenMouseEvents.allowMouseRelease(screen).register((current, event) -> {
					Features.get(TrinketFeature.class).release();
					return true;
				});
			}
			if ((screen instanceof TitleScreen || screen instanceof JacTitleScreen) && !crashChecked) {
				crashChecked = true;
				if (Features.get(dev.justaclient.feature.impl.CrashHelperFeature.class).isEnabled()) {
					dev.justaclient.crash.CrashHelper.Crash crash = dev.justaclient.crash.CrashHelper.unseen();
					if (crash != null) {
						client.execute(() -> client.setScreen(new dev.justaclient.ui.CrashHelperScreen(client.screen, crash)));
					}
				}
			}
			if (screen instanceof TitleScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((current, event) -> {
					if (OPEN_MENU.matches(event) && client.screen == current) {
						client.setScreen(ClickGuiScreen.open(current));
					}
				});
			}
		});
		LOGGER.info("Justaclient ready");
	}
}
