package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.storage.StorageIndex;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.DeathRecapFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Scroller;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** {@code /lastdeath}: the recap of your last death in this world, and earlier ones on the left. */
public final class DeathRecapScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 28f;
	private final Scroller scroll = new Scroller();
	private int selected;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float lw;
	private float top;
	private float bottom;
	private DeathRecapFeature.@Nullable Recap shown;
	/** Read once: the history is a file in the world folder, too slow to parse every frame. */
	private @Nullable List<DeathRecapFeature.Recap> history;

	public DeathRecapScreen(@Nullable Screen parent) {
		super("Death recap", parent);
	}

	private List<DeathRecapFeature.Recap> history() {
		if (history == null) {
			history = DeathRecapFeature.get().history();
		}
		return history;
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		float a = t;
		List<DeathRecapFeature.Recap> list = history();
		float cardW = 200f;
		lw = 170f;
		pw = Math.min(width - 24f, lw + cardW + PAD * 3);
		ph = Math.min(height - 24f, 340f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		top = py + 34;
		bottom = py + ph - PAD;
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.SKULL, px + PAD, py + PAD + 1, 10f, Colors.fade(0xFFF87171, a));
		JacText.draw(g, "Deaths in this world", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (list.isEmpty()) {
			JacText.draw(g, "No deaths here yet", px + PAD, top + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			return;
		}
		selected = Math.min(selected, list.size() - 1);
		scroll.setMax(list.size() * ROW - (bottom - top));
		int accent = JacConfig.accent();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) (px + PAD), (int) top, (int) (px + PAD + lw), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float y = top + i * ROW - scroll.get();
			DeathRecapFeature.Recap r = list.get(i);
			boolean hot = mx >= px + PAD && mx < px + PAD + lw && my >= y && my < y + ROW - 2 && my >= top && my < bottom;
			if (i == selected || hot) {
				Draw.rect(g, px + PAD, y, lw, ROW - 2, 4f, Colors.fade(i == selected ? Colors.withAlpha(accent, 0x38) : Theme.CONTROL_HOVER, a));
			}
			JacText.draw(g, fit(r.message, Theme.LABEL, JacText.Weight.SEMIBOLD, lw - 12), px + PAD + 6, y + 4, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			JacText.draw(g, StorageIndex.ago(r.time, System.currentTimeMillis()) + " · alive " + DeathRecapFeature.duration(r.alive), px + PAD + 6, y + 15,
				Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		g.disableScissor();
		shown = list.get(selected);
		float cx = px + PAD * 2 + lw;
		DeathRecapFeature.drawCard(g, shown, cx, top, Math.min(cardW, px + pw - PAD - cx), mx, my, a);
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		if (shown != null && DeathRecapFeature.clickCard(shown, mx, my)) {
			return true;
		}
		if (mx >= px + PAD && mx < px + PAD + lw && my >= top && my < bottom) {
			int i = (int) ((my - top + scroll.get()) / ROW);
			if (i >= 0 && i < history().size()) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				selected = i;
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, ROW * 2);
		return true;
	}
}
