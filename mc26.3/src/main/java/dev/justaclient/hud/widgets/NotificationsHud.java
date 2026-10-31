package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Toasts for feature toggles and quick actions. They slide in, wait, then fade out. */
public final class NotificationsHud extends HudFeature {
	private static final long SHOW_NANOS = 2_500_000_000L;
	private static final long FADE_NANOS = 250_000_000L;
	private static final float TOAST_H = 18f;
	private static final float GAP = 3f;
	private final List<Notifications.Toast> visible = new ArrayList<>();

	public NotificationsHud() {
		super("notifications", "Notifications", "Toasts when you toggle a feature with its key", Icons.BELL, true, 1f, 0.62f);
	}

	@Override
	public void layout(boolean preview) {
		visible.clear();
		long now = System.nanoTime();
		Notifications.toasts().removeIf(t -> now - t.createdNanos() > SHOW_NANOS + FADE_NANOS);
		if (preview && Notifications.toasts().isEmpty()) {
			visible.add(new Notifications.Toast(Icons.ZOOM_IN, "Zoom", "turned on", true, true, now));
		} else {
			visible.addAll(Notifications.toasts());
		}
		float w = 0f;
		for (Notifications.Toast t : visible) {
			w = Math.max(w, toastWidth(t));
		}
		width = Math.max(80f, w);
		height = Math.max(TOAST_H, visible.size() * (TOAST_H + GAP) - GAP);
	}

	private static float toastWidth(Notifications.Toast t) {
		return PAD + 16f + 5f + JacText.width(t.bold(), TEXT, JacText.Weight.SEMIBOLD) + 3f + JacText.width(t.rest(), TEXT, JacText.Weight.REGULAR) + PAD + 2f;
	}

	@Override
	public boolean hasContent() {
		return !visible.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		long now = System.nanoTime();
		double motion = JacConfig.get().style.motion.scale;
		// Newest at the bottom, so stacks grow upwards from where the widget sits.
		for (int i = 0; i < visible.size(); i++) {
			Notifications.Toast t = visible.get(i);
			long age = preview ? FADE_NANOS : now - t.createdNanos();
			float in = motion <= 0 ? 1f : Easing.CUBIC_OUT.apply(Math.min(1f, age / (float) (FADE_NANOS * motion)));
			float out = age > SHOW_NANOS ? 1f - Math.min(1f, (age - SHOW_NANOS) / (float) FADE_NANOS) : 1f;
			float alpha = in * out;
			float w = toastWidth(t);
			float x = width - w + (1f - in) * 12f;
			float y = height - (i + 1) * (TOAST_H + GAP) + GAP;
			if (background.on()) {
				StyleTokens style = JacConfig.get().style;
				Draw.shadow(g, x, y + 1, w, TOAST_H, style.cornerRadius, 8f, Colors.fade(0x55000000, alpha));
				Draw.rect(g, x, y, w, TOAST_H, style.cornerRadius, Colors.fade(JacConfig.surface() | 0xE0000000, alpha));
				Draw.rect(g, x, y + 3f, 1.5f, TOAST_H - 6f, 0.75f, Colors.fade(JacConfig.accent(), alpha));
			}
			float sx = x + PAD;
			float sy = y + (TOAST_H - 8f) / 2f;
			if (t.toggle()) {
				// A small switch shows the new state at a glance.
				int track = t.on() ? JacConfig.accent() : Theme.CONTROL_HOVER;
				Draw.rect(g, sx, sy, 14f, 8f, 4f, Colors.fade(track, alpha));
				Draw.circle(g, sx + (t.on() ? 10f : 4f), sy + 4f, 2.8f, Colors.fade(Theme.KNOB, alpha));
			} else {
				JacText.icon(g, t.icon(), sx + 3f, sy, 8f, Colors.fade(JacConfig.accent(), alpha));
			}
			float tx = sx + 16f + 5f;
			float ty = y + (TOAST_H - JacText.lineHeight(TEXT)) / 2f;
			text(g, t.bold(), tx, ty, Colors.fade(Theme.TEXT, alpha), TEXT, JacText.Weight.SEMIBOLD);
			text(g, t.rest(), tx + JacText.width(t.bold(), TEXT, JacText.Weight.SEMIBOLD) + 3f, ty, Colors.fade(Theme.TEXT_DIM, alpha), TEXT,
				JacText.Weight.REGULAR);
		}
	}
}
