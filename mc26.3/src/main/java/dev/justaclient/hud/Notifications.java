package dev.justaclient.hud;

import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.hud.widgets.NotificationsHud;
import dev.justaclient.render.Icons;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The notification feed shown by the notifications widget. Any feature can post a toast with one call. Toasts that share a
 * key stack into one ("+12 Cobblestone") and stay up while more arrive.
 */
public final class Notifications {
	private static final Deque<Toast> TOASTS = new ArrayDeque<>();

	/** Where a toast came from, so each kind can be switched off in the widget's settings. */
	public enum Source {
		TOGGLE, PICKUP, INVENTORY_FULL, GENERAL
	}

	public static final class Toast {
		public final @Nullable String key;
		public final Source source;
		public final Icons icon;
		public String bold;
		public String rest;
		public final boolean toggle;
		public boolean on;
		/** An item drawn instead of the icon, e.g. for pickups. */
		public @Nullable ItemStack stack;
		public int count;
		/** When it first appeared (drives the slide-in). */
		public final long firstNanos;
		/** When it was last refreshed (drives how long it stays). */
		public long createdNanos;
		/** When a stacked toast last grew, for a small bump. */
		public long bumpNanos;

		public Toast(@Nullable String key, Source source, Icons icon, String bold, String rest, boolean toggle, boolean on, long now) {
			this.key = key;
			this.source = source;
			this.icon = icon;
			this.bold = bold;
			this.rest = rest;
			this.toggle = toggle;
			this.on = on;
			this.firstNanos = now;
			this.createdNanos = now;
		}
	}

	private Notifications() {
	}

	public static void push(Icons icon, String bold, String rest) {
		add(new Toast(null, Source.GENERAL, icon, bold, rest, false, false, System.nanoTime()));
	}

	public static void toggled(Feature feature) {
		long now = System.nanoTime();
		Toast existing = find("toggle:" + feature.id);
		if (existing != null) {
			existing.on = feature.isEnabled();
			existing.rest = feature.isEnabled() ? "turned on" : "turned off";
			refresh(existing, now);
			return;
		}
		add(new Toast("toggle:" + feature.id, Source.TOGGLE, feature.icon, feature.name, feature.isEnabled() ? "turned on" : "turned off", true,
			feature.isEnabled(), now));
	}

	/** The player picked up {@code amount} of {@code stack}'s item. Repeat pickups of the same item stack into one toast. */
	public static void pickedUp(ItemStack stack, int amount) {
		if (stack.isEmpty() || amount <= 0) {
			return;
		}
		long now = System.nanoTime();
		String key = "pickup:" + stack.getItem() + ":" + Objects.hashCode(stack.getComponentsPatch());
		Toast existing = find(key);
		if (existing != null) {
			existing.count += amount;
			existing.bold = "+" + existing.count;
			existing.bumpNanos = now;
			refresh(existing, now);
			return;
		}
		Toast t = new Toast(key, Source.PICKUP, Icons.BOX, "+" + amount, stack.getHoverName().getString(), false, false, now);
		t.stack = stack.copyWithCount(1);
		t.count = amount;
		add(t);
	}

	/** Items are lying at the player's feet but there is no room for them. */
	public static void inventoryFull(ItemStack stack) {
		long now = System.nanoTime();
		Toast existing = find("full");
		String rest = "can't pick up " + stack.getHoverName().getString();
		if (existing != null) {
			existing.rest = rest;
			existing.stack = stack.copyWithCount(1);
			refresh(existing, now);
			return;
		}
		Toast t = new Toast("full", Source.INVENTORY_FULL, Icons.BOX, "Inventory full", rest, false, false, now);
		t.stack = stack.copyWithCount(1);
		add(t);
	}

	private static @Nullable Toast find(String key) {
		for (Toast t : TOASTS) {
			if (key.equals(t.key)) {
				return t;
			}
		}
		return null;
	}

	/** Keeps a stacked toast up and moves it to the newest place. */
	private static void refresh(Toast t, long now) {
		t.createdNanos = now;
		TOASTS.remove(t);
		TOASTS.addFirst(t);
	}

	private static void add(Toast toast) {
		NotificationsHud hud = Features.get(NotificationsHud.class);
		if (hud == null || !hud.isEnabled() || !hud.shows(toast.source)) {
			return;
		}
		TOASTS.addFirst(toast);
		int max = hud.maxToasts();
		while (TOASTS.size() > max) {
			TOASTS.removeLast();
		}
	}

	/** Drops toasts older than {@code lifeNanos}. */
	public static void expire(long now, long lifeNanos) {
		for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext(); ) {
			if (now - it.next().createdNanos > lifeNanos) {
				it.remove();
			}
		}
	}

	public static Deque<Toast> toasts() {
		return TOASTS;
	}
}
