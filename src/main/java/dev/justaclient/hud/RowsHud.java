package dev.justaclient.hud;

import dev.justaclient.anim.Spring;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** A panel of "label ... value" rows, like the info and coordinates widgets. The width eases so numbers never jitter it. */
public abstract class RowsHud extends HudFeature {
	private static final float GAP = 12f;
	private static final float MIN_WIDTH = 64f;

	public record Row(String label, String value) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final Spring widthSpring = Spring.ui(MIN_WIDTH);
	private boolean first = true;

	protected RowsHud(String id, String name, String description, Icons icon, boolean enabledByDefault, float x, float y) {
		super(id, name, description, icon, enabledByDefault, x, y);
	}

	protected abstract void collect(List<Row> rows, boolean preview);

	@Override
	public void layout(boolean preview) {
		rows.clear();
		collect(rows, preview);
		float target = MIN_WIDTH;
		float h = PAD * 2 - (ROW - JacText.lineHeight(TEXT));
		for (Row row : rows) {
			float w = JacText.width(row.label(), TEXT, JacText.Weight.REGULAR) + GAP + JacText.width(row.value(), TEXT, JacText.Weight.SEMIBOLD);
			target = Math.max(target, w + PAD * 2);
			h += ROW;
		}
		// Round up to whole pixels so small value changes do not restart the spring every frame.
		widthSpring.setTarget((float) Math.ceil(target / 4f) * 4f);
		if (first) {
			widthSpring.snap((float) Math.ceil(target / 4f) * 4f);
			first = false;
		}
		width = widthSpring.update();
		height = h;
	}

	@Override
	public boolean hasContent() {
		return !rows.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float y = PAD;
		for (Row row : rows) {
			text(g, row.label(), PAD, y, Theme.TEXT_DIM, TEXT, JacText.Weight.REGULAR);
			textRight(g, row.value(), width - PAD, y, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
			y += ROW;
		}
	}
}
