package dev.justaclient.common.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModrinthTest {
	private static Modrinth.FileRef file(String name, boolean primary) {
		return new Modrinth.FileRef("https://cdn.modrinth.com/data/x/" + name, name, "", "", 10, primary);
	}

	private static Modrinth.Version version(String id, String number, String type, List<String> games, List<String> loaders) {
		return new Modrinth.Version(id, "proj", id, number, type, "2026-09-20T10:00:00Z", List.of(file(id + ".jar", true)), List.of(), loaders,
			games);
	}

	@Test
	void searchUrlFiltersByVersionLoaderAndClientSide() {
		String url = URLDecoder.decode(Modrinth.searchUrl(" sodium ", Modrinth.Kind.MOD, "1.21.11", "fabric", Modrinth.Sort.DOWNLOADS, 20, 10),
			StandardCharsets.UTF_8);
		assertTrue(url.startsWith(Modrinth.API + "/search?limit=10&offset=20&index=downloads"));
		assertTrue(url.contains("[\"project_type:mod\"]"));
		assertTrue(url.contains("[\"versions:1.21.11\"]"));
		assertTrue(url.contains("[\"categories:fabric\"]"));
		assertTrue(url.contains("[\"client_side:required\",\"client_side:optional\"]"));
		assertTrue(url.endsWith("&query=sodium"));
		assertFalse(Modrinth.searchUrl("  ", Modrinth.Kind.SHADER, "26.3", "fabric", Modrinth.Sort.RELEVANCE, 0, 20).contains("query="));
	}

	@Test
	void versionsUrlUsesTheKindsLoader() {
		String shader = URLDecoder.decode(Modrinth.versionsUrl("iris", Modrinth.Kind.SHADER, "26.3", "fabric"), StandardCharsets.UTF_8);
		assertTrue(shader.contains("game_versions=[\"26.3\"]"));
		assertTrue(shader.contains("loaders=[\"iris\"]"));
		String mod = URLDecoder.decode(Modrinth.versionsUrl("a b", Modrinth.Kind.MOD, "26.3", "fabric"), StandardCharsets.UTF_8);
		assertTrue(mod.contains("/project/a b/version"));
		assertTrue(mod.contains("loaders=[\"fabric\"]"));
	}

	@Test
	void parseVersionsToleratesMissingAndNullFields() {
		String json = """
			[{"id":"v1","project_id":"p","version_number":"1.0","version_type":"release","date_published":"2026-01-02T03:04:05Z",
			  "files":[{"url":"https://cdn.modrinth.com/a.jar","filename":"a.jar","hashes":{"sha1":"AB","sha512":"CD"},"size":5,"primary":null},
			           {"url":"https://cdn.modrinth.com/b.jar","filename":"b.jar","size":"oops"}],
			  "dependencies":[{"project_id":"dep","dependency_type":"required"},{"version_id":null,"dependency_type":"optional"}],
			  "loaders":["fabric"],"game_versions":["1.21.11"]},
			 {"id":"v2"}]
			""";
		List<Modrinth.Version> list = Modrinth.parseVersions(json);
		assertEquals(2, list.size());
		Modrinth.Version v = list.getFirst();
		assertEquals("2026-01-02", v.date());
		assertEquals("ab", v.files().get(0).sha1());
		assertEquals("cd", v.files().get(0).sha512());
		assertFalse(v.files().get(0).primary());
		assertEquals(0, v.files().get(1).size());
		assertEquals("a.jar", v.primaryFile().filename());
		assertTrue(v.dependencies().get(0).required());
		assertNull(v.dependencies().get(1).versionId());
		assertNull(list.get(1).primaryFile());
		assertTrue(Modrinth.parseVersions("{\"error\":\"not found\"}").isEmpty());
	}

	@Test
	void parseVersionFilesLowercasesHashes() {
		Map<String, Modrinth.Version> out = Modrinth.parseVersionFiles("{\"ABC\":{\"id\":\"v\",\"project_id\":\"p\"},\"def\":null}");
		assertEquals(1, out.size());
		assertEquals("p", out.get("abc").projectId());
	}

	@Test
	void bestPrefersTheNewestReleaseWithAFile() {
		Modrinth.Version beta = version("beta", "2.0-beta", "beta", List.of("1.21.11"), List.of("fabric"));
		Modrinth.Version release = version("rel", "1.9", "release", List.of("1.21.11"), List.of("fabric"));
		Modrinth.Version empty = new Modrinth.Version("none", "proj", "", "3.0", "release", "", List.of(), List.of(), List.of(), List.of());
		assertEquals("rel", Modrinth.best(List.of(empty, beta, release)).id());
		assertEquals("beta", Modrinth.best(List.of(empty, beta)).id());
		assertNull(Modrinth.best(List.of(empty)));
	}

	@Test
	void planPinnedDependency() {
		Modrinth.Dependency pin = new Modrinth.Dependency("proj", "v2", "required");
		Modrinth.Version v2 = version("v2", "v2.0", "release", List.of("1.21.11"), List.of("fabric"));
		Modrinth.Version wrongGame = version("v2", "2.0", "release", List.of("1.21.10"), List.of("fabric"));
		assertEquals("v2", Modrinth.plan(pin, v2, List.of(), null, "1.21.11", "fabric").install().id());
		assertNotNull(Modrinth.plan(pin, null, List.of(), null, "1.21.11", "fabric").problem());
		assertNotNull(Modrinth.plan(pin, wrongGame, List.of(), null, "1.21.11", "fabric").problem());
		Modrinth.DependencyPlan same = Modrinth.plan(pin, v2, List.of(), "v2", "1.21.11", "fabric");
		assertNull(same.install());
		assertNull(same.problem());
		assertNotNull(Modrinth.plan(pin, v2, List.of(), "v1", "1.21.11", "fabric").problem());
		// Loaded from outside the folder: a matching version (leading "v" ignored) is enough, another is reported.
		Modrinth.DependencyPlan loaded = Modrinth.plan(pin, v2, List.of(), null, "2.0", "1.21.11", "fabric");
		assertNull(loaded.install());
		assertNull(loaded.problem());
		Modrinth.DependencyPlan other = Modrinth.plan(pin, v2, List.of(), null, "1.9", "1.21.11", "fabric");
		assertNull(other.install());
		assertTrue(other.problem().contains("1.9"));
	}

	@Test
	void planProjectDependency() {
		Modrinth.Dependency any = new Modrinth.Dependency("proj", null, "required");
		Modrinth.Version ok = version("ok", "1.0", "release", List.of("1.21.11"), List.of("fabric"));
		Modrinth.Version forge = version("forge", "1.1", "release", List.of("1.21.11"), List.of("forge"));
		assertEquals("ok", Modrinth.plan(any, null, List.of(forge, ok), null, "1.21.11", "fabric").install().id());
		assertNotNull(Modrinth.plan(any, null, List.of(forge), null, "1.21.11", "fabric").problem());
		assertNull(Modrinth.plan(any, null, List.of(ok), "something", "1.21.11", "fabric").install());
		assertNull(Modrinth.plan(any, null, List.of(ok), null, "0.1", "1.21.11", "fabric").install());
	}

	@Test
	void matchesChecksSha512ThenSha1() {
		byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
		String sha1 = Modrinth.sha1(data);
		String sha512 = Modrinth.hex("SHA-512", data);
		assertTrue(Modrinth.matches(data, new Modrinth.FileRef("", "", sha1, sha512, 5, true)));
		assertTrue(Modrinth.matches(data, new Modrinth.FileRef("", "", sha1, "", 5, true)));
		assertFalse(Modrinth.matches(data, new Modrinth.FileRef("", "", sha1, "00", 5, true)));
		assertFalse(Modrinth.matches(data, new Modrinth.FileRef("", "", "", "", 5, true)));
	}

	@Test
	void safeFileNameKeepsWritesInTheFolder() {
		assertEquals("sodium-1.0.jar", Modrinth.safeFileName("sodium-1.0.jar", Modrinth.Kind.MOD));
		assertEquals("evil.jar", Modrinth.safeFileName("../../evil.jar", Modrinth.Kind.MOD));
		assertEquals("evil.jar", Modrinth.safeFileName("C:\\Windows\\evil.jar", Modrinth.Kind.MOD));
		assertEquals("hidden.jar", Modrinth.safeFileName("...hidden.jar", Modrinth.Kind.MOD));
		assertEquals("a_b_.zip", Modrinth.safeFileName("a:b?.zip", Modrinth.Kind.SHADER));
		assertNull(Modrinth.safeFileName("pack.zip", Modrinth.Kind.MOD));
		assertNull(Modrinth.safeFileName("mod.jar", Modrinth.Kind.RESOURCE_PACK));
		assertNull(Modrinth.safeFileName("../", Modrinth.Kind.MOD));
	}

	@Test
	void shortCount() {
		assertEquals("812", Modrinth.shortCount(812));
		assertEquals("1k", Modrinth.shortCount(1_000));
		assertEquals("34.5k", Modrinth.shortCount(34_500));
		assertEquals("1.2M", Modrinth.shortCount(1_234_567));
	}
}
