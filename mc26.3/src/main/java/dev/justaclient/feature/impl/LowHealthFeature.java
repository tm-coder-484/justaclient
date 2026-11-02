package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/** A soft glow around the screen edges when your health is low, pulsing faster the lower it gets. */
public final class LowHealthFeature extends Feature {
	private final NumberSetting threshold = number("threshold", "Starts below", 8, 2, 16, 1, v -> String.format("%.0f hearts", v / 2));
	private final NumberSetting strength = number("strength", "Strength", 0.6, 0.1, 1, 0.05, NumberSetting.percent());
	private final NumberSetting size = number("size", "Edge size", 0.18, 0.05, 0.4, 0.01, NumberSetting.percent());
	private final BoolSetting pulse = bool("pulse", "Pulse", true);
	private final ColorSetting color = color("color", "Colour", "#F43F5E");
	private float shown;

	public LowHealthFeature() {
		super("low_health", "Low health glow", "Glowing screen edges when your health is low", Category.HUD, Icons.HEART, true);
	}

	public void render(GuiGraphicsExtractor g) {
		Player player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			shown = 0f;
			return;
		}
		float health = player.getHealth() + player.getAbsorptionAmount();
		float limit = threshold.f();
		float target = health >= limit || player.isDeadOrDying() ? 0f : 1f - health / limit;
		// Ease towards the target so a heal or hit fades the glow instead of snapping it.
		shown += (target - shown) * 0.08f;
		if (shown < 0.01f) {
			return;
		}
		float beat = 1f;
		if (pulse.on()) {
			double hz = 0.8 + shown * 1.6;
			beat = 0.7f + 0.3f * (float) Math.sin(System.nanoTime() / 1e9 * Math.PI * 2 * hz);
		}
		int edge = Colors.fade(color.argb(), Math.min(1f, shown * strength.f() * beat * 1.4f));
		int clear = Colors.withAlpha(color.argb(), 0);
		float w = g.guiWidth();
		float h = g.guiHeight();
		float e = Math.min(w, h) * size.f();
		Draw.verticalGradient(g, 0, 0, w, e, 0f, edge, clear);
		Draw.verticalGradient(g, 0, h - e, w, e, 0f, clear, edge);
		Draw.horizontalGradient(g, 0, 0, e, h, 0f, edge, clear);
		Draw.horizontalGradient(g, w - e, 0, e, h, 0f, clear, edge);
	}
}
