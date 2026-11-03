package dev.justaclient.common.mods;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Works out how to start the game again after a restart for mod changes: through Prism or MultiMC when it was launched by
 * one, otherwise with the same Java command line. Adapted from Tom's server-switcher mod (CC0).
 */
public final class Relauncher {
	private static final Set<String> DIRECT_MAIN_CLASSES = Set.of("net.fabricmc.loader.impl.launch.knot.KnotClient",
		"net.fabricmc.loader.launch.knot.KnotClient");
	private static final String MODRINTH_WRAPPER = "com.modrinth.theseus.MinecraftLaunch";
	private static final List<String> INSTANCE_LAUNCHERS = List.of("prismlauncher", "multimc", "polymc", "pollymc");

	private Relauncher() {
	}

	public static Optional<List<String>> launchCommand() {
		Optional<List<String>> viaLauncher = instanceLauncherCommand();
		return viaLauncher.isPresent() ? viaLauncher : directCommand();
	}

	/** Asks for a restart after the game closes, if a way to start it is known. Returns false if it cannot restart itself. */
	public static boolean requestRestart() {
		Optional<List<String>> command = launchCommand();
		command.ifPresent(c -> DeferredRenames.requestRelaunch(c, System.getProperty("user.dir")));
		return command.isPresent();
	}

	private static Optional<List<String>> instanceLauncherCommand() {
		try {
			String instanceId = System.getenv("INST_ID");
			if (instanceId == null || instanceId.isBlank()) {
				return Optional.empty();
			}
			Optional<String> launcher = ProcessHandle.current().parent().flatMap(parent -> parent.info().command());
			if (launcher.isEmpty()) {
				return Optional.empty();
			}
			String name = Path.of(launcher.get()).getFileName().toString().toLowerCase(Locale.ROOT);
			return INSTANCE_LAUNCHERS.stream().noneMatch(name::startsWith) ? Optional.empty()
				: Optional.of(List.of(launcher.get(), "--launch", instanceId));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static Optional<List<String>> directCommand() {
		try {
			Optional<String> java = ProcessHandle.current().info().command();
			if (java.isEmpty()) {
				return Optional.empty();
			}
			return buildDirectCommand(java.get(), System.getProperty("sun.java.command", ""),
				withoutEnvironmentOptions(ManagementFactory.getRuntimeMXBean().getInputArguments()), System.getProperty("java.class.path"),
				Arrays.asList(FabricLoader.getInstance().getLaunchArguments(false)));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	/** Options from JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS are added again by the JVM, so they are left out. */
	private static List<String> withoutEnvironmentOptions(List<String> arguments) {
		List<String> fromEnvironment = new ArrayList<>();
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) {
			String value = System.getenv(variable);
			if (value != null && !value.isBlank()) {
				fromEnvironment.addAll(Arrays.asList(value.trim().split("\\s+")));
			}
		}
		List<String> result = new ArrayList<>(arguments);
		for (String option : fromEnvironment) {
			result.remove(option);
		}
		return result;
	}

	public static Optional<List<String>> buildDirectCommand(String java, String sunCommand, List<String> jvmArguments, String classpath,
		List<String> gameArguments) {
		String[] words = sunCommand.split(" ");
		String mainClass = words[0];
		boolean modrinth = mainClass.equals(MODRINTH_WRAPPER);
		if (modrinth) {
			mainClass = words.length > 1 ? words[1] : "";
		}
		if (!DIRECT_MAIN_CLASSES.contains(mainClass) || classpath == null || classpath.isEmpty()) {
			return Optional.empty();
		}
		List<String> command = new ArrayList<>();
		command.add(java);
		for (String argument : jvmArguments) {
			boolean debugger = argument.startsWith("-agentlib:jdwp") || argument.startsWith("-Xrunjdwp");
			boolean modrinthOnly = modrinth && (argument.startsWith("-Dmodrinth.internal.")
				|| (argument.startsWith("-javaagent:") && argument.toLowerCase(Locale.ROOT).contains("theseus")));
			if (!debugger && !modrinthOnly) {
				command.add(argument);
			}
		}
		command.add("-cp");
		command.add(classpath);
		command.add(mainClass);
		command.addAll(gameArguments);
		return Optional.of(command);
	}
}
