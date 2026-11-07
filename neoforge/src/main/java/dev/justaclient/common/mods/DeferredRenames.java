package dev.justaclient.common.mods;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import dev.justaclient.platform.PlatformLoader;
import dev.justaclient.platform.PlatformLoader.ModContainer;
import dev.justaclient.platform.PlatformLoader.ModOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renames that could not happen while the game runs (Windows keeps loaded jars locked). They are saved to a file and done
 * by {@link RenameHelper} once the game has closed, or at the next start. Adapted from Tom's server-switcher mod (CC0).
 */
public final class DeferredRenames {
	private static final Logger LOG = LoggerFactory.getLogger("justaclient");
	private static final Map<String, Path> PENDING = new LinkedHashMap<>();
	/** Pending renames whose source and target both exist, by source path; shown in the Mods panel. */
	private static final Map<String, Path> CONFLICTS = new LinkedHashMap<>();
	private static boolean helperStarted;
	private static boolean relaunchRequested;

	private DeferredRenames() {
	}

	private static Path folder() {
		return PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("mods");
	}

	private static Path pendingFile() {
		return folder().resolve("pending-renames.txt");
	}

	private static Path relaunchFile() {
		return folder().resolve("relaunch.txt");
	}

	/** Called at start: does any renames left over from last time that can be done now. */
	public static synchronized void load() {
		try {
			Files.deleteIfExists(relaunchFile());
			for (String[] entry : RenameHelper.readPending(pendingFile())) {
				Path from = ModFiles.normalize(Path.of(entry[0]));
				Path to = ModFiles.normalize(Path.of(entry[1]));
				RenameHelper.Outcome outcome = RenameHelper.tryOnce(from, to);
				switch (outcome) {
					case DONE -> LOG.info("Applied pending rename {} -> {}", from, to.getFileName());
					case CONFLICT -> {
						LOG.warn("Pending rename {} -> {} kept: both files exist", from, to.getFileName());
						CONFLICTS.put(from.toString(), to);
					}
					default -> {
					}
				}
				if (!outcome.finished) {
					PENDING.put(from.toString(), to);
				}
			}
			save();
		} catch (IOException | RuntimeException e) {
			LOG.warn("Couldn't read pending mod renames", e);
		}
	}

	public static synchronized boolean isPending(Path from) {
		return PENDING.containsKey(ModFiles.normalize(from).toString());
	}

	/** Pending renames that can't happen because both files exist, as "source -> target" file names. */
	public static synchronized List<String> conflicts() {
		List<String> out = new ArrayList<>();
		CONFLICTS.forEach((from, to) -> out.add(Path.of(from).getFileName() + " and " + to.getFileName()));
		return out;
	}

	public static synchronized boolean anyPending() {
		return !PENDING.isEmpty();
	}

	static synchronized void add(Path from, Path to) {
		PENDING.put(ModFiles.normalize(from).toString(), ModFiles.normalize(to));
		save();
	}

	static synchronized void remove(Path from) {
		PENDING.remove(ModFiles.normalize(from).toString());
		CONFLICTS.remove(ModFiles.normalize(from).toString());
		save();
	}

	private static void save() {
		List<String[]> entries = new ArrayList<>();
		PENDING.forEach((from, to) -> entries.add(new String[] {from, to.toString()}));
		try {
			RenameHelper.writePending(pendingFile(), entries);
		} catch (IOException e) {
			LOG.error("Couldn't save pending mod renames", e);
		}
	}

	/** Asks the helper to start the game again with {@code command} after it closes. */
	public static synchronized void requestRelaunch(List<String> command, String workingDirectory) {
		try {
			Files.createDirectories(folder());
			RenameHelper.writeRelaunch(relaunchFile(), workingDirectory, command);
			relaunchRequested = true;
		} catch (IOException e) {
			LOG.error("Couldn't save the restart command; the game will close without starting again", e);
		}
	}

	/** Called as the game closes: starts the helper if anything is waiting. */
	public static synchronized void applyAfterExit() {
		if (helperStarted || (PENDING.isEmpty() && !relaunchRequested)) {
			return;
		}
		helperStarted = true;
		save();
		Optional<String> classpath = PlatformLoader.getInstance().getModContainer("justaclient").map(ModContainer::getOrigin)
			.filter(origin -> origin.getKind() == ModOrigin.Kind.PATH)
			.map(origin -> origin.getPaths().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		if (classpath.isEmpty()) {
			LOG.error("Couldn't find the client's jar; pending mod renames will be retried next launch");
			return;
		}
		try {
			Files.createDirectories(folder());
			ProcessBuilder builder = new ProcessBuilder(javaExecutable(), "-cp", classpath.get(), RenameHelper.class.getName(),
				Long.toString(ProcessHandle.current().pid()), pendingFile().toString(), relaunchFile().toString());
			builder.redirectErrorStream(true);
			builder.redirectOutput(ProcessBuilder.Redirect.appendTo(folder().resolve("rename-helper.log").toFile()));
			builder.start();
			LOG.info("Scheduled {} mod rename(s){} for after the game closes", PENDING.size(), relaunchRequested ? " and a restart" : "");
		} catch (IOException | RuntimeException e) {
			LOG.error("Couldn't start the rename helper; pending mod renames will be retried next launch", e);
		}
	}

	private static String javaExecutable() {
		Path bin = Path.of(System.getProperty("java.home"), "bin");
		Path javaw = bin.resolve("javaw.exe");
		if (Files.isRegularFile(javaw)) {
			return javaw.toString();
		}
		Optional<String> current = ProcessHandle.current().info().command();
		return current.isPresent() && Files.isRegularFile(Path.of(current.get())) ? current.get() : bin.resolve("java").toString();
	}
}
