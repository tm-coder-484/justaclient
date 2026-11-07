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

	/**
	 * How much a toast matters. When more arrive than fit, low ones go first; important ones stay up twice as long. The
	 * widget can hide everything below a level.
	 */
	public enum Priority {
		LOW, NORMAL, HIGH;

		static Priority of(Source source) {
			return switch (source) {
				case PICKUP -> LOW;
				case INVENTORY_FULL -> HIGH;
				default -> NORMAL;
			};
		}
	}

	/** One line in the notification history, which keeps toasts after they fade, including hidden ones. */
	public static final class Entry {
		public final @Nullable String key;
		public final Source source;
		public final Priority priority;
		public final Icons icon;
		public String bold;
		public String rest;
		public long millis;
		/** True if the widget did not show it (switched off, or below the priority filter). */
		public final boolean hidden;

		Entry(Toast t, boolean hidden) {
			this.key = t.key;
			this.source = t.source;
			this.priority = t.priority;
			this.icon = t.icon;
			this.bold = t.bold;
			this.rest = t.rest;
			this.millis = System.currentTimeMillis();
			this.hidden = hidden;
		}
	}

	private static final int HISTORY_SIZE = 150;
	private static final Deque<Entry> HISTORY = new ArrayDeque<>();

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
		public Priority priority;

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
			this.priority = Priority.of(source);
		}
	}

	private Notifications() {
	}

	public static void push(Icons icon, String bold, String rest) {
		push(icon, bold, rest, Priority.NORMAL);
	}

	public static void push(Icons icon, String bold, String rest, Priority priority) {
		Toast t = new Toast(null, Source.GENERAL, icon, bold, rest, false, false, System.nanoTime());
		t.priority = priority;
		add(t);
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
		// A stacked toast updates its history line instead of adding one per pickup.
		Entry newest = t.key == null ? null : HISTORY.stream().filter(e -> t.key.equals(e.key)).findFirst().orElse(null);
		if (newest != null && System.currentTimeMillis() - newest.millis < 30_000) {
			newest.bold = t.bold;
			newest.rest = t.rest;
			newest.millis = System.currentTimeMillis();
		} else {
			remember(t, false);
		}
	}

	private static void remember(Toast t, boolean hidden) {
		HISTORY.addFirst(new Entry(t, hidden));
		while (HISTORY.size() > HISTORY_SIZE) {
			HISTORY.removeLast();
		}
	}

	/** Recent notifications, newest first. */
	public static Deque<Entry> history() {
		return HISTORY;
	}

	public static void clearHistory() {
		HISTORY.clear();
	}

	private static void add(Toast toast) {
		NotificationsHud hud = Features.get(NotificationsHud.class);
		boolean shown = hud != null && hud.isEnabled() && hud.shows(toast.source) && toast.priority.ordinal() >= hud.minPriority().ordinal();
		remember(toast, !shown);
		if (!shown) {
			return;
		}
		TOASTS.addFirst(toast);
		int max = hud.maxToasts();
		while (TOASTS.size() > max) {
			// Drop the oldest of the least important toasts, so a pickup flood never pushes out an important one.
			Toast drop = null;
			for (Toast t : TOASTS) {
				if (drop == null || t.priority.ordinal() <= drop.priority.ordinal()) {
					drop = t;
				}
			}
			TOASTS.remove(drop);
		}
	}

	/** Drops toasts older than {@code lifeNanos}. */
	public static void expire(long now, long lifeNanos) {
		for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext(); ) {
			Toast t = it.next();
			long life = t.priority == Priority.HIGH ? lifeNanos * 2 : lifeNanos;
			if (now - t.createdNanos > life) {
				it.remove();
			}
		}
	}

	public static Deque<Toast> toasts() {
		return TOASTS;
	}
}
