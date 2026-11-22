package dev.justaclient.ui.widget;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

/** A small right-click menu for the client's screens. */
public final class PopupMenu {
	private static final float ROW = 14f;

	public record Item(@Nullable Icons icon, String label, boolean enabled, Runnable action, boolean header) {
	}

	private final List<Item> items = new ArrayList<>();
	private final Animation in = new Animation(Easing.CUBIC_OUT, 140, 0f);
	private float x;
	private float y;
	private float w;

	public PopupMenu add(@Nullable Icons icon, String label, Runnable action) {
		items.add(new Item(icon, label, true, action, false));
		return this;
	}

	public PopupMenu add(@Nullable Icons icon, String label, boolean enabled, Runnable action) {
		items.add(new Item(icon, label, enabled, action, false));
		return this;
	}

	public PopupMenu header(String label) {
		items.add(new Item(null, label, false, () -> {
		}, true));
		return this;
	}

	/** Places the menu at the mouse, kept inside the screen. */
	public PopupMenu open(double mx, double my, int screenW, int screenH) {
		w = 90;
		for (Item item : items) {
			w = Math.max(w, JacText.width(item.label, Theme.SMALL + 0.5f, JacText.Weight.REGULAR) + (item.icon != null ? 30 : 18));
		}
		float h = height();
		x = (float) Math.min(mx, screenW - w - 4);
		y = (float) Math.min(my, screenH - h - 4);
		return this;
	}

	/** Places the menu so its bottom edge sits at {@code bottom}, for buttons along the bottom of a screen. */
	public PopupMenu openAbove(double x, double bottom, int screenW, int screenH) {
		open(x, bottom, screenW, screenH);
		y = (float) Math.max(4, bottom - height());
		return this;
	}

	private float height() {
		return items.size() * ROW + 6;
	}

	public void draw(GuiGraphics g, double mx, double my) {
		float t = in.animateTo(1f).get();
		float h = height();
		Draw.shadow(g, x, y + 2, w, h, 6f, 10f, Colors.fade(0x66000000, t));
		Draw.rect(g, x, y, w, h, 6f, Colors.fade(Colors.withAlpha(Colors.lerp(Colors.withAlpha(JacConfig.surface(), 0xFF), 0xFF000000, 0.3f), 0xF4), t));
		Draw.outline(g, x, y, w, h, 6f, 0.75f, Colors.fade(0x22FFFFFF, t));
		float ry = y + 3;
		for (Item item : items) {
			boolean hot = item.enabled && mx >= x && mx < x + w && my >= ry && my < ry + ROW;
			if (hot) {
				Draw.rect(g, x + 3, ry, w - 6, ROW, 4f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0x40), t));
			}
			int color = item.header ? Theme.TEXT_DIM : item.enabled ? Theme.TEXT : 0x60FFFFFF;
			float tx = x + 8;
			if (item.icon != null) {
				JacText.icon(g, item.icon, tx, ry + 3.5f, 7f, Colors.fade(color, t));
				tx += 12;
			}
			JacText.draw(g, item.label, tx, ry + 3.5f, Colors.fade(color, t), item.header ? Theme.SMALL : Theme.SMALL + 0.5f,
				item.header ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
			ry += ROW;
		}
	}

	/** Runs the clicked item. Returns true if the click was inside the menu; the caller closes the menu either way. */
	public boolean click(double mx, double my) {
		if (mx < x || mx >= x + w || my < y || my >= y + height()) {
			return false;
		}
		int i = (int) ((my - y - 3) / ROW);
		if (i >= 0 && i < items.size() && items.get(i).enabled) {
			items.get(i).action.run();
		}
		return true;
	}
}
