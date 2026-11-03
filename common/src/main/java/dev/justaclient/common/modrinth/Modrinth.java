package dev.justaclient.common.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Modrinth's public API (v2) as plain data: the addresses to ask, and what comes back. No game code here, so both
 * Minecraft versions share it and the self-test can check it without the network. {@link ModrinthApi} does the asking.
 */
public final class Modrinth {
	public static final String API = "https://api.modrinth.com/v2";
	public static final String SITE = "https://modrinth.com";

	/** What can be browsed, and the folder each kind installs into. */
	public enum Kind {
		MOD("mod", "Mods", "mods"),
		SHADER("shader", "Shaders", "shaderpacks"),
		RESOURCE_PACK("resourcepack", "Resource packs", "resourcepacks"),
		DATA_PACK("datapack", "Data packs", "datapacks");

		public final String type;
		public final String label;
		public final String folder;

		Kind(String type, String label, String folder) {
			this.type = type;
			this.label = label;
			this.folder = folder;
		}

		/** The loader Modrinth files this kind under, given the game's loader (Fabric). */
		public @Nullable String loader(String gameLoader) {
			return switch (this) {
				case MOD -> gameLoader;
				case SHADER -> "iris";
				case RESOURCE_PACK -> "minecraft";
				case DATA_PACK -> "datapack";
			};
		}
	}

	public enum Sort {
		RELEVANCE("relevance", "Best match"), DOWNLOADS("downloads", "Most downloaded"), FOLLOWS("follows", "Most followed"),
		NEWEST("newest", "Newest"), UPDATED("updated", "Recently updated");

		public final String index;
		public final String label;

		Sort(String index, String label) {
			this.index = index;
			this.label = label;
		}
	}

	public record Hit(String id, String slug, String title, String description, String author, long downloads, long follows,
		List<String> categories, String type) {
		public String url() {
			return SITE + "/" + type + "/" + slug;
		}
	}

	public record Page(List<Hit> hits, int offset, int total) {
	}

	public record FileRef(String url, String filename, String sha1, String sha512, long size, boolean primary) {
	}

	public record Dependency(@Nullable String projectId, @Nullable String versionId, String type) {
		public boolean required() {
			return "required".equals(type);
		}
	}

	public record Version(String id, String projectId, String name, String number, String type, String published, List<FileRef> files,
		List<Dependency> dependencies, List<String> loaders, List<String> gameVersions) {
		/** The file to install: the one marked primary, else the first. */
		public @Nullable FileRef primaryFile() {
			for (FileRef f : files) {
				if (f.primary()) {
					return f;
				}
			}
			return files.isEmpty() ? null : files.getFirst();
		}

		/** "2026-09-20" from Modrinth's full timestamp. */
		public String date() {
			return published.length() >= 10 ? published.substring(0, 10) : published;
		}
	}

	private Modrinth() {
	}

	/** The search address for {@code query} in {@code kind}, only things made for {@code gameVersion} (and {@code loader} for mods). */
	public static String searchUrl(String query, Kind kind, String gameVersion, String loader, Sort sort, int offset, int limit) {
		JsonArray facets = new JsonArray();
		facets.add(facet("project_type:" + kind.type));
		facets.add(facet("versions:" + gameVersion));
		if (kind == Kind.MOD) {
			facets.add(facet("categories:" + loader));
			// Only mods that do something on the client; server-only mods would do nothing here.
			facets.add(facet("client_side:required", "client_side:optional"));
		} else if (kind == Kind.SHADER) {
			facets.add(facet("categories:iris"));
		}
		StringBuilder url = new StringBuilder(API).append("/search?limit=").append(limit).append("&offset=").append(offset)
			.append("&index=").append(sort.index).append("&facets=").append(enc(facets.toString()));
		if (!query.isBlank()) {
			url.append("&query=").append(enc(query.strip()));
		}
		return url.toString();
	}

	/** The address listing {@code projectId}'s versions that fit this game. */
	public static String versionsUrl(String projectId, Kind kind, String gameVersion, String loader) {
		JsonArray versions = new JsonArray();
		versions.add(gameVersion);
		StringBuilder url = new StringBuilder(API).append("/project/").append(enc(projectId)).append("/version?game_versions=")
			.append(enc(versions.toString()));
		String l = kind.loader(loader);
		if (l != null) {
			JsonArray loaders = new JsonArray();
			loaders.add(l);
			url.append("&loaders=").append(enc(loaders.toString()));
		}
		return url.toString();
	}

	/** The address of one version by its id (a dependency can name an exact version). */
	public static String versionUrl(String versionId) {
		return API + "/version/" + enc(versionId);
	}

	public static String projectUrl(String idOrSlug) {
		return API + "/project/" + enc(idOrSlug);
	}

	/** Address and body that ask which Modrinth versions these files are (by SHA-1). */
	public static String versionFilesUrl() {
		return API + "/version_files";
	}

