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
 * Time-synced lyrics from lrclib.net, a free open lyrics database, with NetEase Cloud Music as a fallback when lrclib is
 * down or has no synced lyrics. Only the title, artist, album and length are sent.
 * Answers are cached on disk so a song is looked up once.
 */
public final class Lyrics {
	private static final Pattern CREDIT = Pattern.compile("^\\s*[^:\\uFF1A\\s][^:\\uFF1A]{0,15}\\s*[:\\uFF1A]\\s*\\S");
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
		NONE,
		/** lrclib could not be reached; not cached, and tried again shortly. */
		ERROR
	}

	public record Result(Status status, List<Line> lines) {
		public static final Result LOADING = new Result(Status.LOADING, List.of());
		public static final Result NONE = new Result(Status.NONE, List.of());
		public static final Result ERROR = new Result(Status.ERROR, List.of());

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

	private static volatile String note = "";

	/** lrclib answered with an error or could not be reached, as opposed to "no such song". */
	private static final class Unreachable extends Exception {
		Unreachable() {
			super(null, null, false, false);
		}
	}

	private Lyrics() {
	}

	/** Looks the track up, from the disk cache when possible. Blocks; call off the render thread. */
	public static Result fetch(MediaState track) {
		// "v2": answers cached before network errors were told apart from "not found" are not trusted.
		String key = hash("v2|" + track.title() + '|' + track.artist() + '|' + track.album() + '|' + track.durationMs() / 1000);
		Path file = CACHE.resolve(key + ".json");
		try {
			if (Files.exists(file)) {
				JsonObject cached = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				// "Not found" answers are retried after a day, since lrclib grows.
				boolean fresh = !cached.has("missing") || System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < 86_400_000L;
				if (fresh) {
					note = cached.has("missing") ? "no lyrics found (cached)" : "from cache (" + (cached.has("source") ? cached.get("source").getAsString() : "lrclib.net") + ")";
					return toResult(cached);
				}
			}
		} catch (IOException | RuntimeException e) {
			// Fall through to the network.
		}
		JsonObject found = null;
		boolean failed = false;
		try {
			found = lookup(track);
			note = found != null ? "lrclib.net" : "lrclib.net: no match";
		} catch (Unreachable e) {
			failed = true;
			note = "lrclib.net could not be reached";
		}
		if (found == null || !found.has("syncedLyrics") || found.get("syncedLyrics").isJsonNull()) {
			// lrclib is down now and then, and misses some songs: NetEase Cloud Music has synced lyrics for most of them.
			try {
				JsonObject other = netease(track);
				if (other != null) {
					found = other;
					note = note + ", found on NetEase";
				} else {
					note = note + ", NetEase: no match";
				}
			} catch (Unreachable e) {
				failed = true;
				note = note + ", NetEase could not be reached";
			}
		}
		if (found == null && failed) {
			// Not cached: a source was down, so try again in a little while.
			return Result.ERROR;
		}
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

	/** Where the last lyrics came from, or why there were none. For {@code /jmedia}. */
	public static String note() {
		return note;
	}

	/** Searches NetEase Cloud Music for the same title, artist and length, and turns its LRC into an lrclib-style answer. */
	private static @Nullable JsonObject netease(MediaState track) throws Unreachable {
		String artist = firstArtist(track.artist());
		JsonElement search = get("https://music.163.com/api/search/get?s=" + enc(artist + " " + track.title()) + "&type=1&limit=10");
		if (search == null || !search.isJsonObject() || !search.getAsJsonObject().has("result")) {
			return null;
		}
		JsonElement result = search.getAsJsonObject().get("result");
		if (!result.isJsonObject() || !result.getAsJsonObject().has("songs") || !result.getAsJsonObject().get("songs").isJsonArray()) {
			return null;
		}
		String wantTitle = simplify(track.title());
		String wantArtist = simplify(artist);
		long bestId = -1;
		double bestOff = Double.MAX_VALUE;
		for (JsonElement e : result.getAsJsonObject().getAsJsonArray("songs")) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject song = e.getAsJsonObject();
			if (!song.has("id") || !song.has("name") || !simplify(song.get("name").getAsString()).equals(wantTitle)) {
				continue;
			}
			boolean artistMatches = false;
			if (song.has("artists") && song.get("artists").isJsonArray()) {
				for (JsonElement a : song.getAsJsonArray("artists")) {
					if (a.isJsonObject() && a.getAsJsonObject().has("name") && simplify(a.getAsJsonObject().get("name").getAsString()).equals(wantArtist)) {
						artistMatches = true;
					}
				}
			}
			if (!artistMatches) {
				continue;
			}
			double length = song.has("duration") ? song.get("duration").getAsDouble() / 1000.0 : 0;
			double off = track.durationMs() > 0 && length > 0 ? Math.abs(length - track.durationMs() / 1000.0) : 0;
			if (off <= 5 && off < bestOff) {
				bestOff = off;
				bestId = song.get("id").getAsLong();
			}
		}
		if (bestId < 0) {
			return null;
		}
		JsonElement lyric = get("https://music.163.com/api/song/lyric?id=" + bestId + "&lv=1");
		if (lyric == null || !lyric.isJsonObject()) {
			return null;
		}
		JsonObject o = lyric.getAsJsonObject();
		JsonObject answer = new JsonObject();
		answer.addProperty("source", "NetEase");
		if (o.has("pureMusic") && o.get("pureMusic").getAsBoolean()) {
			answer.addProperty("instrumental", true);
			return answer;
		}
		if (!o.has("lrc") || !o.get("lrc").isJsonObject() || !o.getAsJsonObject("lrc").has("lyric")) {
			return null;
		}
		List<Line> lines = new ArrayList<>();
		for (Line line : parse(o.getAsJsonObject("lrc").get("lyric").getAsString())) {
			// NetEase starts most songs with credit lines such as "作词 : Name".
			if (CREDIT.matcher(line.text()).find() && (line.timeMs() < 30_000L || line.text().codePoints().anyMatch(c -> c >= 0x3000))) {
				continue;
			}
			lines.add(line);
		}
		if (lines.size() < 4) {
			return null;
		}
		StringBuilder lrc = new StringBuilder();
		for (Line line : lines) {
			lrc.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]%s%n", line.timeMs() / 60_000L, line.timeMs() / 1000L % 60, line.timeMs() % 1000 / 10,
				line.text()));
		}
		answer.addProperty("syncedLyrics", lrc.toString());
		return answer;
	}

	/** Lower case letters and digits only, without "(feat. ...)" or " - Remastered" style endings. */
	static String simplify(String s) {
		String t = s.toLowerCase(Locale.ROOT);
		for (String cut : new String[] {" - ", " (", " [", " feat. ", " ft. "}) {
			int i = t.indexOf(cut);
			if (i > 0) {
				t = t.substring(0, i);
			}
		}
		return t.replaceAll("[^\\p{L}\\p{N}]", "");
	}

	private static @Nullable JsonObject lookup(MediaState track) throws Unreachable {
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
	static String firstArtist(String artist) {
		int cut = artist.length();
		for (String sep : new String[] {", ", "; ", " & ", " feat. ", " ft. "}) {
			int i = artist.toLowerCase(Locale.ROOT).indexOf(sep.toLowerCase(Locale.ROOT));
			if (i > 0) {
				cut = Math.min(cut, i);
			}
		}
		return artist.substring(0, cut).trim();
	}

	/** The parsed answer, or null when lrclib says there is no such song (404). */
	private static @Nullable JsonElement get(String url) throws Unreachable {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", AGENT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() == 404) {
				return null;
			}
			if (response.statusCode() != 200) {
				throw new Unreachable();
			}
			return JsonParser.parseString(response.body());
		} catch (IOException e) {
			throw new Unreachable();
		} catch (RuntimeException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Unreachable();
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
