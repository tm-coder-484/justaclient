package dev.justaclient.ui;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * A small "are you sure?" card in the client's style. Any of the client's screens can host one as an overlay, and
 * {@link JacConfirmScreen} hosts one over vanilla screens. Enter confirms, Escape cancels.
 */
public final class ConfirmDialog implements Overlay {
	private static final float W = 220f;
	private static final float PAD = 14f;
	private final Icons icon;
	private final String title;
	private final List<String> lines;
	private final String confirmLabel;
	private final Runnable onConfirm;
	private final Runnable onCancel;
	private final Animation in = new Animation(Easing.CUBIC_OUT, 160, 0f);
	private Button confirm;
	private Button cancel;
	private float x;
	private float y;
	private float h;

	public ConfirmDialog(Icons icon, String title, String message, String confirmLabel, Runnable onConfirm, Runnable onCancel) {
		this.icon = icon;
		this.title = title;
		this.lines = wrap(message, W - PAD * 2);
		this.confirmLabel = confirmLabel;
		this.onConfirm = onConfirm;
		this.onCancel = onCancel;
	}

	private static List<String> wrap(String text, float max) {
		List<String> out = new ArrayList<>();
		for (String paragraph : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && JacText.width(next, Theme.LABEL, JacText.Weight.REGULAR) > max) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(next);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	/** Draws the dimmed backdrop and the card centred on a screen of the given size. */
	@Override
	public void render(GuiGraphics g, int screenW, int screenH, double mx, double my) {
		float t = in.animateTo(1f).get();
		h = PAD + 14f + 8f + lines.size() * 10f + 12f + 18f + PAD;
		x = (screenW - W) / 2f;
		y = (screenH - h) / 2f + (1f - t) * 6f;
		float by = y + h - PAD - 18f;
		float bw = (W - PAD * 2 - 8f) / 2f;
		if (confirm == null) {
			cancel = new Button(0, 0, bw, 18, "Cancel", false, onCancel);
			confirm = new Button(0, 0, bw, 18, confirmLabel, true, onConfirm);
		}
		cancel.setPosition(x + PAD, by);
		confirm.setPosition(x + PAD + bw + 8f, by);
		g.flush();
		Draw.rect(g, 0, 0, screenW, screenH, 0, Colors.fade(0x80000000, t));
		Draw.panel(g, x, y, W, h, t);
		JacText.icon(g, icon, x + PAD, y + PAD + 1f, 10f, Colors.fade(JacConfig.accent(), t));
		JacText.draw(g, title, x + PAD + 16f, y + PAD, Colors.fade(Theme.TEXT, t), Theme.TITLE, JacText.Weight.SEMIBOLD);
		float ly = y + PAD + 22f;
		for (String line : lines) {
			JacText.draw(g, line, x + PAD, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.LABEL, JacText.Weight.REGULAR);
			ly += 10f;
		}
		cancel.render(g, mx, my, t);
		confirm.render(g, mx, my, t);
	}

	/** Every click is taken while the dialog is open; a click outside the card cancels. */
	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (confirm == null) {
			return true;
		}
		if (confirm.mouseClicked(mx, my, button) || cancel.mouseClicked(mx, my, button)) {
			return true;
		}
		if (mx < x || mx >= x + W || my < y || my >= y + h) {
			onCancel.run();
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onCancel.run();
		} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			onConfirm.run();
		}
		return true;
	}
}
