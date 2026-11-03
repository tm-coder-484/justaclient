package dev.justaclient.common.modrinth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;

/**
 * Talks to Modrinth: searches, versions, which installed files are which, and downloads that are checked against
 * Modrinth's SHA-512 before they are moved into place. Everything runs off the game thread and returns futures.
 */
public final class ModrinthApi {
	/** Largest file we will download (Modrinth's own upload limit is lower). */
	public static final long MAX_BYTES = 512L * 1024 * 1024;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL)
		.build();
	private final String userAgent;

	/** {@code userAgent} names the client to Modrinth, as its rules ask (no personal details). */
	public ModrinthApi(String userAgent) {
		this.userAgent = userAgent;
	}

	public CompletableFuture<Modrinth.Page> search(String query, Modrinth.Kind kind, String gameVersion, String loader, Modrinth.Sort sort,
		int offset, int limit) {
		return get(Modrinth.searchUrl(query, kind, gameVersion, loader, sort, offset, limit)).thenApply(Modrinth::parseSearch);
	}

	public CompletableFuture<List<Modrinth.Version>> versions(String projectId, Modrinth.Kind kind, String gameVersion, String loader) {
		return get(Modrinth.versionsUrl(projectId, kind, gameVersion, loader)).thenApply(Modrinth::parseVersions);
	}

	public CompletableFuture<Modrinth.Version> version(String versionId) {
		return get(Modrinth.versionUrl(versionId)).thenApply(Modrinth::parseVersion);
	}

	public CompletableFuture<Modrinth.Project> project(String idOrSlug) {
		return get(Modrinth.projectUrl(idOrSlug)).thenApply(Modrinth::parseProject);
	}

	/** Which Modrinth versions these files (by SHA-1) are; files Modrinth does not know are left out. */
	public CompletableFuture<Map<String, Modrinth.Version>> identify(List<String> sha1s) {
		if (sha1s.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}
		HttpRequest request = base(Modrinth.versionFilesUrl()).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(Modrinth.versionFilesBody(sha1s))).build();
		return send(request).thenApply(Modrinth::parseVersionFiles);
	}

	private CompletableFuture<String> get(String url) {
		return send(base(url).GET().build());
	}

	private HttpRequest.Builder base(String url) {
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent)
			.header("Accept", "application/json");
	}

	private CompletableFuture<String> send(HttpRequest request) {
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() == 429) {
				throw new IllegalStateException("Modrinth asked us to slow down; try again in a minute");
			}
			if (r.statusCode() / 100 != 2) {
				throw new IllegalStateException("Modrinth answered " + r.statusCode());
			}
			return r.body();
		});
	}

	/** What happened to a download. */
	public record Installed(Path path, boolean verified) {
	}

	/**
	 * Downloads {@code file} into {@code folder} as {@code name}: first to a temporary file next to it, then checked
	 * against Modrinth's hash, then moved into place. A file that does not match is deleted and the future fails.
	 * {@code progress} gets 0 to 1 as bytes arrive (on the download thread).
	 */
	public CompletableFuture<Installed> download(Modrinth.FileRef file, Path folder, String name, DoubleConsumer progress) {
		URI uri = URI.create(file.url());
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !(uri.getHost().equals("cdn.modrinth.com") || uri.getHost().endsWith(".modrinth.com"))) {
			return CompletableFuture.failedFuture(new IllegalStateException("Not a Modrinth download: " + uri.getHost()));
		}
		if (file.size() > MAX_BYTES) {
			return CompletableFuture.failedFuture(new IllegalStateException("File is too large"));
		}
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).header("User-Agent", userAgent).GET().build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(r -> {
			if (r.statusCode() / 100 != 2) {
				throw new IllegalStateException("Download failed: " + r.statusCode());
			}
			byte[] data;
			try (InputStream in = r.body()) {
				data = readAll(in, file.size(), progress);
			} catch (IOException e) {
				throw new IllegalStateException("Download failed: " + e.getMessage(), e);
			}
			if (!Modrinth.matches(data, file)) {
				throw new IllegalStateException("The download did not match Modrinth's checksum, so it was thrown away");
			}
			try {
				Files.createDirectories(folder);
				Path target = folder.resolve(name).normalize();
				if (!target.getParent().equals(folder.normalize())) {
					throw new IllegalStateException("Bad file name");
				}
				Path part = folder.resolve(name + ".part");
				Files.write(part, data);
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return new Installed(target, true);
			} catch (IOException e) {
				throw new IllegalStateException("Could not save the file: " + e.getMessage(), e);
			}
		});
	}

	private static byte[] readAll(InputStream in, long expected, DoubleConsumer progress) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(expected > 0 ? (int) Math.min(expected, MAX_BYTES) : 1 << 16);
		byte[] buf = new byte[1 << 16];
		long total = 0;
		for (int n; (n = in.read(buf)) > 0; ) {
			out.write(buf, 0, n);
			total += n;
			if (total > MAX_BYTES) {
				throw new IOException("File is too large");
			}
			if (expected > 0) {
				progress.accept(Math.min(1.0, total / (double) expected));
			}
		}
		progress.accept(1.0);
		return out.toByteArray();
	}
}
