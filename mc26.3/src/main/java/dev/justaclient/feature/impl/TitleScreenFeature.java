package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.ClickGuiScreen;
import dev.justaclient.ui.JacTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Swaps the vanilla title screen for Justaclient's. Demo mode keeps the vanilla screen, which has demo-only buttons. */
public final class TitleScreenFeature extends Feature {
	public enum Background implements ChoiceSetting.Named {
		BLURRED("Blurred panorama"), PANORAMA("Panorama"), GRADIENT("Animated gradient");

		private final String label;

		Background(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Background> background = choice("background", "Background", Background.BLURRED);

	public TitleScreenFeature() {
		super("title_screen", "Title screen", "A cleaner title screen in the client's style", Category.UTILITY, Icons.LAYOUT_DASHBOARD, true);
	}

	public Background background() {
		return background.get();
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
