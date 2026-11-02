package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Click, then press a key to bind it. Escape, Backspace or Delete clears the binding. */
public final class KeyBinder extends Widget {
	private static final float CHIP_H = 13f;

	private final KeySetting setting;
	private boolean listening;

	public KeyBinder(float x, float y, float width, KeySetting setting) {
		super(x, y, width, 14f);
		this.setting = setting;
	}

	@Override
	protected void draw(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, setting.name, x, y + (height - JacText.lineHeight(Theme.LABEL)) / 2f, Colors.fade(Theme.TEXT, alpha), Theme.LABEL,
			JacText.Weight.REGULAR);
		String text = listening ? "Press a key" : setting.keyName();
		float chipW = Math.max(34f, JacText.width(text, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12f);
		float cx = x + width - chipW;
		float cy = y + (height - CHIP_H) / 2f;
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		int fill = listening ? Colors.withAlpha(JacConfig.accent(), 0x50) : Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, hover.get());
		Draw.rect(g, cx, cy, chipW, CHIP_H, radius, Colors.fade(fill, alpha));
		if (listening) {
			Draw.outline(g, cx, cy, chipW, CHIP_H, radius, 1f, Colors.fade(JacConfig.accent(), alpha));
		}
		int color = setting.isBound() || listening ? Theme.TEXT : Theme.TEXT_DIM;
		JacText.drawCentered(g, text, cx + chipW / 2f, cy + (CHIP_H - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(color, alpha), Theme.SMALL,
			JacText.Weight.SEMIBOLD);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isMouseOver(mouseX, mouseY) && button == 0) {
			listening = !listening;
			return true;
		}
		listening = false;
		return false;
	}

	@Override
	public boolean capturesKeys() {
		return listening;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!listening) {
			return false;
		}
		boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
		setting.set(clear ? KeySetting.NONE : key);
		listening = false;
		return true;
	}
}
