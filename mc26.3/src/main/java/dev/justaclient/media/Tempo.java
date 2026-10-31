package dev.justaclient.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The song's tempo in beats per minute, from Deezer's public API (no account needed). Minecraft cannot hear what the
 * music player plays, so the sky visuals pulse to the tempo instead. 0 when unknown.
 */
public final class Tempo {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final Map<String, Float> CACHE = new ConcurrentHashMap<>();

	private Tempo() {
	}

	/** Blocks; call off the render thread. */
	public static float lookup(MediaState track) {
		return CACHE.computeIfAbsent(track.trackKey(), k -> find(track));
	}

	private static float find(MediaState track) {
		String artist = Lyrics.firstArtist(track.artist());
		JsonElement search = get("https://api.deezer.com/search?limit=10&q=" + URLEncoder.encode(artist + " " + track.title(), StandardCharsets.UTF_8));
		if (search == null || !search.isJsonObject() || !search.getAsJsonObject().has("data")) {
			return 0f;
		}
		String wantTitle = Lyrics.simplify(track.title());
		String wantArtist = Lyrics.simplify(artist);
		long bestId = -1;
		double bestOff = Double.MAX_VALUE;
		for (JsonElement e : search.getAsJsonObject().getAsJsonArray("data")) {
			JsonObject t = e.getAsJsonObject();
			if (!t.has("id") || !t.has("title") || !t.has("artist") || !Lyrics.simplify(t.get("title").getAsString()).equals(wantTitle)
				|| !Lyrics.simplify(t.getAsJsonObject("artist").get("name").getAsString()).equals(wantArtist)) {
				continue;
			}
			double off = track.durationMs() > 0 && t.has("duration") ? Math.abs(t.get("duration").getAsDouble() - track.durationMs() / 1000.0) : 0;
			if (off < bestOff) {
				bestOff = off;
				bestId = t.get("id").getAsLong();
			}
		}
		if (bestId < 0) {
			return 0f;
		}
		JsonElement detail = get("https://api.deezer.com/track/" + bestId);
		if (detail == null || !detail.isJsonObject() || !detail.getAsJsonObject().has("bpm")) {
			return 0f;
		}
		float bpm = detail.getAsJsonObject().get("bpm").getAsFloat();
		// Deezer gives 0 when it does not know; anything outside a musical range is a bad reading.
		return bpm >= 50f && bpm <= 220f ? bpm : 0f;
	}

	private static JsonElement get(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("User-Agent", "Justaclient (https://github.com/tm-coder-484/justaclient)").GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return response.statusCode() == 200 ? JsonParser.parseString(response.body()) : null;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return null;
		}
	}
}
