package dev.justaclient.media;

import java.util.List;

/** macOS: asks the Spotify app through AppleScript. Spotify gives an https art URL. */
final class MacSource implements MediaSource {
	private static final String SEP = "\u001f";
	private static final String READ = """
		if application "Spotify" is running then
			tell application "Spotify"
				set s to (player state as text)
				set t to current track
				set sep to (ASCII character 31)
				return s & sep & (name of t) & sep & (artist of t) & sep & (album of t) & sep & ((player position * 1000) as integer) & sep & (duration of t) & sep & (artwork url of t)
			end tell
		end if
		return ""
		""";

	private int installed = -1;

	@Override
	public MediaState poll() {
		// Naming an app AppleScript cannot find opens a "Where is Spotify?" dialog, so only ask once it is installed.
		if (installed < 0) {
			String home = System.getProperty("user.home", "");
			installed = java.nio.file.Files.isDirectory(java.nio.file.Path.of("/Applications/Spotify.app"))
				|| java.nio.file.Files.isDirectory(java.nio.file.Path.of(home, "Applications", "Spotify.app")) ? 1 : 0;
		}
		if (installed == 0) {
			return MediaState.unavailable("Spotify app not found");
		}
		String out = Processes.run(List.of("osascript", "-e", READ), 2500);
		if (out == null) {
			return MediaState.none();
		}
		String[] f = out.strip().split(SEP, -1);
		if (f.length < 7) {
			return MediaState.none();
		}
		MediaState.Status status = switch (f[0]) {
			case "playing" -> MediaState.Status.PLAYING;
			case "paused" -> MediaState.Status.PAUSED;
			default -> MediaState.Status.NONE;
		};
		return new MediaState(status, f[1], f[2], f[3], number(f[4]), number(f[5]), f[6].isBlank() ? null : f[6], "Spotify", System.nanoTime());
	}

	private static long number(String s) {
		try {
			return (long) Double.parseDouble(s.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	@Override
	public void send(Command command) {
		String verb = switch (command) {
			case TOGGLE -> "playpause";
			case NEXT -> "next track";
			case PREVIOUS -> "previous track";
		};
		Processes.run(List.of("osascript", "-e", "tell application \"Spotify\" to " + verb), 2500);
	}

	@Override
	public void close() {
	}
}
