package dev.justaclient.ui.widget;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A colour setting: the theme accent or one of the presets. The accent swatch carries a small dot to tell it apart. */
public final class ColorPicker extends Widget {
	private static final float LABEL_GAP = 12f;
	private static final float R = 5.5f;

	private final ColorSetting setting;
	private final Animation[] rings;

	public ColorPicker(float x, float y, float width, ColorSetting setting) {
		super(x, y, width, LABEL_GAP + R * 2 + 4f);
		this.setting = setting;
		this.rings = new Animation[ColorSetting.PRESETS.size()];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = new Animation(Easing.BACK_OUT, 260, selected(i) ? 1f : 0f);
		}
	}

	private boolean selected(int i) {
		return ColorSetting.PRESETS.get(i).equalsIgnoreCase(setting.get());
	}

	private float spacing() {
		return (width - R * 2 - 2f) / Math.max(1, ColorSetting.PRESETS.size() - 1);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, setting.name, x, y, Colors.fade(Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.REGULAR);
		String value = ColorSetting.ACCENT.equals(setting.get()) ? "Accent" : setting.get().toUpperCase();
		JacText.draw(g, value, x + width - JacText.width(value, Theme.SMALL, JacText.Weight.REGULAR), y + 0.5f,
			Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float cy = y + LABEL_GAP + R + 2f;
		for (int i = 0; i < ColorSetting.PRESETS.size(); i++) {
			float cx = x + 1f + R + spacing() * i;
			String preset = ColorSetting.PRESETS.get(i);
			float ring = rings[i].animateTo(selected(i) ? 1f : 0f).get();
			if (ring > 0.01f) {
				float rr = R + 2.2f * ring;
				Draw.outline(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, 1.1f, Colors.fade(0xFFFFFFFF, alpha * Math.min(1f, ring)));
			}
			Draw.circle(g, cx, cy, R - 0.5f * ring, Colors.fade(ColorSetting.resolve(preset), alpha));
			if (ColorSetting.ACCENT.equals(preset)) {
				Draw.circle(g, cx, cy, 1.4f, Colors.fade(0xFFFFFFFF, alpha * 0.9f));
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) {
			return false;
		}
		float cy = y + LABEL_GAP + R + 2f;
		for (int i = 0; i < ColorSetting.PRESETS.size(); i++) {
			float cx = x + 1f + R + spacing() * i;
			double dx = mouseX - cx;
			double dy = mouseY - cy;
			if (dx * dx + dy * dy <= (R + 3) * (R + 3)) {
				setting.set(ColorSetting.PRESETS.get(i));
				return true;
			}
		}
		return false;
	}
}
