package dev.justaclient.media;

import java.util.List;

/**
 * Linux players speak MPRIS over D-Bus; {@code playerctl} reads it for us. Positions come in microseconds and art as a
 * URL (https for Spotify, file:// for most other players).
 */
final class LinuxSource implements MediaSource {
	private static final String SEP = "\u001f";
	private static final String FORMAT = String.join(SEP, "{{status}}", "{{xesam:title}}", "{{xesam:artist}}", "{{xesam:album}}", "{{position}}",
		"{{mpris:length}}", "{{mpris:artUrl}}", "{{playerName}}");
	private final String players;
	private boolean checked;
	private boolean available;

	LinuxSource(boolean spotifyOnly) {
		this.players = spotifyOnly ? "spotify" : "spotify,%any";
	}

	@Override
	public MediaState poll() {
		if (!checked) {
			checked = true;
			available = Processes.exists(List.of("playerctl", "--version"));
		}
		if (!available) {
			return MediaState.unavailable("Install playerctl to read your player");
		}
		String out = Processes.run(List.of("playerctl", "-p", players, "metadata", "--format", FORMAT), 2000);
		if (out == null || out.isBlank()) {
			return MediaState.none();
		}
		String[] f = out.strip().split(SEP, -1);
		if (f.length < 8) {
			return MediaState.none();
		}
		MediaState.Status status = switch (f[0]) {
			case "Playing" -> MediaState.Status.PLAYING;
			case "Paused" -> MediaState.Status.PAUSED;
			default -> MediaState.Status.NONE;
		};
		String art = f[6].isBlank() ? null : fixSpotifyArt(f[6]);
		return new MediaState(status, f[1], f[2], f[3], micros(f[4]) / 1000L, micros(f[5]) / 1000L, art, f[7], System.nanoTime());
	}

	/** Older Spotify builds report art on open.spotify.com, which no longer serves it; the same image is on i.scdn.co. */
	private static String fixSpotifyArt(String url) {
		return url.replace("https://open.spotify.com/image/", "https://i.scdn.co/image/");
	}

	private static long micros(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	@Override
	public void send(Command command) {
		String verb = switch (command) {
			case TOGGLE -> "play-pause";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};
		Processes.run(List.of("playerctl", "-p", players, verb), 2000);
	}

	@Override
	public void close() {
	}
}
