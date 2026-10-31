package dev.justaclient.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Windows: one long-running PowerShell helper reads the system media session (Spotify, browsers, anything that shows
 * in the volume flyout) through WinRT and streams JSON lines. Album art is saved by the helper into a temp folder.
 * PowerShell 5.1 ships with every Windows 10 and 11, so nothing needs installing.
 */
final class WindowsSource implements MediaSource {
	private final boolean spotifyOnly;
	private @Nullable Process process;
	private @Nullable Path dir;
	private volatile MediaState latest = MediaState.none();
	private long startedAt;
	private int failures;

	WindowsSource(boolean spotifyOnly) {
		this.spotifyOnly = spotifyOnly;
	}

	private void ensureRunning() {
		Process p = process;
		if (p != null && p.isAlive()) {
			return;
		}
		// Back off if the helper keeps dying, e.g. PowerShell blocked by policy.
		long wait = Math.min(60_000L, 2_000L << Math.min(5, failures));
		if (p != null && System.currentTimeMillis() - startedAt < wait) {
			return;
		}
		if (p != null) {
			failures++;
		}
		try {
			if (dir == null) {
				dir = Files.createTempDirectory("justaclient-media");
				dir.toFile().deleteOnExit();
			}
			Path script = dir.resolve("now_playing.ps1");
			try (InputStream in = WindowsSource.class.getResourceAsStream("/assets/justaclient/media/now_playing.ps1")) {
				if (in == null) {
					latest = MediaState.unavailable("Helper script missing from the jar");
					return;
				}
				Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
			}
			Process started = new ProcessBuilder(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-WindowStyle", "Hidden", "-File", script.toString(), "-ArtDir", dir.toString(), "-CmdFile", dir.resolve("command.txt").toString(),
				"-Only", spotifyOnly ? "spotify" : "any", "-ParentPid", Long.toString(ProcessHandle.current().pid()))).redirectErrorStream(true).start();
			started.getOutputStream().close();
			process = started;
			startedAt = System.currentTimeMillis();
			Thread reader = new Thread(() -> read(started), "Justaclient media reader");
			reader.setDaemon(true);
			reader.start();
		} catch (IOException e) {
			latest = MediaState.unavailable("Could not start PowerShell");
			startedAt = System.currentTimeMillis();
			failures++;
		}
	}

	private void read(Process p) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.strip();
				if (!line.startsWith("{")) {
					continue;
				}
				try {
					latest = parse(JsonParser.parseString(line).getAsJsonObject());
					failures = 0;
				} catch (RuntimeException e) {
					// A malformed line; keep the last good reading.
				}
			}
		} catch (IOException e) {
			// The helper exited; ensureRunning starts another.
		}
	}

	private static MediaState parse(JsonObject o) {
		String status = str(o, "status");
		switch (status) {
			case "unavailable":
				return MediaState.unavailable(str(o, "message"));
			case "error":
				Justaclient.LOGGER.debug("Media helper: {}", str(o, "message"));
				return MediaState.none();
			case "playing":
			case "paused":
				break;
			default:
				return MediaState.none();
		}
		String art = str(o, "art");
		return new MediaState(status.equals("playing") ? MediaState.Status.PLAYING : MediaState.Status.PAUSED, str(o, "title"), str(o, "artist"),
			str(o, "album"), num(o, "position"), num(o, "duration"), art.isBlank() ? null : art, playerName(str(o, "player")), System.nanoTime());
	}

	/** "Spotify.exe" or "SpotifyAB.SpotifyMusic_...!Spotify" become "Spotify"; browsers keep a readable name. */
	private static String playerName(String id) {
		String lower = id.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("spotify")) {
			return "Spotify";
		}
		if (lower.contains("chrome")) {
			return "Chrome";
		}
		if (lower.contains("msedge")) {
			return "Edge";
		}
		if (lower.contains("firefox")) {
			return "Firefox";
		}
		int bang = id.lastIndexOf('!');
		String name = bang >= 0 ? id.substring(bang + 1) : id;
		return name.endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	private static long num(JsonObject o, String key) {
		try {
			return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	@Override
	public MediaState poll() {
		ensureRunning();
		return latest;
	}

	@Override
	public void send(Command command) {
		Path d = dir;
		if (d == null) {
			return;
		}
		String word = switch (command) {
			case TOGGLE -> "toggle";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};
		try {
			Path tmp = d.resolve("command.tmp");
			Files.writeString(tmp, word, StandardCharsets.UTF_8);
			Files.move(tmp, d.resolve("command.txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.debug("Could not send media key", e);
		}
	}

	@Override
	public void close() {
		Process p = process;
		if (p != null) {
			p.destroy();
		}
		process = null;
	}
}
