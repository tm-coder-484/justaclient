package dev.justaclient.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Time-synced lyrics from lrclib.net, a free open lyrics database. Only the title, artist, album and length are sent.
 * Answers are cached on disk so a song is looked up once.
 */
public final class Lyrics {
	private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("cache").resolve("lyrics");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final String AGENT = "Justaclient (https://github.com/tm-coder-484/justaclient)";

	public record Line(long timeMs, String text) {
	}

	public enum Status {
		LOADING,
		SYNCED,
		/** Words without timings; shown as a hint that only plain lyrics exist. */
		PLAIN,
		INSTRUMENTAL,
		NONE
	}

	public record Result(Status status, List<Line> lines) {
		public static final Result LOADING = new Result(Status.LOADING, List.of());
		public static final Result NONE = new Result(Status.NONE, List.of());

		/** The line playing at {@code positionMs}, or -1 before the first. */
		public int indexAt(long positionMs) {
			int lo = 0;
			int hi = lines.size() - 1;
			int found = -1;
			while (lo <= hi) {
				int mid = (lo + hi) >>> 1;
				if (lines.get(mid).timeMs <= positionMs) {
					found = mid;
					lo = mid + 1;
				} else {
					hi = mid - 1;
				}
			}
			return found;
		}
	}

	private Lyrics() {
	}

	/** Looks the track up, from the disk cache when possible. Blocks; call off the render thread. */
	public static Result fetch(MediaState track) {
		String key = hash(track.title() + '|' + track.artist() + '|' + track.album() + '|' + track.durationMs() / 1000);
		Path file = CACHE.resolve(key + ".json");
		try {
			if (Files.exists(file)) {
				JsonObject cached = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				// "Not found" answers are retried after a day, since lrclib grows.
				boolean fresh = !cached.has("missing") || System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < 86_400_000L;
				if (fresh) {
					return toResult(cached);
				}
			}
		} catch (IOException | RuntimeException e) {
			// Fall through to the network.
		}
		JsonObject found = lookup(track);
		JsonObject store = found != null ? found : new JsonObject();
		if (found == null) {
			store.addProperty("missing", true);
		}
		try {
			Files.createDirectories(CACHE);
			Files.writeString(file, store.toString());
		} catch (IOException e) {
			Justaclient.LOGGER.debug("Could not cache lyrics", e);
		}
		return toResult(store);
	}

	private static @Nullable JsonObject lookup(MediaState track) {
		String artist = firstArtist(track.artist());
		StringBuilder q = new StringBuilder("https://lrclib.net/api/get?track_name=").append(enc(track.title())).append("&artist_name=").append(enc(artist));
		if (!track.album().isBlank()) {
			q.append("&album_name=").append(enc(track.album()));
		}
		if (track.durationMs() > 0) {
			q.append("&duration=").append(Math.round(track.durationMs() / 1000.0));
		}
		JsonElement exact = get(q.toString());
		if (exact != null && exact.isJsonObject() && exact.getAsJsonObject().has("id") && plausible(exact.getAsJsonObject())) {
			return exact.getAsJsonObject();
		}
		// No exact match (album or length differ slightly): search and take the closest length with synced lyrics.
		JsonElement search = get("https://lrclib.net/api/search?track_name=" + enc(track.title()) + "&artist_name=" + enc(artist));
		if (search == null || !search.isJsonArray()) {
			return null;
		}
		JsonObject best = null;
		double bestScore = Double.MAX_VALUE;
		for (JsonElement e : (JsonArray) search) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			double length = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
			double off = track.durationMs() > 0 ? Math.abs(length - track.durationMs() / 1000.0) : 0;
			if (off > 5) {
				continue;
			}
			if (!plausible(o)) {
				continue;
			}
			boolean synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull();
			double score = off + (synced ? 0 : 100);
			if (score < bestScore) {
				best = o;
				bestScore = score;
			}
		}
		return best;
	}

	/** Skips placeholder entries (anyone can submit to lrclib): synced lyrics with only a line or two. */
	private static boolean plausible(JsonObject o) {
		if (o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean()) {
			return true;
		}
		if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
			return parse(o.get("syncedLyrics").getAsString()).size() >= 4;
		}
		return o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull() && o.get("plainLyrics").getAsString().length() > 40;
	}

	/** Players list several artists as "A, B" or "A; B"; lrclib matches best on the first. */
	private static String firstArtist(String artist) {
		int cut = artist.length();
		for (String sep : new String[] {", ", "; ", " & ", " feat. ", " ft. "}) {
			int i = artist.toLowerCase(Locale.ROOT).indexOf(sep.toLowerCase(Locale.ROOT));
			if (i > 0) {
				cut = Math.min(cut, i);
			}
		}
		return artist.substring(0, cut).trim();
	}

	private static @Nullable JsonElement get(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", AGENT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return null;
			}
			return JsonParser.parseString(response.body());
		} catch (IOException | RuntimeException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static Result toResult(JsonObject o) {
		if (o.has("missing")) {
			return Result.NONE;
		}
		if (o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean()) {
			return new Result(Status.INSTRUMENTAL, List.of());
		}
		if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
			List<Line> lines = parse(o.get("syncedLyrics").getAsString());
			if (!lines.isEmpty()) {
				return new Result(Status.SYNCED, lines);
			}
		}
		if (o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull()) {
			return new Result(Status.PLAIN, List.of());
		}
		return Result.NONE;
	}

	/** Reads LRC text: "[mm:ss.xx] words", where one line may carry several time stamps. */
	public static List<Line> parse(String lrc) {
		List<Line> lines = new ArrayList<>();
		for (String raw : lrc.split("\\R")) {
			Matcher m = STAMP.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
				if (m.group(3) != null) {
					String frac = (m.group(3) + "00").substring(0, 3);
					ms += Long.parseLong(frac);
				}
				times.add(ms);
				end = m.end();
			}
			String text = raw.substring(end).trim();
			for (long t : times) {
				lines.add(new Line(t, text));
			}
		}
		lines.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
		return lines;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String hash(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d, 0, 10);
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
