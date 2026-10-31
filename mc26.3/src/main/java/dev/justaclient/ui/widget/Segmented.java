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
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A labelled single-choice control; the highlight slides between options. Options wrap onto more rows when they do not fit. */
public final class Segmented<T> extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float BAR_H = 16f;
	private static final float ROW_GAP = 3f;

	private final String label;
	private final List<T> options;
	private final List<String> names;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final Spring slide;
	private final Spring slideRow;
	private final int perRow;

	public Segmented(float x, float y, float width, String label, List<T> options, List<String> names, Supplier<T> getter, Consumer<T> setter) {
		this(x, y, width, label, options, names, getter, setter, perRow(width, names));
	}

	private Segmented(float x, float y, float width, String label, List<T> options, List<String> names, Supplier<T> getter, Consumer<T> setter,
		int perRow) {
		super(x, y, width, LABEL_GAP + BAR_H * rows(options.size(), perRow) + ROW_GAP * (rows(options.size(), perRow) - 1));
		this.label = label;
		this.options = options;
		this.names = names;
		this.getter = getter;
		this.setter = setter;
		this.perRow = perRow;
		int index = Math.max(0, options.indexOf(getter.get()));
		this.slide = Spring.ui(index % perRow);
		this.slideRow = Spring.ui(index / perRow);
	}

	/** The most options per row that still leaves every label room to breathe. */
	private static int perRow(float width, List<String> names) {
		float widest = 0f;
		for (String name : names) {
			widest = Math.max(widest, JacText.width(name, Theme.LABEL, JacText.Weight.SEMIBOLD));
		}
		int fit = Math.max(1, (int) (width / (widest + 10f)));
		int n = names.size();
		if (fit >= n) {
			return n;
		}
		// Spread the options evenly over the rows instead of leaving a short last row.
		int rows = rows(n, fit);
		return (n + rows - 1) / rows;
	}

	private static int rows(int count, int perRow) {
		return (count + perRow - 1) / perRow;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, label, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float top = y + LABEL_GAP;
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		int rows = rows(options.size(), perRow);
		for (int r = 0; r < rows; r++) {
			Draw.rect(g, x, top + r * (BAR_H + ROW_GAP), width, BAR_H, radius, Colors.fade(Theme.CONTROL, alpha));
		}
		int selected = Math.max(0, options.indexOf(getter.get()));
		slide.setTarget(selected % perRow);
		slideRow.setTarget(selected / perRow);
		float cell = width / perRow;
		float pos = slide.update();
		float row = slideRow.update();
		Draw.horizontalGradient(g, x + pos * cell + 1.5f, top + row * (BAR_H + ROW_GAP) + 1.5f, cell - 3f, BAR_H - 3f, Math.max(0f, radius - 1f),
			Colors.fade(JacConfig.accentAt(0f), alpha), Colors.fade(JacConfig.accentAt(1f), alpha));
		for (int i = 0; i < options.size(); i++) {
			int col = i % perRow;
			int r = i / perRow;
			float closeness = Math.max(0f, 1f - (float) Math.hypot(pos - col, row - r));
			int color = Colors.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, closeness);
			float textY = top + r * (BAR_H + ROW_GAP) + (BAR_H - JacText.lineHeight(Theme.LABEL)) / 2f;
			JacText.drawCentered(g, names.get(i), x + cell * col + cell / 2f, textY, Colors.fade(color, alpha), Theme.LABEL,
				i == selected ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !isMouseOver(mouseX, mouseY) || mouseY < y + LABEL_GAP) {
			return false;
		}
		int col = (int) ((mouseX - x) / (width / perRow));
		int row = (int) ((mouseY - y - LABEL_GAP) / (BAR_H + ROW_GAP));
		int index = row * perRow + col;
		if (col >= 0 && col < perRow && index >= 0 && index < options.size()) {
			setter.accept(options.get(index));
			return true;
		}
		return false;
	}
}
