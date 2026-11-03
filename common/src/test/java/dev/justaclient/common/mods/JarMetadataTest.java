package dev.justaclient.common.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarMetadataTest {
	@TempDir
	Path dir;

	private static byte[] zip(Map<String, byte[]> entries) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, byte[]> e : entries.entrySet()) {
				out.putNextEntry(new ZipEntry(e.getKey()));
				out.write(e.getValue());
				out.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private static byte[] modJar(String json, Map<String, byte[]> nested) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("fabric.mod.json", json.getBytes(StandardCharsets.UTF_8));
		entries.putAll(nested);
		return zip(entries);
	}

	private Path write(String name, byte[] bytes) throws IOException {
		return Files.write(dir.resolve(name), bytes);
	}

	private static String iconOf(String iconJson) {
		return "{\"id\":\"m\",\"icon\":" + iconJson + "}";
	}

	private JarMetadata readJson(String json) throws IOException {
		return JarMetadata.read(write("m.jar", modJar(json, Map.of())));
	}

	@Test
	void readsAPlainMod() throws IOException {
		JarMetadata m = readJson("""
			{"schemaVersion": 1, "id": "coolmod", "name": "Cool Mod", "version": "1.2.3", "description": "Does things",
			 "icon": "assets/coolmod/icon.png", "provides": ["cool"], "depends": {"fabricloader": ">=0.16", "minecraft": "~1.21", "cool": "*"}}""");
		assertEquals("coolmod", m.id());
		assertEquals("Cool Mod", m.name());
		assertEquals("1.2.3", m.version());
		assertEquals("Does things", m.description());
		assertEquals("assets/coolmod/icon.png", m.icon());
		assertEquals(Set.of("coolmod", "cool"), m.providedIds());
		assertEquals(Set.of("fabricloader", "minecraft"), m.requiredIds());
	}

	@Test
	void missingFieldsFallBack() throws IOException {
		JarMetadata m = JarMetadata.read(write("nameless.jar", modJar("{\"depends\": [\"not an object\"]}", Map.of())));
		assertEquals("nameless.jar", m.id());
		assertEquals("nameless.jar", m.name());
		assertEquals("?", m.version());
		assertEquals("", m.description());
		assertNull(m.icon());
		assertEquals(Set.of(), m.requiredIds());
	}

	@Test
	void lenientJsonWithCommentsIsRead() throws IOException {
		assertEquals("commented", readJson("{\n// a comment\n\"id\": \"commented\"\n}").id());
	}

	@Test
	void unreadableJarsGiveNull() throws IOException {
		assertNull(JarMetadata.read(write("broken.jar", modJar("{\"id\": \"x\", ", Map.of()))));
		assertNull(JarMetadata.read(write("array.jar", modJar("[1, 2]", Map.of()))));
		assertNull(JarMetadata.read(write("plain.jar", zip(Map.of("a.class", new byte[] {1, 2})))));
		assertNull(JarMetadata.read(write("notazip.jar", "hello".getBytes(StandardCharsets.UTF_8))));
		assertNull(JarMetadata.read(dir.resolve("missing.jar")));
	}

	@Test
	void nestedJarsAddTheirIdsAndDependencies() throws IOException {
		byte[] inner = modJar("{\"id\": \"inner-lib\", \"provides\": [\"innerapi\"], \"depends\": {\"cloth-config\": \"*\", \"outer\": \"*\"}}", Map.of());
		byte[] outer = modJar("""
			{"id": "outer", "depends": {"inner-lib": "*", "innerapi": "*", "fabric-api": "*"},
			 "jars": [{"file": "META-INF/jars/inner.jar"}, {"file": "META-INF/jars/missing.jar"}]}""",
			Map.of("META-INF/jars/inner.jar", inner, "META-INF/jars/unlisted.jar", modJar("{\"id\": \"unlisted\"}", Map.of())));
		JarMetadata m = JarMetadata.read(write("outer.jar", outer));
		assertEquals(Set.of("outer", "inner-lib", "innerapi"), m.providedIds());
		assertEquals(Set.of("fabric-api", "cloth-config"), m.requiredIds());
	}

	@Test
	void nestingIsFollowedThreeLevelsDeep() throws IOException {
		byte[] level4 = modJar("{\"id\": \"l4\"}", Map.of());
		byte[] level3 = modJar("{\"id\": \"l3\", \"jars\": [{\"file\": \"l4.jar\"}]}", Map.of("l4.jar", level4));
		byte[] level2 = modJar("{\"id\": \"l2\", \"jars\": [{\"file\": \"l3.jar\"}]}", Map.of("l3.jar", level3));
		byte[] level1 = modJar("{\"id\": \"l1\", \"jars\": [{\"file\": \"l2.jar\"}]}", Map.of("l2.jar", level2));
		byte[] top = modJar("{\"id\": \"top\", \"jars\": [{\"file\": \"l1.jar\"}]}", Map.of("l1.jar", level1));
		assertEquals(Set.of("top", "l1", "l2", "l3"), JarMetadata.read(write("top.jar", top)).providedIds());
	}

	@Test
	void iconPicksTheLargestUpTo128() throws IOException {
		assertEquals("b", readJson(iconOf("{\"16\": \"a\", \"64\": \"b\", \"256\": \"c\"}")).icon());
		assertEquals("b", readJson(iconOf("{\"256\": \"c\", \"16\": \"a\", \"64\": \"b\"}")).icon());
		assertEquals("e", readJson(iconOf("{\"128\": \"e\", \"32\": \"d\"}")).icon());
	}

	@Test
	void iconFallsBackToTheSmallestOver128() throws IOException {
		assertEquals("c", readJson(iconOf("{\"512\": \"d\", \"256\": \"c\"}")).icon());
		assertEquals("c", readJson(iconOf("{\"256\": \"c\", \"512\": \"d\"}")).icon());
	}

	@Test
	void iconIgnoresNonSizeKeysAndOddShapes() throws IOException {
		assertEquals("a", readJson(iconOf("{\"big\": \"x\", \"32\": \"a\", \"64\": {\"no\": 1}}")).icon());
		assertNull(readJson(iconOf("[\"a.png\"]")).icon());
		assertNull(readJson(iconOf("{}")).icon());
	}
}
