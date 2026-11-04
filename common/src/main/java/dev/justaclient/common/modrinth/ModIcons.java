package dev.justaclient.common.modrinth;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Project icons for the Modrinth browser: downloaded from Modrinth's CDN on a few background threads, kept on disk so
 * the next visit is instant, and decoded into square pixel arrays with rounded corners ready to upload. Most icons are
 * WebP, which neither the game nor the JDK can read, so they go through TwelveMonkeys' WebP reader (bundled).
 */
public final class ModIcons {
	/** Side of the decoded square, in pixels (Modrinth serves icons at 96). */
	public static final int SIZE = 96;
	/** Largest icon we will download; Modrinth's resized icons are a few KB. */
	static final int MAX_BYTES = 2 * 1024 * 1024;
	/** How long a cached icon is trusted before it is fetched again (icon URLs change when the icon does, so this is generous). */
	static final Duration KEEP = Duration.ofDays(14);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
	private final String userAgent;
	private final Path cacheDir;
	private final ThreadPoolExecutor pool;

	public ModIcons(String userAgent, Path cacheDir) {
		this.userAgent = userAgent;
		this.cacheDir = cacheDir;
		AtomicInteger n = new AtomicInteger();
		// Newest request first: while scrolling, the icons now on screen jump ahead of the ones scrolled past.
		this.pool = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>() {
			@Override
			public boolean offer(Runnable r) {
				return offerFirst(r);
			}
		}, r -> {
			Thread t = new Thread(r, "Justaclient Modrinth icons " + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
		pool.allowCoreThreadTimeOut(true);
	}

	/** The icon at {@code url} as {@link #SIZE}x{@link #SIZE} ARGB pixels with rounded corners. Fails if it cannot be fetched or read. */
	public CompletableFuture<int[]> load(String url) {
		return CompletableFuture.supplyAsync(() -> {
			byte[] bytes = fetch(url);
			BufferedImage image = decode(bytes);
			if (image == null) {
				throw new IllegalStateException("Unreadable icon " + url);
			}
			return square(image, SIZE, 0.22f);
		}, pool);
	}

	/** Whether {@code url} is one of Modrinth's own image hosts over https (nothing else is fetched). */
	public static boolean allowed(String url) {
		try {
			URI uri = URI.create(url);
			String host = uri.getHost();
			return "https".equals(uri.getScheme()) && host != null && (host.equals("cdn.modrinth.com") || host.endsWith(".modrinth.com"));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** The file an icon is cached in: the SHA-1 of its URL, so any URL maps to a safe name. */
	Path cacheFile(String url) {
		return cacheDir.resolve(Modrinth.sha1(url.getBytes(StandardCharsets.UTF_8)) + ".img");
	}

	private byte[] fetch(String url) {
		if (!allowed(url)) {
			throw new IllegalStateException("Not a Modrinth image: " + url);
		}
		Path file = cacheFile(url);
		try {
			if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isAfter(java.time.Instant.now().minus(KEEP))) {
				return Files.readAllBytes(file);
			}
		} catch (IOException e) {
			// Fetch it again below.
		}
		byte[] data;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", userAgent).GET().build();
			HttpResponse<InputStream> r = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream in = r.body()) {
				if (r.statusCode() / 100 != 2) {
					throw new IllegalStateException("Modrinth answered " + r.statusCode() + " for an icon");
				}
				data = readAll(in);
			}
		} catch (IOException e) {
			// Offline: an old cached copy is better than the letter placeholder.
			byte[] stale = readQuietly(file);
			if (stale != null) {
				return stale;
			}
			throw new IllegalStateException("Could not fetch icon: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted", e);
		}
		save(file, data);
		return data;
	}

	private void save(Path file, byte[] data) {
		try {
			Files.createDirectories(cacheDir);
			Path part = file.resolveSibling(file.getFileName() + "." + Thread.currentThread().threadId() + ".part");
			try {
				Files.write(part, data);
				Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				Files.setLastModifiedTime(file, FileTime.from(java.time.Instant.now()));
			} finally {
				Files.deleteIfExists(part);
			}
		} catch (IOException e) {
			// Not cached this time; it still shows.
		}
	}

	private static byte @Nullable [] readQuietly(Path file) {
		try {
			return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		byte[] buf = new byte[16 * 1024];
		for (int n; (n = in.read(buf)) > 0; ) {
			out.write(buf, 0, n);
			if (out.size() > MAX_BYTES) {
				throw new IOException("Icon is too large");
			}
		}
		return out.toByteArray();
	}

	/** Whether {@code b} starts like a WebP file ("RIFF", size, "WEBP"). */
	static boolean isWebP(byte[] b) {
		return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
	}

	/** Reads PNG, JPEG, GIF (first frame), BMP and WebP (lossy, lossless, with alpha; first frame of an animation). Null if it is none of those. */
	public static @Nullable BufferedImage decode(byte[] bytes) {
		try {
			if (isWebP(bytes)) {
				// Straight to the reader: plugin lookup through ImageIO's registry does not see jars nested in a mod.
				ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
				try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
					reader.setInput(in);
					return reader.read(0);
				} finally {
					reader.dispose();
				}
			}
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * Fits {@code image} into a {@code size} square (centred, keeping its shape) and cuts the corners round with a
	 * smooth edge, {@code radius} as a fraction of the side. Returns ARGB pixels, row by row.
	 */
	public static int[] square(BufferedImage image, int size, float radius) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		float scale = Math.min(size / (float) image.getWidth(), size / (float) image.getHeight());
		int w = Math.max(1, Math.round(image.getWidth() * scale));
		int h = Math.max(1, Math.round(image.getHeight() * scale));
		// Pixel art icons (32 px and under) stay sharp instead of going blurry.
		if (image.getWidth() <= 32 && image.getHeight() <= 32) {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		}
		g.drawImage(image, (size - w) / 2, (size - h) / 2, w, h, null);
		g.dispose();
		int[] px = out.getRGB(0, 0, size, size, null, 0, size);
		roundCorners(px, size, radius * size);
		return px;
	}

	/** Fades the corners outside a rounded square of corner radius {@code r} (in pixels), with about a pixel of anti-aliasing. */
	static void roundCorners(int[] px, int size, float r) {
		if (r <= 0) {
			return;
		}
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float cx = x + 0.5f;
				float cy = y + 0.5f;
				float dx = Math.max(0, Math.max(r - cx, cx - (size - r)));
				float dy = Math.max(0, Math.max(r - cy, cy - (size - r)));
				if (dx == 0 && dy == 0) {
					continue;
				}
				float d = (float) Math.sqrt(dx * dx + dy * dy) - r;
				float cover = Math.max(0f, Math.min(1f, 0.5f - d));
				if (cover < 1f) {
					int i = y * size + x;
					int alpha = Math.round((px[i] >>> 24) * cover);
					px[i] = px[i] & 0xFFFFFF | alpha << 24;
				}
			}
		}
	}
}
