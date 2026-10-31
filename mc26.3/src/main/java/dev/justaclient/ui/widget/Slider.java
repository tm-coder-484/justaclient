package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** A labelled slider with the value shown on the right. Snaps to {@code step}; the fill follows on a spring. */
public final class Slider extends Widget {
	private static final float TRACK_H = 3f;

	private final String label;
	private final double min;
	private final double max;
	private final double step;
	private final DoubleSupplier getter;
	private final DoubleConsumer setter;
	private final DoubleFunction<String> format;
	private final Spring fill;
	private boolean dragging;

	public Slider(float x, float y, float width, String label, double min, double max, double step,
		DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format) {
		super(x, y, width, 24f);
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.format = format;
		this.fill = Spring.ui(fraction());
	}

	private float fraction() {
		return (float) ((getter.getAsDouble() - min) / (max - min));
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		fill.setTarget(fraction());
		float t = Mth.clamp(fill.update(), 0f, 1f);
		JacText.draw(g, label, x, y, Colors.fade(Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.REGULAR);
		String value = format.apply(getter.getAsDouble());
		JacText.draw(g, value, x + width - JacText.width(value, Theme.LABEL, JacText.Weight.SEMIBOLD), y,
			Colors.fade(Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.SEMIBOLD);
		float ty = y + 16f;
		Draw.rect(g, x, ty, width, TRACK_H, TRACK_H / 2f, Colors.fade(Theme.CONTROL, alpha));
		Draw.horizontalGradient(g, x, ty, Math.max(TRACK_H, width * t), TRACK_H, TRACK_H / 2f,
			Colors.fade(JacConfig.accentAt(0f), alpha), Colors.fade(JacConfig.accentAt(1f), alpha));
		float r = 4.5f + (dragging ? 0.8f : hover.get() * 0.5f);
		float cx = x + width * t;
		float cy = ty + TRACK_H / 2f;
		Draw.shadow(g, cx - r, cy - r + 1f, r * 2, r * 2, r, 2.5f, Colors.fade(0x55000000, alpha));
		Draw.circle(g, cx, cy, r, Colors.fade(Theme.KNOB, alpha));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOver(mouseX, mouseY)) {
			dragging = true;
			setFromMouse(mouseX);
			return true;
		}
		return false;
	}

	@Override
	public void mouseDragged(double mouseX, double mouseY) {
		if (dragging) {
			setFromMouse(mouseX);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		dragging = false;
	}

	private void setFromMouse(double mouseX) {
		double t = Mth.clamp((mouseX - x) / width, 0.0, 1.0);
		double value = min + (max - min) * t;
		value = Math.round(value / step) * step;
		setter.accept(Mth.clamp(value, min, max));
	}
}
