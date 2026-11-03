package dev.justaclient.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrashReportsTest {
	private static final Set<String> IGNORE = Set.of("minecraft", "java", "fabricloader", "fabric-api");

	@Test
	void readsDescriptionExceptionAndDetail() {
		String text = """
			---- Minecraft Crash Report ----
			Time: 2026-11-03 10:00:00
			Description: Ticking entity

			java.lang.NullPointerException: Cannot invoke "Object.toString()" because "x" is null
				at net.minecraft.class_1297.method_5773(class_1297.java:10)
			""";
		CrashReports.Info info = CrashReports.parse(text, Set.of(), IGNORE);
		assertEquals("Ticking entity", info.description());
		assertEquals("java.lang.NullPointerException", info.exception());
		assertEquals("Cannot invoke \"Object.toString()\" because \"x\" is null", info.detail());
		assertFalse(info.nativeCrash());
		assertTrue(info.explanation().contains("bug while the world was running"));
	}

	@Test
	void namesModsFromMixinHandlersButNotIgnoredOrUnknownOnes() {
		String text = """
			Description: Rendering overlay
			java.lang.IllegalStateException: boom
				at net.minecraft.class_310.handler$zza000$sodium$onRender(class_310.java:1)
				at net.minecraft.class_310.handler$zzb000$minecraft$tick(class_310.java:2)
				at net.minecraft.class_310.handler$zzc000$notinstalled$tick(class_310.java:3)
			""";
		CrashReports.Info info = CrashReports.parse(text, Set.of("sodium", "minecraft"), IGNORE);
		assertEquals(List.of("sodium"), info.suspects());
	}

	@Test
	void namesTheModOfAFailingMixinConfig() {
		String text = """
			Description: Initializing game
			org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
			Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: Critical injection failure in iris.mixins.json:MixinFoo
			""";
		CrashReports.Info info = CrashReports.parse(text, Set.of("iris"), IGNORE);
		assertEquals(List.of("iris"), info.suspects());
		assertTrue(info.explanation().contains("Two mods"));
	}

	@Test
	void suspectsAreUniqueAndAtMostFive() {
		StringBuilder text = new StringBuilder("java.lang.RuntimeException: x\n");
		for (String id : List.of("aa", "bb", "cc", "dd", "ee", "ff", "aa")) {
			text.append("\tat a.B.handler$zz0$").append(id).append("$m(B.java:1)\n");
		}
		CrashReports.Info info = CrashReports.parse(text.toString(), Set.of("aa", "bb", "cc", "dd", "ee", "ff"), IGNORE);
		assertEquals(List.of("aa", "bb", "cc", "dd", "ee"), info.suspects());
	}

	@Test
	void recognisesTheDebugCrashAndOutOfMemory() {
		CrashReports.Info debug = CrashReports.parse("Description: Manually triggered debug crash\njava.lang.Throwable: Manually triggered debug crash\n", Set.of(),
			IGNORE);
		assertEquals("Nothing to fix.", debug.advice());
		CrashReports.Info oom = CrashReports.parse("Description: Unexpected error\njava.lang.OutOfMemoryError: Java heap space\n", Set.of(), IGNORE);
		assertEquals("java.lang.OutOfMemoryError", oom.exception());
		assertTrue(oom.explanation().contains("out of memory"));
	}

	@Test
	void versionMismatchErrorsGetTheirOwnExplanation() {
		CrashReports.Info info = CrashReports.parse("java.lang.NoSuchMethodError: 'void net.minecraft.class_1.method_2()'\n", Set.of(), IGNORE);
		assertTrue(info.explanation().contains("different version"));
	}

	@Test
	void aJvmCrashInTheGraphicsDriverIsNative() {
		String text = """
			#
			# A fatal error has been detected by the Java Runtime Environment:
			#
			#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb, pid=1234, tid=5678
			#
			# Problematic frame:
			# C  [nvoglv64.dll+0xabc]
			#
			""";
		CrashReports.Info info = CrashReports.parse(text, Set.of(), IGNORE);
		assertTrue(info.nativeCrash());
		assertEquals("C  [nvoglv64.dll+0xabc]", info.detail());
		assertTrue(info.explanation().contains("graphics driver"));
	}

	@Test
	void unknownTextGetsTheGeneralExplanation() {
		CrashReports.Info info = CrashReports.parse("", Set.of(), IGNORE);
		assertEquals("", info.exception());
		assertTrue(info.suspects().isEmpty());
		assertTrue(info.explanation().contains("could not recover"));
	}
}
