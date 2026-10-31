package dev.justaclient.media;

import org.jspecify.annotations.Nullable;

/**
 * One reading of the music player: what is playing and where it is. {@code sampledAt} is the {@link System#nanoTime}
 * of the reading, so the position can be moved forward smoothly between polls.
 */
public record MediaState(Status status, String title, String artist, String album, long positionMs, long durationMs, @Nullable String art,
	String player, long sampledAt) {

	public enum Status {
		/** Nothing is playing, or the player is closed. */
		NONE,
		PLAYING,
		PAUSED,
		/** The helper the platform needs is missing or failed; {@code title} holds a short reason. */
		UNAVAILABLE
	}

	public static MediaState none() {
		return new MediaState(Status.NONE, "", "", "", 0, 0, null, "", System.nanoTime());
	}

	public static MediaState unavailable(String reason) {
		return new MediaState(Status.UNAVAILABLE, reason, "", "", 0, 0, null, "", System.nanoTime());
	}

	public boolean hasTrack() {
		return (status == Status.PLAYING || status == Status.PAUSED) && !title.isBlank();
	}

	/** Identifies the track, to notice when it changes. */
	public String trackKey() {
		return title + '\u0000' + artist + '\u0000' + album;
	}

	/** The position now, moved on from the reading while playing. */
	public long positionNow() {
		long pos = positionMs;
		if (status == Status.PLAYING) {
			pos += (System.nanoTime() - sampledAt) / 1_000_000L;
		}
		return durationMs > 0 ? Math.max(0, Math.min(durationMs, pos)) : Math.max(0, pos);
	}
}
