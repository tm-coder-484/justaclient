package dev.justaclient.common.chat;

import org.jspecify.annotations.Nullable;

/**
 * Auto GG's one pending message. It belongs to the connection and world it was triggered in: if either changes before
 * the delay runs out (a disconnect, a new server, a new world), or the server stops being allowed, it is dropped instead
 * of being said somewhere else.
 */
public final class GgSend {
	private @Nullable Object connection;
	private @Nullable Object world;
	private String text = "";
	private int ticks = -1;

	public boolean pending() {
		return ticks >= 0;
	}

	/** Waits {@code ticks} game ticks, then {@link #tick} hands back {@code text} if nothing changed. */
	public void schedule(Object connection, Object world, String text, int ticks) {
		this.connection = connection;
		this.world = world;
		this.text = text;
		this.ticks = Math.max(0, ticks);
	}

	public void cancel() {
		connection = null;
		world = null;
		text = "";
		ticks = -1;
	}

	/**
	 * One game tick. Returns the message to send now, or null. {@code connection} and {@code world} are the current ones
	 * (null when there is none); {@code allowed} is whether Auto GG may speak on the current server at all.
	 */
	public @Nullable String tick(@Nullable Object connection, @Nullable Object world, boolean allowed) {
		if (ticks < 0) {
			return null;
		}
		if (connection == null || connection != this.connection || world != this.world || !allowed) {
			cancel();
			return null;
		}
		if (ticks-- > 0) {
			return null;
		}
		String out = text;
		cancel();
		return out.isEmpty() ? null : out;
	}
}
