package dev.justaclient.ui.widget;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A labelled row of colour presets. The selection ring grows in around the picked colour. */
public final class Swatches extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float R = 6f;

	private final String label;
	private final List<String> colors;
	private final Supplier<String> getter;
	private final Consumer<String> setter;
	private final Animation[] rings;

	public Swatches(float x, float y, float width, String label, List<String> colors, Supplier<String> getter, Consumer<String> setter) {
		super(x, y, width, LABEL_GAP + R * 2 + 4f);
		this.label = label;
		this.colors = colors;
		this.getter = getter;
		this.setter = setter;
		this.rings = new Animation[colors.size()];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = new Animation(Easing.BACK_OUT, 260, colors.get(i).equalsIgnoreCase(getter.get()) ? 1f : 0f);
		}
	}

	private float spacing() {
		return (width - R * 2) / Math.max(1, colors.size() - 1);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, label, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float cy = y + LABEL_GAP + R + 2f;
		for (int i = 0; i < colors.size(); i++) {
			float cx = x + R + spacing() * i;
			int color = Colors.parse(colors.get(i), 0xFFFFFFFF);
			float ring = rings[i].animateTo(colors.get(i).equalsIgnoreCase(getter.get()) ? 1f : 0f).get();
			if (ring > 0.01f) {
				float rr = R + 2.5f * ring;
				Draw.outline(g, cx - rr, cy - rr, rr * 2, rr * 2, rr, 1.2f, Colors.fade(0xFFFFFFFF, alpha * Math.min(1f, ring)));
			}
			Draw.circle(g, cx, cy, R - 0.5f * ring, Colors.fade(color, alpha));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY)) {
			return false;
		}
		float cy = y + LABEL_GAP + R + 2f;
		for (int i = 0; i < colors.size(); i++) {
			float cx = x + R + spacing() * i;
			double dx = mouseX - cx;
			double dy = mouseY - cy;
			if (dx * dx + dy * dy <= (R + 3) * (R + 3)) {
				setter.accept(colors.get(i));
				return true;
			}
		}
		return false;
	}
}