	public static String versionFilesBody(List<String> sha1s) {
		JsonObject body = new JsonObject();
		JsonArray hashes = new JsonArray();
		sha1s.forEach(hashes::add);
		body.add("hashes", hashes);
		body.addProperty("algorithm", "sha1");
		return body.toString();
	}

	private static JsonArray facet(String... options) {
		JsonArray a = new JsonArray();
		for (String o : options) {
			a.add(o);
		}
		return a;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public static Page parseSearch(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		List<Hit> hits = new ArrayList<>();
		for (JsonElement e : arr(o, "hits")) {
			JsonObject h = e.getAsJsonObject();
			hits.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "description"), str(h, "author"), num(h, "downloads"),
				num(h, "follows"), strings(h, "categories"), str(h, "project_type")));
		}
		return new Page(hits, (int) num(o, "offset"), (int) num(o, "total_hits"));
	}

	public static List<Version> parseVersions(String json) {
		List<Version> out = new ArrayList<>();
		JsonElement root = JsonParser.parseString(json);
		if (root.isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray()) {
				out.add(version(e.getAsJsonObject()));
			}
		}
		return out;
	}

	/** {@code /version_files} answers with a map from each known hash to its version; unknown hashes are left out. */
	public static Map<String, Version> parseVersionFiles(String json) {
		Map<String, Version> out = new LinkedHashMap<>();
		JsonElement root = JsonParser.parseString(json);
		if (root.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
				if (e.getValue().isJsonObject()) {
					out.put(e.getKey().toLowerCase(Locale.ROOT), version(e.getValue().getAsJsonObject()));
				}
			}
		}
		return out;
	}

	/** The project's long description and links, from {@code /project/{id}}. */
	public record Project(String id, String slug, String title, String body, @Nullable String source, @Nullable String issues,
		@Nullable String wiki, String license) {
	}

	public static Project parseProject(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		String license = "";
		if (o.has("license") && o.get("license").isJsonObject()) {
			license = str(o.getAsJsonObject("license"), "id");
		}
		return new Project(str(o, "id"), str(o, "slug"), str(o, "title"), str(o, "body"), nullable(o, "source_url"), nullable(o, "issues_url"),
			nullable(o, "wiki_url"), license);
	}

	private static Version version(JsonObject v) {
		List<FileRef> files = new ArrayList<>();
		for (JsonElement e : arr(v, "files")) {
			JsonObject f = e.getAsJsonObject();
			JsonObject hashes = f.has("hashes") && f.get("hashes").isJsonObject() ? f.getAsJsonObject("hashes") : new JsonObject();
			files.add(new FileRef(str(f, "url"), str(f, "filename"), str(hashes, "sha1").toLowerCase(Locale.ROOT),
				str(hashes, "sha512").toLowerCase(Locale.ROOT), num(f, "size"), f.has("primary") && f.get("primary").isJsonPrimitive() && f.get("primary").getAsBoolean()));
		}
		List<Dependency> deps = new ArrayList<>();
		for (JsonElement e : arr(v, "dependencies")) {
			JsonObject d = e.getAsJsonObject();
			deps.add(new Dependency(nullable(d, "project_id"), nullable(d, "version_id"), str(d, "dependency_type")));
		}
		return new Version(str(v, "id"), str(v, "project_id"), str(v, "name"), str(v, "version_number"), str(v, "version_type"),
			str(v, "date_published"), files, deps, strings(v, "loaders"), strings(v, "game_versions"));
	}

	public static Version parseVersion(String json) {
		return version(JsonParser.parseString(json).getAsJsonObject());
	}

	/** True if {@code v} is made for {@code gameVersion} and {@code loader}. */
	public static boolean compatible(Version v, String gameVersion, String loader) {
		return v.gameVersions().contains(gameVersion) && v.loaders().contains(loader);
	}

	/** What to do about one required dependency: install {@code install}, report {@code problem}, or neither (already there). */
	public record DependencyPlan(@Nullable Version install, @Nullable String problem) {
	}

	/**
	 * Decides what to install for {@code dep}. A dependency that names an exact version gets that version ({@code pinned},
	 * looked up by its id) and nothing else, and only if it is made for this game and loader; one that names only a
	 * project gets the best of {@code candidates} (already filtered to this game and loader). {@code installedVersion} is
	 * the version of that project already in the folder, if any.
	 */
	public static DependencyPlan plan(Dependency dep, @Nullable Version pinned, List<Version> candidates, @Nullable String installedVersion,
		String gameVersion, String loader) {
		return plan(dep, pinned, candidates, installedVersion, null, gameVersion, loader);
	}

	/**
	 * As above, also knowing {@code loadedVersion}: the version string of that mod if the game has it loaded (from
	 * outside the mods folder, say). A pinned dependency is only satisfied by a loaded mod whose version matches the pin;
	 * a different loaded version is reported, never installed beside it. {@code installedVersion} covers files already in
	 * the folder and installs still queued.
	 */
	public static DependencyPlan plan(Dependency dep, @Nullable Version pinned, List<Version> candidates, @Nullable String installedVersion,
		@Nullable String loadedVersion, String gameVersion, String loader) {
		if (dep.versionId() != null && installedVersion == null && loadedVersion != null) {
			if (pinned == null) {
				return new DependencyPlan(null, "could not find the exact version it needs to compare with " + loadedVersion + ", which is loaded");
			}
			return sameVersion(loadedVersion, pinned) ? new DependencyPlan(null, null)
				: new DependencyPlan(null, "it needs version " + pinned.number() + ", but " + loadedVersion + " is loaded");
		}
		if (dep.versionId() == null && installedVersion == null && loadedVersion != null) {
			return new DependencyPlan(null, null);
		}
		if (dep.versionId() != null) {
			if (pinned == null) {
				return new DependencyPlan(null, "could not find the exact version it needs");
			}
			if (!compatible(pinned, gameVersion, loader)) {
				return new DependencyPlan(null, "it needs version " + pinned.number() + ", which is not made for " + gameVersion + " " + loader);
			}
			if (installedVersion != null) {
				return installedVersion.equals(pinned.id()) ? new DependencyPlan(null, null)
					: new DependencyPlan(null, "it needs version " + pinned.number() + ", but another version is installed");
			}
			return pinned.primaryFile() == null ? new DependencyPlan(null, "version " + pinned.number() + " has no file to download")
				: new DependencyPlan(pinned, null);
		}
		if (installedVersion != null) {
			return new DependencyPlan(null, null);
		}
		List<Version> usable = candidates.stream().filter(v -> compatible(v, gameVersion, loader)).toList();
		Version best = best(usable);
		return best == null ? new DependencyPlan(null, "it has no version for " + gameVersion + " " + loader) : new DependencyPlan(best, null);
	}

	/** True if a loaded mod's version string is the Modrinth version {@code v} (its number, ignoring a leading "v"). */
	static boolean sameVersion(String loaded, Version v) {
		String a = loaded.trim().toLowerCase(Locale.ROOT);
		String b = v.number().trim().toLowerCase(Locale.ROOT);
		if (a.startsWith("v")) {
			a = a.substring(1);
		}
		if (b.startsWith("v")) {
			b = b.substring(1);
		}
		return !a.isEmpty() && a.equals(b);
	}

	/** The newest version to offer: the newest release, else the newest of any kind. Modrinth lists newest first. */
	public static @Nullable Version best(List<Version> versions) {
		for (Version v : versions) {
			if ("release".equals(v.type()) && v.primaryFile() != null) {
				return v;
			}
		}
		for (Version v : versions) {
			if (v.primaryFile() != null) {
				return v;
			}
		}
		return null;
	}

	/** True if {@code data} is exactly the file Modrinth describes (SHA-512, or SHA-1 when that is all there is). */
	public static boolean matches(byte[] data, FileRef file) {
		if (!file.sha512().isEmpty()) {
			return hex("SHA-512", data).equals(file.sha512());
		}
		if (!file.sha1().isEmpty()) {
			return hex("SHA-1", data).equals(file.sha1());
		}
		return false;
	}

	public static String sha1(byte[] data) {
		return hex("SHA-1", data);
	}

	static String hex(String algorithm, byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * A file name that is safe to write into the install folder: no folders, no odd characters, and the extension the
	 * kind needs (.jar for mods, .zip for packs). Returns null if nothing usable is left.
	 */
	public static @Nullable String safeFileName(String name, Kind kind) {
		String base = name.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._+\\- ]", "_").strip();
		while (base.startsWith(".")) {
			base = base.substring(1);
		}
		if (base.isEmpty()) {
			return null;
		}
		String lower = base.toLowerCase(Locale.ROOT);
		boolean ok = kind == Kind.MOD ? lower.endsWith(".jar") : lower.endsWith(".zip");
		return ok ? base : null;
	}

	/** "1.2M", "34.5k", "812": downloads the short way. */
	public static String shortCount(long n) {
		if (n >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0).replace(".0M", "M");
		}
		if (n >= 1_000) {
			return String.format(Locale.ROOT, "%.1fk", n / 1_000.0).replace(".0k", "k");
		}
		return Long.toString(n);
	}

	private static JsonArray arr(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static @Nullable String nullable(JsonObject o, String key) {
		String s = str(o, key);
		return s.isEmpty() ? null : s;
	}

	private static long num(JsonObject o, String key) {
		try {
			return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : arr(o, key)) {
			if (e.isJsonPrimitive()) {
				out.add(e.getAsString());
			}
		}
		return out;
	}
}
