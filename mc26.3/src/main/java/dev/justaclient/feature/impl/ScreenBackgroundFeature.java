package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.feature.setting.FileSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.ImageBackground;
import dev.justaclient.ui.JacTitleScreen;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Backgrounds for the screens behind the menus, by group: menus outside a world, menus over the world (pause, options),
 * and inventories and containers. Each group can keep vanilla, use frosted glass, the animated gradient or a picture or
 * GIF from {@code config/justaclient/backgrounds}.
 */
public final class ScreenBackgroundFeature extends Feature {
	public enum Look implements ChoiceSetting.Named {
		VANILLA("Vanilla"), GLASS("Glass"), GRADIENT("Gradient"), PICTURE("Picture"), DIM("Dim only");

		private final String label;

		Look(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Group {
		MENUS, PAUSE, CONTAINERS
	}

	private final ChoiceSetting<Look> menus = choice("menus", "Menus (no world)", Look.GLASS);
	private final FileSetting menusPicture = add(new FileSetting("menus_picture", "Menus picture", () -> TitleScreenFeature.DIR, TitleScreenFeature.TYPES, "First file in backgrounds"))
		.visibleWhen(() -> menus.get() == Look.PICTURE);
	private final ChoiceSetting<Look> pause = choice("pause", "Pause and options", Look.GLASS);
	private final FileSetting pausePicture = add(new FileSetting("pause_picture", "Pause picture", () -> TitleScreenFeature.DIR, TitleScreenFeature.TYPES, "First file in backgrounds"))
		.visibleWhen(() -> pause.get() == Look.PICTURE);
	private final ChoiceSetting<Look> containers = choice("containers", "Inventories and containers", Look.VANILLA);
	private final FileSetting containersPicture = add(new FileSetting("containers_picture", "Container picture", () -> TitleScreenFeature.DIR, TitleScreenFeature.TYPES, "First file in backgrounds")).visibleWhen(() -> containers.get() == Look.PICTURE);
	private final ChoiceSetting<ImageBackground.Fit> fit = choice("fit", "Picture fit", ImageBackground.Fit.COVER).visibleWhen(this::anyPicture);
	private final BoolSetting blurPicture = bool("blur_picture", "Blur the picture", false).visibleWhen(this::anyPicture);
	private final BoolSetting parallax = bool("parallax", "Parallax with the mouse", true).visibleWhen(this::anyPicture);
	private final NumberSetting dim = number("dim", "Darken", 0.35, 0, 0.9, 0.05, v -> Math.round(v * 100) + "%");
	private final BoolSetting worldBlur = bool("world_blur", "Blur the world behind", true);

	private final ImageBackground[] pictures = {new ImageBackground("menus"), new ImageBackground("pause"), new ImageBackground("containers")};
	private float parallaxX;
	private float parallaxY;

	public ScreenBackgroundFeature() {
		super("screen_backgrounds", "Screen backgrounds", "Glass, gradient or your own picture or GIF behind menus, by screen group",
			Category.SCREENS, Icons.IMAGE, true);
	}

	private boolean anyPicture() {
		return menus.get() == Look.PICTURE || pause.get() == Look.PICTURE || containers.get() == Look.PICTURE;
	}

	public static Group groupOf(Screen screen, boolean inGameUi) {
		if (inGameUi) {
			return Group.CONTAINERS;
		}
		return Minecraft.getInstance().level == null ? Group.MENUS : Group.PAUSE;
	}

	private Look look(Group group) {
		return switch (group) {
			case MENUS -> menus.get();
			case PAUSE -> pause.get();
			case CONTAINERS -> containers.get();
		};
	}

	private FileSetting picture(Group group) {
		return switch (group) {
			case MENUS -> menusPicture;
			case PAUSE -> pausePicture;
			case CONTAINERS -> containersPicture;
		};
	}

	/**
	 * Draws the background for {@code screen} and returns true, or returns false to let vanilla draw it. {@code panorama}
	 * draws the vanilla panorama, {@code blur} applies the vanilla blur once.
	 */
	public boolean draw(Screen screen, GuiGraphicsExtractor g, boolean inGameUi, int mouseX, int mouseY, Runnable panorama, Runnable blur) {
		if (!isEnabled()) {
			return false;
		}
		ScreenStyleFeature style = Features.get(ScreenStyleFeature.class);
		if (screen.getClass().getName().startsWith("dev.justaclient.") || (style != null && style.isSkipped(screen.getClass().getSimpleName()))) {
			return false;
		}
		Group group = groupOf(screen, inGameUi);
		Look look = look(group);
		if (look == Look.VANILLA) {
			return false;
		}
		int w = screen.width;
		int h = screen.height;
		boolean inWorld = Minecraft.getInstance().level != null;
		switch (look) {
			case GLASS -> {
				if (!inWorld) {
					panorama.run();
				}
				if (!inWorld || worldBlur.on()) {
					Draw.glass(g, 0, 0, w, h, 0, Colors.withAlpha(JacConfig.surface(), 0x50), 1f);
				}
				int accent = JacConfig.accent();
				Draw.verticalGradient(g, 0, h * 0.55f, w, h * 0.45f, 0, 0x00000000, Colors.withAlpha(accent, 0x22));
				Draw.rect(g, 0, 0, w, h, 0, Colors.withAlpha(0x000000, (int) (dim.f() * 160)));
			}
			case GRADIENT -> JacTitleScreen.drawGradient(g, w, h);
			case DIM -> {
				if (!inWorld) {
					panorama.run();
				} else if (worldBlur.on()) {
					blur.run();
				}
				Draw.rect(g, 0, 0, w, h, 0, Colors.withAlpha(0x000000, (int) (dim.f() * 255)));
			}
			case PICTURE -> {
				ImageBackground picture = pictures[group.ordinal()];
				picture.use(picture(group).resolve(), PerformanceModeFeature.imageSize(), blurPicture.on());
				if (!picture.ready()) {
					JacTitleScreen.drawGradient(g, w, h);
					return true;
				}
				float px = 0f;
				float py = 0f;
				if (parallax.on()) {
					float tx = -(mouseX / (float) Math.max(1, w) - 0.5f) * 2f;
					float ty = -(mouseY / (float) Math.max(1, h) - 0.5f) * 2f;
					parallaxX += (tx - parallaxX) * 0.08f;
					parallaxY += (ty - parallaxY) * 0.08f;
					px = parallaxX;
					py = parallaxY;
				}
				picture.draw(g, w, h, 0xFFFFFFFF, fit.get(), px, py);
				Draw.rect(g, 0, 0, w, h, 0, Colors.withAlpha(0x000000, (int) (dim.f() * 255)));
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Frees the picture textures, e.g. on joining a world. */
	public void unload() {
		for (ImageBackground picture : pictures) {
			picture.unload();
		}
	}

	@Override
	protected void onDisable() {
		unload();
	}
}
