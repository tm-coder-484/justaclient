package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.JacTitleScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;

/** Swaps the vanilla title screen for Justaclient's. Demo mode keeps the vanilla screen, which has demo-only buttons. */
public final class TitleScreenFeature extends Feature {
	public enum Background implements ChoiceSetting.Named {
		BLURRED("Blurred panorama"), PANORAMA("Panorama"), GRADIENT("Animated gradient"), IMAGE("Your picture or GIF");

		private final String label;

		Background(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("backgrounds");
	private static final List<String> TYPES = List.of(".png", ".jpg", ".jpeg", ".gif");

	private final ChoiceSetting<Background> background = choice("background", "Background", Background.BLURRED);
	private final TextSetting image = add(new TextSetting("image", "Picture file", "", 64, "blank = first file in config/justaclient/backgrounds"))
		.visibleWhen(() -> background() == Background.IMAGE);
	private final NumberSetting dim = number("dim", "Darken", 0.25, 0, 0.8, 0.05, v -> Math.round(v * 100) + "%")
		.visibleWhen(() -> background() == Background.IMAGE);
	private final BoolSetting imageBlur = bool("image_blur", "Blur the picture", false).visibleWhen(() -> background() == Background.IMAGE);

	public TitleScreenFeature() {
		super("title_screen", "Title screen", "A cleaner title screen in the client's style", Category.UTILITY, Icons.LAYOUT_DASHBOARD, true);
	}

	public Background background() {
		return background.get();
	}

	public float dim() {
		return dim.f();
	}

	public boolean imageBlur() {
		return imageBlur.on();
	}

	/** The chosen picture in {@link #DIR}, or the first picture there when none is named. Null if there is none. */
	public @Nullable Path imagePath() {
		try {
			Files.createDirectories(DIR);
		} catch (IOException e) {
			return null;
		}
		String name = image.get().trim();
		if (!name.isEmpty()) {
			Path path = DIR.resolve(name).normalize();
			return path.startsWith(DIR) && Files.isRegularFile(path) ? path : null;
		}
		try (Stream<Path> files = Files.list(DIR)) {
			return files.filter(Files::isRegularFile)
				.filter(p -> TYPES.stream().anyMatch(t -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(t)))
				.sorted().findFirst().orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	/** Called whenever a screen is about to open; returns the screen to open instead. */
	public Screen replace(Screen screen) {
		if (isEnabled() && screen != null && screen.getClass() == TitleScreen.class && !Minecraft.getInstance().isDemo()) {
			return new JacTitleScreen();
		}
		return screen;
	}

	@Override
	protected void onEnable() {
		swapOpenScreen();
	}

	@Override
	protected void onDisable() {
		swapOpenScreen();
	}

	/** Applies the change straight away if the title screen is showing behind the menu. */
	private void swapOpenScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof ClickGuiScreen menu) {
			menu.replaceParentTitle(isEnabled() ? new JacTitleScreen() : new TitleScreen());
		}
	}
}
