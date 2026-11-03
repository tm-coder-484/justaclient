package dev.justaclient.mods;

import dev.justaclient.Justaclient;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.ui.ClickGuiScreen;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Finds a mod's settings screen through the {@code modmenu} entry point that most mods register, without needing Mod
 * Menu installed. Everything is looked up by reflection and any failure just means "no settings screen".
 */
public final class ModConfigScreens {
	/**
	 * Settings screens of common mods that do not offer them through {@code modmenu} without Mod Menu installed: a class
	 * with a static {@code createScreen(Screen)} or a constructor taking the parent screen.
	 */
	private static final Map<String, String> KNOWN = Map.of("sodium", "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "iris",
		"net.irisshaders.iris.gui.screen.ShaderPackScreen");
	/** mod id to a factory object with {@code create(Screen)}. */
	private static @Nullable Map<String, Object> factories;

	private ModConfigScreens() {
	}

	private static Map<String, Object> factories() {
		if (factories != null) {
			return factories;
		}
		Map<String, Object> found = new HashMap<>();
		try {
			for (EntrypointContainer<Object> container : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				String id = container.getProvider().getMetadata().getId();
				try {
					Object api = container.getEntrypoint();
					Object factory = call(api, "getModConfigScreenFactory");
					if (factory != null && hasCreate(factory)) {
						found.put(id, factory);
					}
					Object provided = call(api, "getProvidedConfigScreenFactories");
					if (provided instanceof Map<?, ?> map) {
						for (Map.Entry<?, ?> e : map.entrySet()) {
							if (e.getKey() instanceof String other && e.getValue() != null && hasCreate(e.getValue())) {
								found.putIfAbsent(other, e.getValue());
							}
						}
					}
				} catch (Throwable t) {
					// A mod whose Mod Menu hook needs Mod Menu itself; it has no screen here.
				}
			}
		} catch (Throwable t) {
			Justaclient.LOGGER.debug("Could not read modmenu entry points", t);
		}
		KNOWN.forEach((id, className) -> {
			if (!found.containsKey(id) && FabricLoader.getInstance().isModLoaded(id)) {
				try {
					found.put(id, new KnownScreen(Class.forName(className)));
				} catch (Throwable t) {
					// Not in this version.
				}
			}
		});
		factories = found;
		return found;
	}

	private static @Nullable Object call(Object target, String name) throws ReflectiveOperationException {
		for (Method m : target.getClass().getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == 0) {
				m.setAccessible(true);
				return m.invoke(target);
			}
		}
		return null;
	}

	/** Opens a known screen class: {@code createScreen(Screen)} if it has one, else its constructor. */
	public record KnownScreen(Class<?> type) {
		public Object create(Screen parent) throws ReflectiveOperationException {
			try {
				return type.getMethod("createScreen", Screen.class).invoke(null, parent);
			} catch (NoSuchMethodException e) {
				return type.getConstructor(Screen.class).newInstance(parent);
			}
		}
	}

	private static boolean hasCreate(Object factory) {
		return create(factory) != null;
	}

	private static @Nullable Method create(Object factory) {
		for (Method m : factory.getClass().getMethods()) {
			if (m.getName().equals("create") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
				return m;
			}
		}
		return null;
	}

	public static boolean has(String modId) {
		return modId.equals(ModManager.SELF) || factories().containsKey(modId);
	}

	/** The mod's settings screen, or null if it has none or it failed to open. */
	public static @Nullable Screen open(String modId, Screen parent) {
		if (modId.equals(ModManager.SELF)) {
			return new ClickGuiScreen(parent);
		}
		Object factory = factories().get(modId);
		if (factory == null) {
			return null;
		}
		try {
			Method m = create(factory);
			if (m == null) {
				return null;
			}
			m.setAccessible(true);
			Object screen = m.invoke(factory, parent);
			return screen instanceof Screen s ? s : null;
		} catch (Throwable t) {
			Justaclient.LOGGER.warn("Could not open the settings screen of {}", modId, t);
			return null;
		}
	}
}
