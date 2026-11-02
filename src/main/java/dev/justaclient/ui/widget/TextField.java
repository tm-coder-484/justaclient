package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** A one-line text box. Click to type, Enter or a click elsewhere to finish, Ctrl+V pastes. */
public final class TextField extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float BOX_H = 15f;

	private final TextSetting setting;
	private boolean editing;

	public TextField(float x, float y, float width, TextSetting setting) {
		super(x, y, width, LABEL_GAP + BOX_H);
		this.setting = setting;
	}

	@Override
	protected void draw(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		JacText.draw(g, setting.name, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float by = y + LABEL_GAP;
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		int fill = Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, editing ? 1f : hover.get());
		Draw.rect(g, x, by, width, BOX_H, radius, Colors.fade(fill, alpha));
		if (editing) {
			Draw.outline(g, x, by, width, BOX_H, radius, 1f, Colors.fade(JacConfig.accent(), alpha));
		}
		String text = setting.get();
		boolean empty = text.isEmpty();
		String shown = empty && !editing ? setting.placeholder : text;
		// Keep the end of long text in view while typing.
		float max = width - 10f;
		while (shown.length() > 1 && JacText.width(shown, Theme.LABEL, JacText.Weight.REGULAR) > max) {
			shown = shown.substring(1);
		}
		float ty = by + (BOX_H - JacText.lineHeight(Theme.LABEL)) / 2f;
		JacText.draw(g, shown, x + 5f, ty, Colors.fade(empty && !editing ? Theme.TEXT_DIM : Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.REGULAR);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			float cx = x + 5f + (empty ? 0f : JacText.width(shown, Theme.LABEL, JacText.Weight.REGULAR)) + 0.5f;
			Draw.rect(g, cx, by + 3.5f, 1f, BOX_H - 7f, 0f, Colors.fade(JacConfig.accent(), alpha));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean inside = isMouseOver(mouseX, mouseY) && mouseY >= y + LABEL_GAP;
		editing = inside && button == 0;
		return inside;
	}

	@Override
	public boolean capturesKeys() {
		return editing;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!editing) {
			return false;
		}
		String text = setting.get();
		switch (key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> editing = false;
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!text.isEmpty()) {
					setting.set(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
				}
			}
			case GLFW.GLFW_KEY_V -> {
				long window = Minecraft.getInstance().getWindow().handle();
				boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
					|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
					|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS;
				if (ctrl) {
					String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " ");
					setting.set(text + clip);
				}
			}
			default -> {
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!editing) {
			return false;
		}
		setting.set(setting.get() + Character.toString(codepoint));
		return true;
	}
}
