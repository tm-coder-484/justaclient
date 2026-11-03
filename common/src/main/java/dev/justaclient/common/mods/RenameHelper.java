package dev.justaclient.common.mods;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A tiny program started just before the game closes. It waits for the game to exit (Windows keeps loaded jars locked),
 * renames the mod jars that were switched, then optionally starts the game again. It only uses the JDK, so it runs with
 * the client jar alone on the class path. Adapted from Tom's server-switcher mod (CC0).
 */
public final class RenameHelper {
	private static final int ATTEMPTS = 60;
	private static final long RETRY_MILLIS = 500L;
	private static final long RELAUNCH_DELAY_MILLIS = 2000L;

	private RenameHelper() {
	}

	public static void main(String[] args) throws Exception {
		long pid = Long.parseLong(args[0]);
		Path pendingFile = Path.of(args[1]);
		PrintStream log = System.out;
		ProcessHandle.of(pid).ifPresent(handle -> {
			try {
				handle.onExit().get(30L, TimeUnit.MINUTES);
			} catch (Exception e) {
				log.println(LocalDateTime.now() + " Gave up waiting for Minecraft (pid " + pid + ") to exit: " + e);
			}
		});
		List<String[]> remaining = new ArrayList<>();
		for (String[] entry : readPending(pendingFile)) {
			if (!apply(Path.of(entry[0]), Path.of(entry[1]), log).finished) {
				remaining.add(entry);
			}
		}
		writePending(pendingFile, remaining);
		if (args.length > 2) {
			relaunch(Path.of(args[2]), log);
		}
	}

	private static void relaunch(Path relaunchFile, PrintStream log) throws IOException {
		if (!Files.exists(relaunchFile)) {
			return;
		}
		List<String> lines = Files.readAllLines(relaunchFile, StandardCharsets.UTF_8);
		Files.deleteIfExists(relaunchFile);
		if (lines.size() < 2) {
			return;
		}
		try {
			Thread.sleep(RELAUNCH_DELAY_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		ProcessBuilder builder = new ProcessBuilder(lines.subList(1, lines.size()));
		builder.directory(Path.of(lines.getFirst()).toFile());
		builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);
		builder.start();
		log.println(LocalDateTime.now() + " Restarted Minecraft");
	}

	public static void writeRelaunch(Path file, String workingDirectory, List<String> command) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(workingDirectory);
		lines.addAll(command);
		Files.write(file, lines, StandardCharsets.UTF_8);
	}

	/** What one try at a pending rename found. */
	public enum Outcome {
		/** Renamed now. */
		DONE(true),
		/** Already renamed: the source is gone and the target is there. */
		ALREADY_DONE(true),
		/** Neither file exists any more, so there is nothing left to do. */
		GONE(true),
		/** Both files exist. Renaming would overwrite one, so the entry stays pending until the player removes one. */
		CONFLICT(false),
		/** The move failed, most likely because the game still holds the jar. */
		LOCKED(false);

		/** Whether the entry can leave the pending list. */
		public final boolean finished;

		Outcome(boolean finished) {
			this.finished = finished;
		}
	}

	/** Tries a pending rename once, never overwriting an existing target. */
	public static Outcome tryOnce(Path from, Path to) {
		boolean source = Files.exists(from);
		boolean target = Files.exists(to);
		if (!source) {
			return target ? Outcome.ALREADY_DONE : Outcome.GONE;
		}
		if (target) {
			return Outcome.CONFLICT;
		}
		try {
			Files.move(from, to);
			return Outcome.DONE;
		} catch (IOException e) {
			return Files.exists(to) && !Files.exists(from) ? Outcome.DONE : Outcome.LOCKED;
		}
	}

	/** Applies one entry, retrying while the jar is locked. Returns the final outcome. */
	static Outcome apply(Path from, Path to, PrintStream log) throws InterruptedException {
		Outcome outcome = Outcome.LOCKED;
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			outcome = tryOnce(from, to);
			if (outcome != Outcome.LOCKED) {
				break;
			}
			Thread.sleep(RETRY_MILLIS);
		}
		String what = switch (outcome) {
			case DONE -> "Renamed";
			case ALREADY_DONE -> "Already renamed";
			case GONE -> "Dropped (neither file exists)";
			case CONFLICT -> "Kept pending, both files exist:";
			case LOCKED -> "Couldn't rename, will retry next time:";
		};
		log.println(LocalDateTime.now() + " " + what + " " + from + " -> " + to.getFileName());
		return outcome;
	}

	public static List<String[]> readPending(Path file) throws IOException {
		List<String[]> entries = new ArrayList<>();
		if (!Files.exists(file)) {
			return entries;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String[] parts = line.split("\t");
			if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
				entries.add(parts);
			}
		}
		return entries;
	}

	public static void writePending(Path file, List<String[]> entries) throws IOException {
		if (entries.isEmpty()) {
			Files.deleteIfExists(file);
			return;
		}
		Files.createDirectories(file.getParent());
		List<String> lines = new ArrayList<>();
		for (String[] entry : entries) {
			lines.add(entry[0] + "\t" + entry[1]);
		}
		Files.write(file, lines, StandardCharsets.UTF_8);
	}
}
