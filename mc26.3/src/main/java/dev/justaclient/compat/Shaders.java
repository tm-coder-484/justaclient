package dev.justaclient.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Whether a shader pack is drawing the world (Iris). Shader packs replace the world's shaders and only draw render types
 * they know, so world effects switch to a vanilla render type while one is on. Iris is looked up by name, so it stays
 * optional and the same code builds for every Minecraft version.
 */
public final class Shaders {
	private static final @Nullable Object IRIS = irisApi();
	private static final @Nullable MethodHandle IN_USE = IRIS == null ? null : inUse(IRIS);

	private Shaders() {
	}

	public static boolean packInUse() {
		if (IN_USE == null) {
			return false;
		}
		try {
			return (boolean) IN_USE.invoke(IRIS);
		} catch (Throwable e) {
			return true;
		}
	}

	private static @Nullable Object irisApi() {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return null;
		}
		try {
			return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	private static @Nullable MethodHandle inUse(Object api) {
		try {
			Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			return MethodHandles.publicLookup().findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}
}
