package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;

/** A labelled on/off switch. The knob slides on a spring and the track fades to the accent. */
public final class Toggle extends Widget {
	private static final float TRACK_W = 22f;
	private static final float TRACK_H = 12f;

	private final String label;
	private final Supplier<Boolean> getter;
	private final Consumer<Boolean> setter;
	private final Spring knob;

	public Toggle(float x, float y, float width, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		super(x, y, width, 14f);
		this.label = label;
		this.getter = getter;
		this.setter = setter;
		this.knob = Spring.ui(getter.get() ? 1f : 0f);
	}

	@Override
	protected void draw(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		knob.setTarget(getter.get() ? 1f : 0f);
		float t = knob.update();
		JacText.draw(g, label, x, y + (height - JacText.lineHeight(Theme.LABEL)) / 2f, Colors.fade(Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.REGULAR);
		float tx = x + width - TRACK_W;
		float ty = y + (height - TRACK_H) / 2f;
		int off = Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, hover.get());
		int track = Colors.lerp(off, JacConfig.accent(), Math.max(0f, Math.min(1f, t)));
		Draw.rect(g, tx, ty, TRACK_W, TRACK_H, TRACK_H / 2f, Colors.fade(track, alpha));
		float r = TRACK_H / 2f - 1.5f;
		float cx = tx + TRACK_H / 2f + (TRACK_W - TRACK_H) * t;
		Draw.shadow(g, cx - r, ty + 1.5f, r * 2, r * 2, r, 2f, Colors.fade(0x50000000, alpha));
		Draw.circle(g, cx, ty + TRACK_H / 2f, r, Colors.fade(Theme.KNOB, alpha));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isMouseOver(mouseX, mouseY)) {
			setter.accept(!getter.get());
			return true;
		}
		return false;
	}
}
