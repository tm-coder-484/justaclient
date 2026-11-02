package dev.justaclient.hud;

import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.hud.widgets.NotificationsHud;
import dev.justaclient.render.Icons;
import java.util.ArrayDeque;
import java.util.Deque;

/** Short toasts shown by the notifications widget, e.g. when a feature is toggled by its key. */
public final class Notifications {
	public static final int MAX = 4;
	private static final Deque<Toast> TOASTS = new ArrayDeque<>();

	/** {@code toggle} toasts draw a switch showing {@code on}; others draw the icon. */
	public record Toast(Icons icon, String bold, String rest, boolean toggle, boolean on, long createdNanos) {
	}

	private Notifications() {
	}

	public static void push(Icons icon, String bold, String rest) {
		add(new Toast(icon, bold, rest, false, false, System.nanoTime()));
	}

	public static void toggled(Feature feature) {
		add(new Toast(feature.icon, feature.name, feature.isEnabled() ? "turned on" : "turned off", true, feature.isEnabled(), System.nanoTime()));
	}

	private static void add(Toast toast) {
		if (!Features.get(NotificationsHud.class).isEnabled()) {
			return;
		}
		TOASTS.addFirst(toast);
		while (TOASTS.size() > MAX) {
			TOASTS.removeLast();
		}
	}

	public static Deque<Toast> toasts() {
		return TOASTS;
	}
}
