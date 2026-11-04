package dev.justaclient.common.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RelauncherTest {
	private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";
	private static final String MODRINTH = "com.modrinth.theseus.MinecraftLaunch";

	@Test
	void knotGivesTheSameJavaCommand() {
		Optional<List<String>> command = Relauncher.buildDirectCommand("/usr/bin/java", KNOT + " --username Steve", List.of("-Xmx4G", "-Dfoo=bar"),
			"a.jar:b.jar", List.of("--username", "Steve", "--gameDir", "/mc"));
		assertEquals(List.of("/usr/bin/java", "-Xmx4G", "-Dfoo=bar", "-cp", "a.jar:b.jar", KNOT, "--username", "Steve", "--gameDir", "/mc"),
			command.orElseThrow());
	}

	@Test
	void theOldKnotPackageIsAccepted() {
		String legacy = "net.fabricmc.loader.launch.knot.KnotClient";
		List<String> command = Relauncher.buildDirectCommand("java", legacy, List.of(), "cp", List.of()).orElseThrow();
		assertEquals(List.of("java", "-cp", "cp", legacy), command);
	}

	@Test
	void debuggerArgumentsAreLeftOut() {
		List<String> command = Relauncher.buildDirectCommand("java", KNOT,
			List.of("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", "-Xrunjdwp:transport=dt_socket", "-Xss4M"), "cp", List.of())
			.orElseThrow();
		assertEquals(List.of("java", "-Xss4M", "-cp", "cp", KNOT), command);
	}

	@Test
	void theModrinthWrapperIsSkippedWithItsOwnOptions() {
		List<String> jvm = List.of("-Dmodrinth.internal.ipc.host=127.0.0.1", "-javaagent:/opt/Theseus/theseus.jar", "-javaagent:/other/agent.jar",
			"-Xmx2G");
		List<String> command = Relauncher.buildDirectCommand("java", MODRINTH + " " + KNOT, jvm, "cp", List.of("--demo")).orElseThrow();
		assertEquals(List.of("java", "-javaagent:/other/agent.jar", "-Xmx2G", "-cp", "cp", KNOT, "--demo"), command);
	}

	@Test
	void modrinthOptionsAreKeptWithoutTheWrapper() {
		List<String> command = Relauncher.buildDirectCommand("java", KNOT, List.of("-Dmodrinth.internal.x=1"), "cp", List.of()).orElseThrow();
		assertTrue(command.contains("-Dmodrinth.internal.x=1"));
	}

	@Test
	void unknownMainClassesCannotBeRestarted() {
		assertTrue(Relauncher.buildDirectCommand("java", "net.minecraft.client.main.Main --demo", List.of(), "cp", List.of()).isEmpty());
		assertTrue(Relauncher.buildDirectCommand("java", "", List.of(), "cp", List.of()).isEmpty());
		assertTrue(Relauncher.buildDirectCommand("java", MODRINTH, List.of(), "cp", List.of()).isEmpty());
		assertTrue(Relauncher.buildDirectCommand("java", MODRINTH + " com.example.Other", List.of(), "cp", List.of()).isEmpty());
	}

	@Test
	void aMissingClassPathCannotBeRestarted() {
		assertTrue(Relauncher.buildDirectCommand("java", KNOT, List.of(), null, List.of()).isEmpty());
		assertTrue(Relauncher.buildDirectCommand("java", KNOT, List.of(), "", List.of()).isEmpty());
	}
}
