package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** The world loading screen as a glass card with a smooth progress bar and spinner, over the blurred panorama. */
public final class LoadingScreenFeature extends Feature {
	private final BoolSetting percent = bool("percent", "Show percentage", true);
	private float shownProgress;

	public LoadingScreenFeature() {
		super("loading_screen", "Loading screen", "A cleaner world loading screen", Category.UTILITY, Icons.DOWNLOAD, true);
	}

	public void draw(GuiGraphics g, int width, int height, String label, boolean hasProgress, float progress) {
		float w = 200f;
		float h = hasProgress ? 58f : 46f;
		float x = (width - w) / 2f;
		float y = (height - h) / 2f;
		Draw.panel(g, x, y, w, h, 1f);
		// Spinner: an accent arc chasing round a faint ring.
		float t = (System.nanoTime() % 4_000_000_000L) / 4e9f;
		float cx = x + 20f;
		float cy = y + 23f;
		Draw.arc(g, cx, cy, 8f, 2f, 0f, 1f, Theme.CONTROL);
		float sweep = 0.25f + 0.15f * (float) Math.sin(t * Math.PI * 4);
		Draw.arc(g, cx, cy, 8f, 2f, t * 2f % 1f, sweep, JacConfig.accent());
		JacText.draw(g, label, x + 36f, y + 15f, Theme.TEXT, Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, hasProgress ? "Preparing the world" : "Almost there", x + 36f, y + 26f, Theme.TEXT_DIM, Theme.SMALL, JacText.Weight.REGULAR);
		if (hasProgress) {
			shownProgress += (progress - shownProgress) * 0.15f;
			float bx = x + 12f;
			float by = y + h - 14f;
			float bw = w - 24f - (percent.on() ? 26f : 0f);
			Draw.rect(g, bx, by, bw, 4f, 2f, Theme.CONTROL);
			Draw.horizontalGradient(g, bx, by, Math.max(4f, bw * shownProgress), 4f, 2f, JacConfig.accentAt(0f), JacConfig.accentAt(1f));
			if (percent.on()) {
				String p = Math.round(shownProgress * 100) + "%";
				JacText.draw(g, p, x + w - 12f - JacText.width(p, Theme.SMALL, JacText.Weight.SEMIBOLD), by - 1.5f, Colors.fade(Theme.TEXT, 0.9f),
					Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		} else {
			shownProgress = 0f;
		}
	}
}
