package dev.justaclient.feature.impl;

import dev.justaclient.Justaclient;
import dev.justaclient.common.ScreenshotIndex;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.waypoint.Waypoints;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The screenshot manager: a gallery of your screenshots with tags, favourites, search and a compare slider. With
 * auto-tagging on, each new screenshot remembers the world, dimension, biome, weather, time of day and position.
 */
public final class ScreenshotsFeature extends Feature {
	public static final Path INDEX = PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("screenshots.json");

	private final BoolSetting autoTag = bool("auto_tag", "Remember where each screenshot was taken", true);
	private final BoolSetting position = bool("position", "Include coordinates", true).visibleWhen(autoTag::on);
	private ScreenshotIndex index;

	public ScreenshotsFeature() {
		super("screenshots", "Screenshot manager", "A gallery with tags, favourites, search and a compare slider", Category.SCREENS, Icons.CAMERA,
			true);
	}

	public static ScreenshotsFeature get() {
		return Features.get(ScreenshotsFeature.class);
	}

	public static Path folder() {
		return PlatformLoader.getInstance().getGameDir().resolve("screenshots");
	}

	public ScreenshotIndex index() {
		if (index == null) {
			index = ScreenshotIndex.load(INDEX);
		}
		return index;
	}

	public void save() {
		try {
			index().save(INDEX);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save the screenshot index", e);
		}
	}

	/** Called on the render thread as the game picks a file name for a new screenshot. */
	public static void taken(File file) {
		ScreenshotsFeature f = get();
		Minecraft mc = Minecraft.getInstance();
		if (f == null || !f.isEnabled() || !f.autoTag.on() || mc.level == null || mc.player == null) {
			return;
		}
		ScreenshotIndex.Shot shot = f.index().get(file.getName());
		shot.taken = System.currentTimeMillis();
		shot.worldKey = Waypoints.worldKey();
		ServerData server = mc.getCurrentServer();
		if (mc.getSingleplayerServer() != null) {
			shot.world = mc.getSingleplayerServer().getWorldData().getLevelName();
		} else if (server != null) {
			shot.world = server.name;
		}
		Level level = mc.level;
		shot.dimension = level.dimension() == Level.NETHER ? "Nether" : level.dimension() == Level.END ? "The End"
			: level.dimension() == Level.OVERWORLD ? "Overworld" : level.dimension().location().getPath();
		BlockPos pos = mc.player.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		shot.biome = biome.unwrapKey().map(k -> pretty(k.location().getPath())).orElse(null);
		shot.weather = level.isThundering() ? "Thunder" : level.isRaining()
			? (biome.value().coldEnoughToSnow(pos) ? "Snow" : "Rain") : "Clear";
		long day = Math.floorMod(level.getDayTime(), 24000L);
		shot.time = day < 12000 ? "Day" : day < 13800 ? "Sunset" : day < 22200 ? "Night" : "Sunrise";
		if (f.position.on()) {
			shot.x = pos.getX();
			shot.y = pos.getY();
			shot.z = pos.getZ();
		}
		f.save();
	}

	private static String pretty(String path) {
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}
}
