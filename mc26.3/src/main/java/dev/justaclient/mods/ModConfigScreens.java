package dev.justaclient.mods;

import dev.justaclient.Justaclient;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.ui.ClickGuiScreen;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import dev.justaclient.ui.ModsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Finds a mod's settings screen through the {@code modmenu} entry point that most mods register, without needing Mod
 * Menu installed ({@link ModMenuStubs} puts stand-ins of Mod Menu's API on the class path so those entry points load).
 * Mods that put their options into Sodium's video settings ({@code sodium:config_api_user}) open Sodium's screen on
 * their own page. Everything is looked up by reflection and any failure just means "no settings screen".
 */
public final class ModConfigScreens {
	/**
	 * Settings screens of common mods that do not offer them through {@code modmenu} without Mod Menu installed: a class
	 * with a static {@code createScreen(Screen)} or a constructor taking the parent screen.
	 */
	private static final Map<String, String> KNOWN = Map.of("sodium", "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", "iris",
		"net.irisshaders.iris.gui.screen.ShaderPackScreen");
	/** Where Sodium keeps the options of every mod that adds pages to its video settings. */
	private static final String SODIUM_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";
	/** The last settings screen opened from the Mods panel, and the screen to go back to when it closes to nothing. */
	private static WeakReference<Screen> opened = new WeakReference<>(null);
	private static WeakReference<Screen> openedFrom = new WeakReference<>(null);
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
					// A Mod Menu hook that needs more of Mod Menu than its API; this mod has no screen here.
					Justaclient.LOGGER.info("No settings screen for {}: its Mod Menu hook failed to load ({})", id, rootCause(t));
				}
			}
		} catch (Throwable t) {
			Justaclient.LOGGER.debug("Could not read modmenu entry points", t);
		}
		sodiumPages(found);
		if (!found.containsKey("minecraft")) {
			// Mod Menu itself offers Minecraft's options screen for the "minecraft" entry.
			found.put("minecraft", new OptionsFactory());
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

	/** Mods with a {@code sodium:config_api_user} entry point and no screen of their own open Sodium's, on their page. */
	private static void sodiumPages(Map<String, Object> found) {
		if (!FabricLoader.getInstance().isModLoaded("sodium")) {
			return;
		}
		try {
			for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("sodium:config_api_user", Object.class)) {
				String id = c.getProvider().getMetadata().getId();
				if (!id.equals("sodium")) {
					found.putIfAbsent(id, new SodiumPage(id));
				}
			}
		} catch (Throwable t) {
			// Not this Sodium.
		}
	}

	private static String rootCause(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null && c.getCause() != c) {
			c = c.getCause();
		}
		return c.getClass().getSimpleName() + ": " + c.getMessage();
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

	/** Minecraft's own options screen. */
	public record OptionsFactory() {
		public Screen create(Screen parent) {
			Minecraft mc = Minecraft.getInstance();
			return new OptionsScreen(parent, mc.options);
		}
	}

	/**
	 * Sodium's video settings opened on the first page a mod added; the plain screen if the page can't be found (Sodium
	 * keeps that part of its API internal, so it may change).
	 */
	public record SodiumPage(String modId) {
		public Object create(Screen parent) throws ReflectiveOperationException {
			Class<?> screen = Class.forName(SODIUM_SCREEN);
			try {
				Class<?> manager = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager");
				Object config = manager.getField("CONFIG").get(null);
				Class<?> optionPage = Class.forName("net.caffeinemc.mods.sodium.client.config.structure.OptionPage");
				for (Object mod : (Iterable<?>) config.getClass().getMethod("getModOptions").invoke(config)) {
					if (!modId.equals(mod.getClass().getMethod("configId").invoke(mod))) {
						continue;
					}
					for (Object page : (Iterable<?>) mod.getClass().getMethod("pages").invoke(mod)) {
						if (optionPage.isInstance(page)) {
							return screen.getMethod("createScreen", Screen.class, optionPage).invoke(null, parent, page);
						}
					}
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				Justaclient.LOGGER.debug("Could not find {}'s page in Sodium's settings", modId, e);
			}
			return screen.getMethod("createScreen", Screen.class).invoke(null, parent);
		}
	}

	/** {@code ModMenuApi.createModsScreen}: mods that link to "the mods screen" get the Mods panel. */
	public static Screen modsScreen(@Nullable Screen parent) {
		return new ModsScreen(parent);
	}

	/** {@code ModMenuApi.createModsButtonText}. */
	public static Component modsButtonText() {
		return Component.literal("Mods");
	}

	private static boolean hasCreate(Object factory) {
		return create(factory) != null;
	}

	private static @Nullable Method create(Object factory) {
		try {
			Class<?> api = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory", false, ModConfigScreens.class.getClassLoader());
			if (api.isInstance(factory)) {
				// The public interface method, so a lambda's hidden class needs no access tricks.
				return api.getMethod("create", Screen.class);
			}
		} catch (ReflectiveOperationException | LinkageError e) {
			// No Mod Menu API at all; look the method up on the object.
		}
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

	/**
	 * Where to go when {@code closing} closes without naming a screen ({@code setScreen(null)}, which many settings screens
	 * do instead of going back to the screen that opened them): the Mods panel if {@code closing} is a settings screen it
	 * opened, else null.
	 */
	public static @Nullable Screen closedTo(@Nullable Screen closing) {
		if (closing == null || opened.get() != closing) {
			return null;
		}
		Screen back = openedFrom.get();
		opened = new WeakReference<>(null);
		openedFrom = new WeakReference<>(null);
		return back;
	}

	/** The mod's settings screen, or null if it has none or it failed to open. */
	public static @Nullable Screen open(String modId, Screen parent) {
		if (modId.equals(ModManager.SELF)) {
			return ClickGuiScreen.open(parent);
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
			if (screen instanceof Screen s) {
				opened = new WeakReference<>(s);
				openedFrom = new WeakReference<>(parent);
				return s;
			}
			return null;
		} catch (Throwable t) {
			Justaclient.LOGGER.warn("Could not open the settings screen of {}", modId, t);
			return null;
		}
	}
}
