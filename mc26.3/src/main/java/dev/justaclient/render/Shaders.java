package dev.justaclient.render;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/** Asks Iris, through its public API and without a compile-time dependency, whether a shader pack is drawing the world. */
public final class Shaders {
	private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
	private static @Nullable Object api;
	private static @Nullable Method inUse;
	private static boolean failed;

	private Shaders() {
	}

	/** True while an Iris shader pack is active. Effects that replace how the frame is composed step aside then. */
	public static boolean packInUse() {
		if (!IRIS || failed) {
			return false;
		}
		try {
			if (inUse == null) {
				Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = type.getMethod("getInstance").invoke(null);
				inUse = type.getMethod("isShaderPackInUse");
			}
			return (Boolean) inUse.invoke(api);
		} catch (ReflectiveOperationException | ClassCastException e) {
			failed = true;
			return false;
		}
	}
}
