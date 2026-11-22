package dev.justaclient.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.TextBuffer;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

/** A one-line text box for the client's screens: search boxes, names, addresses. */
public final class InputBox {
	public final TextBuffer text;
	private final String placeholder;
	private final @Nullable Icons icon;
	public float x;
	public float y;
	public float w;
	public float h = 16;
	public boolean focused;

	public InputBox(String initial, int max, String placeholder, @Nullable Icons icon) {
		this.text = new TextBuffer(initial, max);
		this.placeholder = placeholder;
		this.icon = icon;
	}

	public String get() {
		return text.get();
	}

	public void set(String value) {
		text.set(value);
		text.end();
	}

	public void place(float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public void draw(GuiGraphics g, double mx, double my, float a) {
		float r = Math.min(5f, JacConfig.get().style.cornerRadius);
		boolean hover = inside(mx, my);
		Draw.rect(g, x, y, w, h, r, Colors.fade(focused || hover ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
		if (focused) {
			Draw.outline(g, x, y, w, h, r, 0.75f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0x90), a));
		}
		float tx = x + 6;
		if (icon != null) {
			JacText.icon(g, icon, x + 6, y + (h - 6.5f) / 2f, 6.5f, Colors.fade(Theme.TEXT_DIM, a));
			tx += 10;
		}
		float ty = y + (h - JacText.lineHeight(Theme.LABEL)) / 2f;
		String value = text.get();
		if (value.isEmpty()) {
			JacText.draw(g, placeholder, tx, ty, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.LABEL, JacText.Weight.REGULAR);
		}
		// Show the end of long text so the caret stays in view.
		String before = value.substring(0, Math.min(value.length(), text.caret()));
		float max = w - (tx - x) - 6;
		int start = 0;
		while (start < before.length() && JacText.width(before.substring(start), Theme.LABEL, JacText.Weight.REGULAR) > max) {
			start++;
		}
		String shown = value.substring(start);
		while (!shown.isEmpty() && JacText.width(shown, Theme.LABEL, JacText.Weight.REGULAR) > max) {
			shown = shown.substring(0, shown.length() - 1);
		}
		JacText.draw(g, shown, tx, ty, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
		if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
			float cx = tx + JacText.width(before.substring(start), Theme.LABEL, JacText.Weight.REGULAR) + 0.5f;
			Draw.rect(g, cx, y + 4, 0.75f, h - 8, 0, Colors.fade(Theme.TEXT, a));
		}
	}

	public boolean inside(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Focuses on a click inside, unfocuses on a click elsewhere. Returns true if the click was inside. */
	public boolean click(double mx, double my) {
		focused = inside(mx, my);
		if (focused) {
			text.end();
		}
		return focused;
	}

	/** Handles editing keys while focused. Returns true if the key was used. */
	public boolean key(int key, boolean ctrl) {
		if (!focused) {
			return false;
		}
		switch (key) {
			case InputConstants.KEY_BACKSPACE -> text.backspace(ctrl);
			case InputConstants.KEY_DELETE -> text.delete();
			case InputConstants.KEY_LEFT -> text.left(ctrl);
			case InputConstants.KEY_RIGHT -> text.right(ctrl);
			case InputConstants.KEY_HOME -> text.home();
			case InputConstants.KEY_END -> text.end();
			case InputConstants.KEY_ESCAPE -> focused = false;
			default -> {
				if (ctrl && key == InputConstants.KEY_V) {
					text.insert(JacScreen.clipboard().replace("\r", "").replace('\n', ' '));
				} else if (ctrl && key == InputConstants.KEY_A) {
					text.end();
				} else {
					return false;
				}
			}
		}
		return true;
	}

	public boolean typed(String s) {
		if (!focused) {
			return false;
		}
		text.insert(s);
		return true;
	}
}
