package dev.justaclient.media;

import dev.justaclient.Justaclient;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * Keeps track of what the system music player is doing. A background thread polls the platform's {@link MediaSource};
 * a second thread loads album art and lyrics when the track changes. The render thread only reads fields.
 */
public final class NowPlaying {
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Justaclient media loader");
		t.setDaemon(true);
		return t;
	});
	/** Covers load on their own thread so a slow lyrics lookup never holds them up. */
	private static final ExecutorService ART_LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Justaclient album art loader");
		t.setDaemon(true);
		return t;
	});
	private static final ConcurrentLinkedQueue<MediaSource.Command> COMMANDS = new ConcurrentLinkedQueue<>();

	private static volatile MediaState state = MediaState.none();
	private static volatile @Nullable AlbumArt art;
	/** Bumped each time {@link #art} changes, so the widget knows to upload it. */
	private static volatile int artVersion;
	private static volatile Lyrics.Result lyrics = Lyrics.Result.NONE;
	private static volatile String artNote = "";
	private static volatile float tempo;
	private static volatile boolean wantLyrics = true;
	private static volatile boolean spotifyOnly;
	private static volatile @Nullable Thread poller;
	private static volatile int generation;

	/** Smoothed clock: the position at {@link #clockNanos}, so small polling jitter never makes the bar jump back. */
	private static volatile long clockPos;
	private static volatile long clockNanos = System.nanoTime();
	private static volatile boolean clockRunning;

	private NowPlaying() {
	}

	/** Starts polling, or restarts it if the player choice changed. */
	public static synchronized void start(boolean onlySpotify, boolean fetchLyrics) {
		wantLyrics = fetchLyrics;
		if (poller != null && onlySpotify == spotifyOnly) {
			return;
		}
		stop();
		spotifyOnly = onlySpotify;
		int gen = ++generation;
		Thread t = new Thread(() -> loop(gen, onlySpotify), "Justaclient media poll");
		t.setDaemon(true);
		poller = t;
		t.start();
	}

	public static synchronized void stop() {
		generation++;
		Thread t = poller;
		poller = null;
		if (t != null) {
			t.interrupt();
		}
		state = MediaState.none();
	}

	public static void send(MediaSource.Command command) {
		COMMANDS.add(command);
		// Feels instant: flip play/pause locally, the next reading confirms it.
		if (command == MediaSource.Command.TOGGLE && state.hasTrack()) {
			MediaState s = state;
			long pos = position();
			MediaState.Status flipped = s.status() == MediaState.Status.PLAYING ? MediaState.Status.PAUSED : MediaState.Status.PLAYING;
			state = new MediaState(flipped, s.title(), s.artist(), s.album(), pos, s.durationMs(), s.art(), s.player(), System.nanoTime());
			rebase(pos, flipped == MediaState.Status.PLAYING);
		}
	}

	public static MediaState state() {
		return state;
	}

	public static @Nullable AlbumArt art() {
		return art;
	}

	public static int artVersion() {
		return artVersion;
	}

	public static Lyrics.Result lyrics() {
		return lyrics;
	}

	/** Beats per minute of the current song, or 0 if unknown. */
	public static float tempo() {
		return tempo;
	}

	/** What the media code knows, for {@code /jmedia}: the player, track, timeline, cover and lyrics. */
	public static java.util.List<String> describe() {
		MediaState s = state;
		java.util.List<String> out = new java.util.ArrayList<>();
		out.add("Status: " + s.status() + (s.status() == MediaState.Status.UNAVAILABLE ? " (" + s.title() + ")" : ""));
		if (s.status() == MediaState.Status.UNAVAILABLE) {
			return out;
		}
		out.add("Player: " + (s.player().isBlank() ? "none" : s.player()) + (spotifyOnly ? "  (Spotify only)" : "  (any player)"));
		if (!s.hasTrack()) {
			return out;
		}
		out.add("Track: " + s.title() + " / " + s.artist() + (s.album().isBlank() ? "" : " / " + s.album()));
		out.add("Time: " + position() / 1000 + " s of " + (s.durationMs() > 0 ? s.durationMs() / 1000 + " s" : "unknown (player sends no timeline)"));
		out.add("Tempo: " + (tempo > 0 ? Math.round(tempo) + " bpm (Deezer)" : "unknown"));
		out.add("Cover: " + (art != null ? "shown, " : "none, ") + artNote);
		Lyrics.Result l = lyrics;
		out.add("Lyrics: " + (wantLyrics ? l.status() + (l.status() == Lyrics.Status.SYNCED ? " (" + l.lines().size() + " lines)" : "") + ", " + Lyrics.note()
			: "off (turn on card lyrics or sky lyrics)"));
		return out;
	}

	/** Playback position in milliseconds, moving smoothly between readings. */
	public static long position() {
		long pos = clockPos;
		if (clockRunning) {
			pos += (System.nanoTime() - clockNanos) / 1_000_000L;
		}
		long duration = state.durationMs();
		return duration > 0 ? Math.max(0, Math.min(duration, pos)) : Math.max(0, pos);
	}

	private static void rebase(long pos, boolean running) {
		clockPos = pos;
		clockNanos = System.nanoTime();
		clockRunning = running;
	}

	private static void loop(int gen, boolean onlySpotify) {
		try (MediaSource source = MediaSource.create(onlySpotify)) {
			String lastKey = "";
			String lastArt = "";
			String searchedKey = "";
			long trackAt = System.currentTimeMillis();
			// The track the current lyrics belong to, and when they were last asked for.
			String lyricsKey = "";
			long lyricsAt = 0L;
			while (gen == generation) {
				MediaSource.Command command;
				while ((command = COMMANDS.poll()) != null) {
					source.send(command);
				}
				MediaState next;
				try {
					next = source.poll();
				} catch (RuntimeException e) {
					Justaclient.LOGGER.debug("Media poll failed", e);
					next = MediaState.none();
				}
				if (gen != generation) {
					break;
				}
				boolean changed = !next.trackKey().equals(lastKey);
				boolean running = next.status() == MediaState.Status.PLAYING;
				// Some players (browsers among them) report no timeline at all: run our own clock from the track change.
				boolean noTimeline = next.durationMs() <= 0 && next.positionMs() <= 0;
				long reading = noTimeline ? (changed ? 0L : position()) : next.positionNow();
				// Snap the clock on a seek, pause or new song; ease it towards small disagreements so lyrics stay in step
				// without the bar jumping back and forth on polling jitter.
				long drift = reading - position();
				if (changed || running != clockRunning || Math.abs(drift) > 250) {
					rebase(reading, running);
				} else if (!noTimeline && Math.abs(drift) > 40) {
					rebase(position() + drift / 3, running);
				}
				state = next;
				if (next.hasTrack() && changed) {
					lastKey = next.trackKey();
				} else if (!next.hasTrack()) {
					lastKey = "";
				}
				// Lyrics: on a new track, when they are switched on mid-song, and again a little later if lrclib was unreachable.
				if (next.hasTrack() && wantLyrics) {
					long nowMs = System.currentTimeMillis();
					boolean retry = lyrics.status() == Lyrics.Status.ERROR && nowMs - lyricsAt > 20_000L;
					if (!lyricsKey.equals(next.trackKey()) || retry) {
						lyricsKey = next.trackKey();
						lyricsAt = nowMs;
						if (!retry) {
							lyrics = Lyrics.Result.LOADING;
						}
						MediaState track = next;
						LOADER.submit(() -> {
							Lyrics.Result result = Lyrics.fetch(track);
							if (track.trackKey().equals(state.trackKey())) {
								lyrics = result;
							}
						});
					}
				} else if (!wantLyrics && !lyricsKey.isEmpty()) {
					lyricsKey = "";
					lyrics = Lyrics.Result.NONE;
				}
				// Album art: the player's own cover when it sends one, else (or if it cannot be read) a lookup by title
				// and artist on iTunes a moment after the track starts.
				if (next.hasTrack() && changed) {
					trackAt = System.currentTimeMillis();
					tempo = 0f;
					MediaState track = next;
					ART_LOADER.submit(() -> {
						float bpm = Tempo.lookup(track);
						if (track.trackKey().equals(state.trackKey())) {
							tempo = bpm;
						}
					});
					lastArt = null;
					searchedKey = "";
				}
				String artRef = next.art() == null ? "" : next.art();
				if (next.hasTrack() && !artRef.equals(lastArt)) {
					lastArt = artRef;
					if (artRef.isEmpty()) {
						art = null;
						artVersion++;
						artNote = "the player sent no cover";
					} else {
						String ref = artRef;
						String key = next.trackKey();
						ART_LOADER.submit(() -> {
							AlbumArt loaded = AlbumArt.load(ref);
							if (loaded != null || key.equals(state.trackKey())) {
								art = loaded;
								artVersion++;
								artNote = loaded != null ? "from the player" : "the player's cover could not be read (" + AlbumArt.lastError() + ")";
							}
						});
					}
				}
				if (next.hasTrack() && art == null && !next.trackKey().equals(searchedKey) && System.currentTimeMillis() - trackAt > 2500L) {
					searchedKey = next.trackKey();
					MediaState track = next;
					ART_LOADER.submit(() -> {
						if (art != null) {
							return;
						}
						AlbumArt found = AlbumArt.search(track.title(), track.artist());
						if (track.trackKey().equals(state.trackKey())) {
							if (found != null) {
								art = found;
								artVersion++;
								artNote = "from iTunes (the player sent none)";
							} else {
								artNote = artNote + "; iTunes: " + AlbumArt.lastError();
							}
						}
					});
				}
				Thread.sleep(next.status() == MediaState.Status.UNAVAILABLE ? 5000 : 500);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException e) {
			Justaclient.LOGGER.warn("Now playing stopped", e);
		}
	}
}
