package dev.justaclient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.impl.FabricLoaderImpl;

/**
 * Points Fabric's game folder at a temporary one, so classes that work out their files from the config folder when they
 * load (Waypoints, Profiles...) can be tested without a running game. Call {@link #config()} before touching them.
 */
public final class TestGame {
	private static Path dir;

	private TestGame() {
	}

	public static synchronized Path dir() {
		if (dir == null) {
			try {
				dir = Files.createTempDirectory("jac-game");
				Method set = FabricLoaderImpl.class.getDeclaredMethod("setGameDir", Path.class);
				set.setAccessible(true);
				set.invoke(FabricLoaderImpl.INSTANCE, dir);
			} catch (IOException | ReflectiveOperationException e) {
				throw new IllegalStateException("Could not set up a test game folder", e);
			}
		}
		return dir;
	}

	/** The client's own config folder inside the test game folder. */
	public static Path config() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("justaclient");
	}
}
