package dev.justaclient.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

/**
 * Album art, decoded off the render thread into plain pixel arrays: the square cover, a wide blurred strip for the
 * card's backdrop, and two colours picked from the cover to tint the card.
 */
public record AlbumArt(int[] cover, int[] backdrop, int accent, int shade) {
	public static final int COVER = 128;
	public static final int BACK_W = 192;
	public static final int BACK_H = 64;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();

	private static volatile String lastError = "";

	/** Why the last {@link #load} or {@link #search} came back empty. */
	public static String lastError() {
		return lastError;
	}

	/** Loads a local path, a file:// URL or an http(s) URL. Null if it cannot be read or decoded. */
	public static @Nullable AlbumArt load(String ref) {
		byte[] bytes = read(ref);
		if (bytes == null) {
			lastError = "could not read " + (ref.startsWith("http") ? "the image" : "the file");
			return null;
		}
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null) {
				lastError = "unknown image format, " + bytes.length + " bytes starting " + String.format("%02X %02X %02X %02X",
					bytes.length > 0 ? bytes[0] : 0, bytes.length > 1 ? bytes[1] : 0, bytes.length > 2 ? bytes[2] : 0, bytes.length > 3 ? bytes[3] : 0);
				return null;
			}
			return from(image);
		} catch (IOException | RuntimeException | LinkageError e) {
			lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
			return null;
		}
	}

	/**
	 * Finds the cover on the iTunes Search API by title and artist (no account needed), for players that send no cover
	 * or one that cannot be read. Null if there is no confident match.
	 */
	public static @Nullable AlbumArt search(String title, String artist) {
		String first = Lyrics.firstArtist(artist);
		String url = "https://itunes.apple.com/search?entity=song&limit=10&term=" + URLEncoder.encode(first + " " + title, StandardCharsets.UTF_8);
		byte[] body = read(url);
		if (body == null) {
			lastError = "could not be reached";
			return null;
		}
		try {
			JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
			String wantTitle = Lyrics.simplify(title);
			String wantArtist = Lyrics.simplify(first);
			String artwork = null;
			for (JsonElement e : root.getAsJsonArray("results")) {
				JsonObject r = e.getAsJsonObject();
				if (!r.has("artworkUrl100") || !r.has("artistName") || !Lyrics.simplify(r.get("artistName").getAsString()).contains(wantArtist)) {
					continue;
				}
				boolean sameTitle = r.has("trackName") && Lyrics.simplify(r.get("trackName").getAsString()).equals(wantTitle);
				if (sameTitle) {
					artwork = r.get("artworkUrl100").getAsString();
					break;
				}
			}
			if (artwork == null) {
				lastError = "no match";
				return null;
			}
			return load(artwork.replace("100x100bb", "600x600bb"));
		} catch (RuntimeException e) {
			lastError = "unexpected answer";
			return null;
		}
	}

	private static byte @Nullable [] read(String ref) {
		try {
			if (ref.startsWith("http://") || ref.startsWith("https://")) {
				HttpRequest request = HttpRequest.newBuilder(URI.create(ref)).timeout(Duration.ofSeconds(10))
					.header("User-Agent", "Justaclient (https://github.com/tm-coder-484/justaclient)").GET().build();
				HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
				return response.statusCode() == 200 ? response.body() : null;
			}
			Path path = ref.startsWith("file:") ? Path.of(URI.create(ref)) : Path.of(ref);
			return Files.size(path) < 16_000_000L ? Files.readAllBytes(path) : null;
		} catch (IOException | RuntimeException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	static AlbumArt from(BufferedImage image) {
		// Centre square: some players hand over wide thumbnails with the cover in the middle.
		int side = Math.min(image.getWidth(), image.getHeight());
		BufferedImage square = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
		int[] cover = scale(square, COVER, COVER);
		// The backdrop is a band through the middle of the cover, blurred hard and darkened.
		int bandH = Math.max(1, side * BACK_H / BACK_W);
		BufferedImage band = square.getSubimage(0, (side - bandH) / 2, side, bandH);
		int[] backdrop = scale(band, BACK_W / 4, BACK_H / 4);
		backdrop = blur(backdrop, BACK_W / 4, BACK_H / 4, 3);
		backdrop = upscale(backdrop, BACK_W / 4, BACK_H / 4, BACK_W, BACK_H);
		backdrop = blur(backdrop, BACK_W, BACK_H, 4);
		// Darken, and fade the edges out so the strip sits inside a rounded card without showing its corners.
		for (int y = 0; y < BACK_H; y++) {
			for (int x = 0; x < BACK_W; x++) {
				float ex = Math.min(x + 0.5f, BACK_W - x - 0.5f) / (BACK_W * 0.12f);
				float ey = Math.min(y + 0.5f, BACK_H - y - 0.5f) / (BACK_H * 0.22f);
				float edge = Math.min(1f, Math.min(ex, ey));
				int alpha = (int) (255 * edge * edge * (3 - 2 * edge));
				backdrop[y * BACK_W + x] = darken(backdrop[y * BACK_W + x], 0.6f) & 0xFFFFFF | alpha << 24;
			}
		}
		int[] palette = palette(cover);
		return new AlbumArt(cover, backdrop, palette[0], palette[1]);
	}

	private static int[] scale(BufferedImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		int[] px = out.getRGB(0, 0, w, h, null, 0, w);
		for (int i = 0; i < px.length; i++) {
			px[i] |= 0xFF000000;
		}
		return px;
	}

	/** Bilinear upscale, so the stretched backdrop has no blocks. */
	private static int[] upscale(int[] src, int sw, int sh, int dw, int dh) {
		int[] out = new int[dw * dh];
		for (int y = 0; y < dh; y++) {
			float fy = Math.max(0f, (y + 0.5f) * sh / dh - 0.5f);
			int y0 = Math.min(sh - 1, (int) fy);
			int y1 = Math.min(sh - 1, y0 + 1);
			float ty = fy - y0;
			for (int x = 0; x < dw; x++) {
				float fx = Math.max(0f, (x + 0.5f) * sw / dw - 0.5f);
				int x0 = Math.min(sw - 1, (int) fx);
				int x1 = Math.min(sw - 1, x0 + 1);
				float tx = fx - x0;
				int top = mix(src[y0 * sw + x0], src[y0 * sw + x1], tx);
				int bottom = mix(src[y1 * sw + x0], src[y1 * sw + x1], tx);
				out[y * dw + x] = mix(top, bottom, ty);
			}
		}
		return out;
	}

	/** Three box blurs approximate a Gaussian. */
	private static int[] blur(int[] px, int w, int h, int radius) {
		int[] a = px.clone();
		int[] b = new int[a.length];
		for (int pass = 0; pass < 3; pass++) {
			box(a, b, w, h, radius, true);
			box(b, a, w, h, radius, false);
		}
		return a;
	}

	private static void box(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
		int lines = horizontal ? h : w;
		int len = horizontal ? w : h;
		for (int l = 0; l < lines; l++) {
			for (int i = 0; i < len; i++) {
				int rs = 0;
				int gs = 0;
				int bs = 0;
				int n = 0;
				for (int k = i - r; k <= i + r; k++) {
					int kk = Math.max(0, Math.min(len - 1, k));
					int c = horizontal ? src[l * w + kk] : src[kk * w + l];
					rs += (c >> 16) & 0xFF;
					gs += (c >> 8) & 0xFF;
					bs += c & 0xFF;
					n++;
				}
				int idx = horizontal ? l * w + i : i * w + l;
				dst[idx] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
			}
		}
	}

	/**
	 * Picks an accent (the most common lively hue, brightened to read on dark glass) and a shade (the average colour,
	 * darkened) from the cover.
	 */
	private static int[] palette(int[] px) {
		float[] hueWeight = new float[24];
		float[] hueSat = new float[24];
		float[] hueVal = new float[24];
		long r = 0;
		long g = 0;
		long b = 0;
		float[] hsb = new float[3];
		for (int i = 0; i < px.length; i += 3) {
			int c = px[i];
			int cr = (c >> 16) & 0xFF;
			int cg = (c >> 8) & 0xFF;
			int cb = c & 0xFF;
			r += cr;
			g += cg;
			b += cb;
			java.awt.Color.RGBtoHSB(cr, cg, cb, hsb);
			if (hsb[1] < 0.25f || hsb[2] < 0.2f) {
				continue;
			}
			int bin = Math.min(23, (int) (hsb[0] * 24));
			float w = hsb[1] * hsb[2];
			hueWeight[bin] += w;
			hueSat[bin] += hsb[1] * w;
			hueVal[bin] += hsb[2] * w;
		}
		int count = (px.length + 2) / 3;
		int average = 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
		int best = -1;
		for (int i = 0; i < 24; i++) {
			if (hueWeight[i] > 0 && (best < 0 || hueWeight[i] > hueWeight[best])) {
				best = i;
			}
		}
		int accent;
		if (best < 0 || hueWeight[best] < count * 0.01f) {
			// A grey cover: use a light neutral so the card still reads.
			accent = 0xFFD8DCE6;
		} else {
			float sat = Math.min(0.85f, Math.max(0.45f, hueSat[best] / hueWeight[best]));
			float val = Math.max(0.85f, hueVal[best] / hueWeight[best]);
			accent = 0xFF000000 | java.awt.Color.HSBtoRGB((best + 0.5f) / 24f, sat, val) & 0xFFFFFF;
		}
		return new int[] {accent, darken(average, 0.45f)};
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static int darken(int c, float f) {
		int r = (int) (((c >> 16) & 0xFF) * f);
		int g = (int) (((c >> 8) & 0xFF) * f);
		int b = (int) ((c & 0xFF) * f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
