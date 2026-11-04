package dev.justaclient.feature.impl;

import dev.justaclient.Justaclient;
import dev.justaclient.common.modrinth.ModrinthApi;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ActionSetting;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.render.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Browse Modrinth for mods, shaders, resource packs and data packs made for this game version, and install them into the
 * right folder. Off by default because it goes online (to api.modrinth.com and cdn.modrinth.com only). Every download is
 * checked against Modrinth's SHA-512 before it is saved.
 */
public final class ModrinthFeature extends Feature {
	/** How search results are laid out: big icon tiles with just the name, or rows with author and downloads. */
	public enum Layout implements ChoiceSetting.Named {
		TILES("Tiles"),
		LIST("List");

		private final String label;

		Layout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final ChoiceSetting<Layout> layout = choice("layout", "Results as", Layout.TILES);
	public final BoolSetting betas = bool("betas", "Offer beta and alpha versions when there is no release", true);
	public final BoolSetting dependencies = bool("dependencies", "Also install mods a mod needs", true);
	private final ActionSetting open = add(new ActionSetting("open", "Browse Modrinth...", () -> openScreen()));
	private ModrinthApi api;

	public ModrinthFeature() {
		super("modrinth", "Modrinth browser", "Find and install mods, shaders, resource packs and data packs for this version (goes online)",
			Category.TOOLS, Icons.DOWNLOAD, false);
	}

	public static ModrinthFeature get() {
		return Features.get(ModrinthFeature.class);
	}

	public static void openScreen() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new dev.justaclient.ui.ModrinthScreen(mc.screen)));
	}

	public ModrinthApi api() {
		if (api == null) {
			api = new ModrinthApi("tm-coder-484/justaclient/" + modVersion() + " (https://github.com/tm-coder-484/justaclient)");
		}
		return api;
	}

	public static String modVersion() {
		return FabricLoader.getInstance().getModContainer(Justaclient.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}

	/** The game version Modrinth files things under, such as "1.21.11". */
	public static String gameVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
	}
}
