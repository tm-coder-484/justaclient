package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

/** A rounded button. Primary buttons use the accent; others use the neutral control colour. */
public final class Button extends Widget {
	private final String label;
	private final boolean primary;
	private final Runnable action;
	private final @Nullable Icons icon;

	public Button(float x, float y, float width, float height, String label, boolean primary, Runnable action) {
		this(x, y, width, height, null, label, primary, action);
	}

	public Button(float x, float y, float width, float height, @Nullable Icons icon, String label, boolean primary, Runnable action) {
		super(x, y, width, height);
		this.label = label;
		this.primary = primary;
		this.action = action;
		this.icon = icon;
	}

	@Override
	protected void draw(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		float h = hover.get();
		float radius = Math.min(height / 2f, JacConfig.get().style.cornerRadius);
		if (primary) {
			int a = Colors.lerp(JacConfig.accentAt(0f), 0xFFFFFFFF, h * 0.12f);
			int b = Colors.lerp(JacConfig.accentAt(1f), 0xFFFFFFFF, h * 0.12f);
			Draw.shadow(g, x, y + 1, width, height, radius, 6f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0x40), alpha * (0.6f + h * 0.4f)));
			Draw.horizontalGradient(g, x, y, width, height, radius, Colors.fade(a, alpha), Colors.fade(b, alpha));
		} else {
			Draw.rect(g, x, y, width, height, radius, Colors.fade(Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, h), alpha));
		}
		float ty = y + (height - JacText.lineHeight(Theme.LABEL)) / 2f;
		int color = Colors.fade(primary ? 0xFFFFFFFF : Theme.TEXT, alpha);
		if (icon == null) {
			JacText.drawCentered(g, label, x + width / 2f, ty, color, Theme.LABEL, JacText.Weight.SEMIBOLD);
			return;
		}
		float iconSize = Theme.LABEL + 0.5f;
		float total = iconSize + 4f + JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD);
		float left = x + (width - total) / 2f;
		JacText.icon(g, icon, left, y + (height - iconSize) / 2f, iconSize, color);
		JacText.draw(g, label, left + iconSize + 4f, ty, color, Theme.LABEL, JacText.Weight.SEMIBOLD);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOver(mouseX, mouseY)) {
			action.run();
			return true;
		}
		return false;
	}
}
