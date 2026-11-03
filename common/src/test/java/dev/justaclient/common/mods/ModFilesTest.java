package dev.justaclient.common.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModFilesTest {
	@TempDir
	Path dir;

	@Test
	void jarNamesIgnoreCase() {
		assertTrue(ModFiles.isJar(dir.resolve("sodium.jar")));
		assertTrue(ModFiles.isJar(dir.resolve("Sodium.JAR")));
		assertFalse(ModFiles.isJar(dir.resolve("sodium.jar.disabled")));
		assertFalse(ModFiles.isJar(dir.resolve("readme.txt")));
	}

	@Test
	void disabledJarNamesIgnoreCase() {
		assertTrue(ModFiles.isDisabledJar(dir.resolve("sodium.jar.disabled")));
		assertTrue(ModFiles.isDisabledJar(dir.resolve("Sodium.Jar.DISABLED")));
		assertFalse(ModFiles.isDisabledJar(dir.resolve("notes.disabled")));
		assertFalse(ModFiles.isDisabledJar(dir.resolve("sodium.jar")));
	}

	@Test
	void disablingAndEnablingAreInverse() {
		Path jar = dir.resolve("My Mod-1.0.jar");
		Path off = ModFiles.disabledPath(jar);
		assertEquals("My Mod-1.0.jar.disabled", off.getFileName().toString());
		assertEquals(dir, off.getParent());
		assertEquals(jar, ModFiles.enabledPath(off));
	}

	@Test
	void enablingKeepsTheJarsOwnCase() {
		assertEquals("Mod.JAR", ModFiles.enabledPath(dir.resolve("Mod.JAR.DISABLED")).getFileName().toString());
	}

	@Test
	void enabledNameIsTheSameForBothStatesAndIsAbsolute() {
		Path jar = dir.resolve("a.jar");
		String name = ModFiles.enabledName(jar);
		assertEquals(name, ModFiles.enabledName(ModFiles.disabledPath(jar)));
		assertEquals(name, ModFiles.enabledName(dir.resolve("sub").resolve("..").resolve("a.jar.disabled")));
		assertTrue(Path.of(name).isAbsolute());
	}
}
