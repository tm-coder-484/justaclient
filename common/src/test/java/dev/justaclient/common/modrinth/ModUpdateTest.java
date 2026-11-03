package dev.justaclient.common.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.justaclient.common.mods.ManagedMod;
import dev.justaclient.common.mods.ModStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModUpdateTest {
	@TempDir
	Path dir;

	private static ManagedMod mod(String id, ModStatus status, @Nullable Path file, @Nullable Path loaded) {
		return new ManagedMod(id, id, "1", "", List.of(), Map.of(), null, status, file, loaded, status == ModStatus.LOCKED ? "locked" : null,
			Set.of(id), Set.of(), loaded != null, false);
	}

	@Test
	void nothingToRetireLeavesTheNewJarOn() throws IOException {
		Path fresh = Files.writeString(dir.resolve("demo-2.jar"), "new");
		ModUpdate.Result r = ModUpdate.retireOld(null, fresh);
		assertTrue(r.oldOff());
		assertTrue(r.newActive());
		assertEquals(fresh, r.newFile());
	}

	@Test
	void anOldVersionThatIsAlreadyOffIsNotSwitchedBackOn() throws IOException {
		Path fresh = Files.writeString(dir.resolve("demo-2.jar"), "new");
		Path oldOff = Files.writeString(dir.resolve("demo-1.jar.disabled"), "old");
		ModUpdate.Result r = ModUpdate.retireOld(mod("demo", ModStatus.DISABLED, oldOff, null), fresh);
		assertTrue(r.oldOff());
		assertTrue(Files.exists(oldOff));
		assertFalse(Files.exists(dir.resolve("demo-1.jar")));
	}

	@Test
	void anEnabledOldVersionIsTurnedOff() throws IOException {
		Path fresh = Files.writeString(dir.resolve("demo-2.jar"), "new");
		Path old = Files.writeString(dir.resolve("demo-1.jar"), "old");
		ModUpdate.Result r = ModUpdate.retireOld(mod("demo", ModStatus.ENABLE_PENDING, old, null), fresh);
		assertTrue(r.oldOff());
		assertTrue(r.newActive());
		assertFalse(Files.exists(old));
		assertTrue(Files.exists(dir.resolve("demo-1.jar.disabled")));
	}

	@Test
	void whenTheOldOneCannotBeTurnedOffTheNewOneIsSetAside() throws IOException {
		Path fresh = Files.writeString(dir.resolve("demo-2.jar"), "new");
		Path old = Files.writeString(dir.resolve("demo-1.jar"), "old");
		Files.writeString(dir.resolve("demo-1.jar.disabled"), "older copy");
		ModUpdate.Result r = ModUpdate.retireOld(mod("demo", ModStatus.ENABLE_PENDING, old, null), fresh);
		assertFalse(r.oldOff());
		assertFalse(r.newActive());
		assertEquals(dir.resolve("demo-2.jar.disabled"), r.newFile());
		assertTrue(r.message().contains("could not be turned off"));
		assertEquals("old", Files.readString(old));
	}

	@Test
	void clashFindsAnotherActiveCopyWithTheSameId() {
		Path fresh = dir.resolve("demo-2.jar");
		ManagedMod self = mod("demo", ModStatus.ENABLE_PENDING, fresh, null);
		ManagedMod offCopy = mod("demo", ModStatus.DISABLED, dir.resolve("demo-0.jar.disabled"), null);
		ManagedMod other = mod("other", ModStatus.ENABLED, dir.resolve("other.jar"), dir.resolve("other.jar"));
		assertNull(ModUpdate.clash("demo", fresh, List.of(self, offCopy, other)));
		ManagedMod renamed = mod("demo", ModStatus.ENABLED, dir.resolve("Demo (1).jar"), dir.resolve("Demo (1).jar"));
		assertSame(renamed, ModUpdate.clash("demo", fresh, List.of(self, renamed)));
		ManagedMod outside = mod("demo", ModStatus.LOCKED, null, null);
		assertSame(outside, ModUpdate.clash("demo", fresh, List.of(self, outside)));
		// The same file under a path written another way is not a clash.
		ManagedMod sameFile = mod("demo", ModStatus.ENABLE_PENDING, dir.resolve("x/../demo-2.jar"), null);
		assertNull(ModUpdate.clash("demo", fresh, List.of(sameFile)));
	}

	@Test
	void keepOneSetsTheNewJarAside() throws IOException {
		Path fresh = Files.writeString(dir.resolve("demo-2.jar"), "new");
		ModUpdate.Result r = ModUpdate.keepOne(mod("demo", ModStatus.ENABLED, dir.resolve("Demo (1).jar"), null), fresh);
		assertFalse(r.newActive());
		assertFalse(Files.exists(fresh));
		assertTrue(Files.exists(dir.resolve("demo-2.jar.disabled")));
		assertTrue(r.message().contains("Demo (1).jar"));
	}

	@Test
	void indexOnlyKeepsAnInstallThatWillLoad() {
		Map<String, String> index = new HashMap<>(Map.of("demo", "demo-1.jar"));
		ModUpdate.index(index, "demo", "demo-1.jar", "demo-2.jar", false);
		assertEquals("demo-1.jar", index.get("demo"));
		ModUpdate.index(index, "demo", "demo-1.jar", "demo-2.jar", true);
		assertEquals("demo-2.jar", index.get("demo"));
		Map<String, String> empty = new HashMap<>();
		ModUpdate.index(empty, "demo", null, "demo-2.jar", false);
		assertFalse(empty.containsKey("demo"));
	}
}
