package dev.justaclient.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.common.WorldKeys;
import dev.justaclient.waypoint.Waypoints;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * One folder per singleplayer save or server ({@code config/justaclient/worlds/<world>/}) for features that remember
 * things per world: notes, storage memory, kits and the like. Writes happen on a background thread so a save never
 * stalls a frame, and go through a temporary file so a crash cannot leave half a file behind.
 */
public final class WorldStore {
	public static final Path DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("worlds");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Justaclient world store");
		t.setDaemon(true);
		return t;
	});

	private WorldStore() {
	}

	/** The folder of the world being played, or null in menus. */
	public static @Nullable Path folder() {
		String key = Waypoints.worldKey();
		return key == null ? null : DIR.resolve(WorldKeys.folder(key));
	}

	/** Reads {@code name}.json from the current world's folder, or null if it does not exist or cannot be read. */
	public static @Nullable JsonElement read(String name) {
		Path folder = folder();
		if (folder == null) {
			return null;
		}
		Path file = folder.resolve(name + ".json");
		if (!Files.exists(file)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(reader);
		} catch (IOException | RuntimeException e) {
			Justaclient.LOGGER.warn("Could not read {}", file, e);
			return null;
		}
	}

	/** Writes {@code json} to {@code name}.json in the current world's folder, in the background. */
	public static void write(String name, JsonElement json) {
		Path folder = folder();
		if (folder != null) {
			write(folder, name, json);
		}
	}

	/** Writes {@code json} to {@code name}.json in the folder of the world with key {@code worldKey}, in the background. */
	public static void write(String worldKey, String name, JsonElement json) {
		write(DIR.resolve(WorldKeys.folder(worldKey)), name, json);
	}

	/** Waits (up to a few seconds) for queued writes to land, so closing the game does not drop them. */
	public static void flush() {
		WRITER.shutdown();
		try {
			if (!WRITER.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
				Justaclient.LOGGER.warn("Some world data was still being written when the game closed");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void write(Path folder, String name, JsonElement json) {
		String text = GSON.toJson(json);
		Runnable task = () -> {
			Path file = folder.resolve(name + ".json");
			try {
				Files.createDirectories(folder);
				Path tmp = folder.resolve(name + ".json.tmp");
				try (Writer writer = Files.newBufferedWriter(tmp)) {
					writer.write(text);
				}
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				Justaclient.LOGGER.warn("Could not write {}", file, e);
			}
		};
		if (WRITER.isShutdown()) {
			// After the flush on exit, write straight away.
			task.run();
		} else {
			WRITER.execute(task);
		}
	}
}
