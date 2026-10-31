package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.Segmented;
import dev.justaclient.ui.widget.Slider;
import dev.justaclient.ui.widget.Swatches;
import dev.justaclient.ui.widget.Toggle;
import dev.justaclient.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** The Theme page of the click GUI: live controls for every style token. */
final class ThemePage {
	private static final List<String> ACCENTS = List.of("#4C8DFF", "#8B5CF6", "#14B8A6", "#22C55E", "#F59E0B", "#F43F5E", "#E5E7EB");

	private static final List<String> KEYWORDS = List.of("theme", "accent", "colour", "color", "rainbow", "panel", "opacity", "corner", "radius",
		"blur", "glass", "outline", "glow", "font", "motion", "animation", "style");

	private ThemePage() {
	}

	static boolean matches(String query) {
		String q = query.toLowerCase(java.util.Locale.ROOT).trim();
		return KEYWORDS.stream().anyMatch(k -> q.length() >= 3 && k.contains(q) || q.contains(k));
	}

	static List<Widget> build(float left, float leftY, float right, float rightY, float col, Runnable reset) {
		StyleTokens s = JacConfig.get().style;
		List<Widget> widgets = new ArrayList<>();
		widgets.add(new Swatches(left, leftY, col, "Accent", ACCENTS, () -> s.accent, v -> {
			s.accent = v;
			s.rainbowAccent = false;
		}));
		widgets.add(new Toggle(left, leftY + 32, col, "Rainbow accent", () -> s.rainbowAccent, v -> s.rainbowAccent = v));
		widgets.add(new Slider(left, leftY + 54, col, "Panel opacity", 0, 1, 0.01,
			() -> s.surfaceOpacity, v -> s.surfaceOpacity = (float) v, v -> Math.round(v * 100) + "%"));
		widgets.add(new Slider(left, leftY + 84, col, "Corner radius", 0, 16, 1,
			() -> s.cornerRadius, v -> s.cornerRadius = (float) v, v -> Math.round(v) + " px"));
		widgets.add(new Toggle(left, leftY + 114, col, "Background blur", () -> s.blur, v -> s.blur = v));
		widgets.add(new Toggle(left, leftY + 134, col, "Blur behind HUD widgets", () -> s.hudBlur, v -> s.hudBlur = v));
		widgets.add(new Slider(left, leftY + 154, col, "Blur strength", 1, 5, 1,
			() -> s.blurStrength, v -> s.blurStrength = (int) Math.round(v), v -> String.valueOf(Math.round(v))));

		widgets.add(new Segmented<>(right, rightY, col, "Outline", List.of(StyleTokens.Outline.values()), List.of("Off", "Thin", "Glow"),
			() -> s.outline, v -> s.outline = v));
		widgets.add(new Segmented<>(right, rightY + 34, col, "Font", List.of(StyleTokens.FontChoice.values()), List.of("Inter", "Vanilla"),
			() -> s.font, v -> s.font = v));
		widgets.add(new Segmented<>(right, rightY + 68, col, "Motion", List.of(StyleTokens.Motion.values()), List.of("Off", "Normal", "Fast"),
			() -> s.motion, v -> s.motion = v));
		widgets.add(new Button(right, rightY + 108, col, 18, "Reset to dark glass", false, reset));
		return widgets;
	}

	/** Accent strip and two text sizes, so changes to accent and font are visible at a glance. */
	static void drawPreview(GuiGraphics g, float x, float y, float alpha) {
		Draw.horizontalGradient(g, x, y, 3f, 18f, 1.5f, Colors.fade(JacConfig.accentAt(0f), alpha), Colors.fade(JacConfig.accentAt(1f), alpha));
		JacText.draw(g, "Crisp at any scale", x + 9, y, Colors.fade(Theme.TEXT, alpha), Theme.LABEL + 1, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "The quick brown fox jumps over the lazy dog", x + 9, y + 11, Colors.fade(Theme.TEXT_DIM, alpha), 5.5f, JacText.Weight.REGULAR);
	}
}
