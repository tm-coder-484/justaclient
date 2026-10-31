package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;

/** A labelled single-choice control; the highlight slides between options. */
public final class Segmented<T> extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float BAR_H = 16f;

	private final String label;
	private final List<T> options;
	private final List<String> names;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final Spring slide;

	public Segmented(float x, float y, float width, String label, List<T> options, List<String> names, Supplier<T> getter, Consumer<T> setter) {
		super(x, y, width, LABEL_GAP + BAR_H);
		this.label = label;
		this.options = options;
		this.names = names;
		this.getter = getter;
		this.setter = setter;
		this.slide = Spring.ui(Math.max(0, options.indexOf(getter.get())));
	}

	@Override
	protected void draw(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, label, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float by = y + LABEL_GAP;
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		Draw.rect(g, x, by, width, BAR_H, radius, Colors.fade(Theme.CONTROL, alpha));
		int selected = Math.max(0, options.indexOf(getter.get()));
		slide.setTarget(selected);
		float cell = width / options.size();
		float pos = slide.update();
		Draw.horizontalGradient(g, x + pos * cell + 1.5f, by + 1.5f, cell - 3f, BAR_H - 3f, Math.max(0f, radius - 1f),
			Colors.fade(JacConfig.accentAt(0f), alpha), Colors.fade(JacConfig.accentAt(1f), alpha));
		float textY = by + (BAR_H - JacText.lineHeight(Theme.LABEL)) / 2f;
		for (int i = 0; i < options.size(); i++) {
			float closeness = Math.max(0f, 1f - Math.abs(pos - i));
			int color = Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, closeness);
			JacText.drawCentered(g, names.get(i), x + cell * i + cell / 2f, textY, Colors.fade(color, alpha), Theme.LABEL,
				i == selected ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY) || mouseY < y + LABEL_GAP) {
			return false;
		}
		int index = (int) ((mouseX - x) / (width / options.size()));
		if (index >= 0 && index < options.size()) {
			setter.accept(options.get(index));
			return true;
		}
		return false;
	}
}
