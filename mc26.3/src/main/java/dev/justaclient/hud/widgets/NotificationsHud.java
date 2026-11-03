package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The notification feed: feature toggles, pickups ("+12 Cobblestone"), "Inventory full" and messages from other
 * features. Toasts slide in, stack while more of the same arrive, then fade out. Each kind has its own switch.
 */
public final class NotificationsHud extends HudFeature {
	private static final long FADE_NANOS = 250_000_000L;
	private static final long BUMP_NANOS = 180_000_000L;
	private static final float TOAST_H = 18f;
	private static final float GAP = 3f;
	private final BoolSetting toggles = bool("toggles", "Feature toggles", true);
	private final BoolSetting pickups = bool("pickups", "Pickups", true);
	private final BoolSetting full = bool("inventory_full", "Inventory full", true);
	private final BoolSetting other = bool("other", "Other messages", true);
	private final NumberSetting seconds = number("seconds", "Show for", 2.5, 1, 8, 0.5, NumberSetting.decimal(" s"));
	private final NumberSetting max = number("max", "Most at once", 4, 1, 8, 1, NumberSetting.unit(""));
	private final NumberSetting opacity = number("opacity", "Opacity", 1.0, 0.2, 1.0, 0.05, NumberSetting.percent());
	private final List<Notifications.Toast> visible = new ArrayList<>();

	public NotificationsHud() {
		super("notifications", "Notifications", "Toggles, pickups, inventory full and other popups", Icons.BELL, true, 1f, 0.62f);
	}

	public boolean shows(Notifications.Source source) {
		return switch (source) {
			case TOGGLE -> toggles.on();
			case PICKUP -> pickups.on();
			case INVENTORY_FULL -> full.on();
			case GENERAL -> other.on();
		};
	}

	public int maxToasts() {
		return max.i();
	}

	private long showNanos() {
		return (long) (seconds.get() * 1e9);
	}

	@Override
	public void layout(boolean preview) {
		visible.clear();
		long now = System.nanoTime();
		Notifications.expire(now, showNanos() + FADE_NANOS);
		if (preview && Notifications.toasts().isEmpty()) {
			Notifications.Toast pickup = new Notifications.Toast(null, Notifications.Source.PICKUP, Icons.BOX, "+12", "Cobblestone", false, false, now);
			pickup.stack = new ItemStack(Items.COBBLESTONE);
			visible.add(pickup);
			visible.add(new Notifications.Toast(null, Notifications.Source.TOGGLE, Icons.ZOOM_IN, "Zoom", "turned on", true, true, now));
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
		return PAD + 16f + 5f + JacText.width(t.bold, TEXT, JacText.Weight.SEMIBOLD) + 3f + JacText.width(t.rest, TEXT, JacText.Weight.REGULAR) + PAD + 2f;
	}

	@Override
	public boolean hasContent() {
		return !visible.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		long now = System.nanoTime();
		long show = showNanos();
		double motion = JacConfig.get().style.motion.scale;
		float fade = opacity.f();
		// Newest at the bottom, so stacks grow upwards from where the widget sits.
		for (int i = 0; i < visible.size(); i++) {
			Notifications.Toast t = visible.get(i);
			long age = preview ? FADE_NANOS : now - t.firstNanos;
			long life = preview ? 0 : now - t.createdNanos;
			float in = motion <= 0 ? 1f : Easing.CUBIC_OUT.apply(Math.min(1f, age / (float) (FADE_NANOS * motion)));
			float out = life > show ? 1f - Math.min(1f, (life - show) / (float) FADE_NANOS) : 1f;
			float alpha = in * out * fade;
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
			if (t.toggle) {
				// A small switch shows the new state at a glance.
				int track = t.on ? JacConfig.accent() : Theme.CONTROL_HOVER;
				Draw.rect(g, sx, sy, 14f, 8f, 4f, Colors.fade(track, alpha));
				Draw.circle(g, sx + (t.on ? 10f : 4f), sy + 4f, 2.8f, Colors.fade(Theme.KNOB, alpha));
			} else if (t.stack != null && alpha > 0.5f) {
				g.pose().pushMatrix();
				g.pose().translate(sx + 2f, y + (TOAST_H - 10f) / 2f);
				g.pose().scale(10f / 16f, 10f / 16f);
				g.item(t.stack, 0, 0);
				g.pose().popMatrix();
			} else if (t.stack == null) {
				JacText.icon(g, t.icon, sx + 3f, sy, 8f, Colors.fade(JacConfig.accent(), alpha));
			}
			float tx = sx + 16f + 5f;
			float ty = y + (TOAST_H - JacText.lineHeight(TEXT)) / 2f;
			// A stacked toast that just grew gives its count a short lift.
			float bump = t.bumpNanos > 0 && now - t.bumpNanos < BUMP_NANOS ? (float) Math.sin(Math.PI * (now - t.bumpNanos) / BUMP_NANOS) : 0f;
			int boldColor = bump > 0 ? Colors.lerp(Theme.TEXT, JacConfig.accent(), bump) : Theme.TEXT;
			text(g, t.bold, tx, ty - bump, Colors.fade(boldColor, alpha), TEXT, JacText.Weight.SEMIBOLD);
			text(g, t.rest, tx + JacText.width(t.bold, TEXT, JacText.Weight.SEMIBOLD) + 3f, ty, Colors.fade(Theme.TEXT_DIM, alpha), TEXT,
				JacText.Weight.REGULAR);
		}
	}
}
