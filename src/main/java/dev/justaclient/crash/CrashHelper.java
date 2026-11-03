package dev.justaclient.crash;

import dev.justaclient.Justaclient;
import dev.justaclient.common.CrashReports;
import dev.justaclient.feedback.FeedbackSender;
import dev.justaclient.profile.Profiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Notices a crash from the last time the game ran (a crash report or a JVM hs_err log newer than the last one handled)
 * and builds the diagnostic zip for it.
 */
public final class CrashHelper {
	public static final Path DIAGNOSTICS = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("diagnostics");
	private static final Path SEEN = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("crash-seen.txt");
	private static final Set<String> IGNORE = Set.of("minecraft", "java", "fabricloader", "mixinextras", "fabric-api", "fabric");

	public record Crash(Path file, String text, CrashReports.Info info) {
	}

	private CrashHelper() {
	}

	private static Path game() {
		return FabricLoader.getInstance().getGameDir();
	}

	/** The newest crash report or JVM crash log, if any. */
	public static Optional<Path> newest() {
		Optional<Path> report = FeedbackSender.newestCrash(game());
		Optional<Path> jvm = Optional.empty();
		try (Stream<Path> files = Files.list(game())) {
			jvm = files.filter(p -> p.getFileName().toString().startsWith("hs_err_pid") && p.toString().endsWith(".log"))
				.max(Comparator.comparingLong(p -> p.toFile().lastModified()));
		} catch (IOException ignored) {
			// No game folder listing, no JVM log.
		}
		if (report.isEmpty()) {
			return jvm;
		}
		if (jvm.isEmpty()) {
			return report;
		}
		return Optional.of(report.get().toFile().lastModified() >= jvm.get().toFile().lastModified() ? report.get() : jvm.get());
	}

	/**
	 * A crash that has not been shown yet, or null. The first launch with the helper only records the newest crash, so an
	 * old one is never brought up.
	 */
	public static @Nullable Crash unseen() {
		Optional<Path> newest = newest();
		String marker = "";
		boolean hadMarker = Files.exists(SEEN);
		try {
			marker = hadMarker ? Files.readString(SEEN).strip() : "";
		} catch (IOException ignored) {
			// Treated as no marker.
		}
		if (newest.isEmpty()) {
			if (!hadMarker) {
				markSeen(null);
			}
			return null;
		}
		String key = key(newest.get());
		if (!hadMarker) {
			markSeen(newest.get());
			return null;
		}
		if (key.equals(marker)) {
			return null;
		}
		return read(newest.get());
	}

	public static @Nullable Crash read(Path file) {
		try {
			byte[] bytes = Files.readAllBytes(file);
			String text = new String(bytes, StandardCharsets.UTF_8);
			Set<String> ids = FabricLoader.getInstance().getAllMods().stream().map(m -> m.getMetadata().getId()).collect(Collectors.toSet());
			return new Crash(file, text, CrashReports.parse(text, ids, IGNORE));
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not read {}", file, e);
			return null;
		}
	}

	private static String key(Path file) {
		return file.getFileName() + "@" + file.toFile().lastModified();
	}

	public static void markSeen(@Nullable Path file) {
		try {
			Files.createDirectories(SEEN.getParent());
			Files.writeString(SEEN, file == null ? "none" : key(file));
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not write {}", SEEN, e);
		}
	}

	/** Display names of mods by id, for the suspects list. */
	public static String modName(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getName() + " (" + m.getMetadata().getVersion().getFriendlyString() + ")")
			.orElse(id);
	}

	/** Writes the mod list to the diagnostics folder and returns the file. */
	public static Path exportModList() throws IOException {
		Files.createDirectories(DIAGNOSTICS);
		Path file = DIAGNOSTICS.resolve("mods_" + stamp() + ".txt");
		Files.writeString(file, FeedbackSender.modList());
		return file;
	}

	/**
	 * Zips the crash, the log of the session that crashed, the mod list, system details and the active profile into the
	 * diagnostics folder. Nothing leaves the computer.
	 */
	public static Path buildZip(Crash crash) throws IOException {
		Files.createDirectories(DIAGNOSTICS);
		Path zip = DIAGNOSTICS.resolve("justaclient-diagnostic_" + stamp() + ".zip");
		try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
			add(z, crash.file().getFileName().toString(), crash.file());
			// The log of the run that crashed is the newest archived one; latest.log is this run.
			Path logs = game().resolve("logs");
			if (Files.isDirectory(logs)) {
				try (Stream<Path> files = Files.list(logs)) {
					Optional<Path> previous = files.filter(p -> p.toString().endsWith(".log.gz") && !p.getFileName().toString().startsWith("debug"))
						.max(Comparator.comparingLong(p -> p.toFile().lastModified()));
					if (previous.isPresent()) {
						add(z, "logs/" + previous.get().getFileName(), previous.get());
					}
				}
				Path latest = logs.resolve("latest.log");
				if (Files.isRegularFile(latest)) {
					add(z, "logs/latest.log", latest);
				}
			}
			text(z, "mods.txt", FeedbackSender.modList());
			text(z, "system.txt", system());
			Profiles.save();
			Path profile = Profiles.DIR.resolve(Profiles.active().replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json");
			if (Files.isRegularFile(profile)) {
				add(z, "justaclient-profile.json", profile);
			}
			text(z, "README.txt", "Justaclient diagnostic, made " + LocalDateTime.now() + ".\n\nWhat probably happened: " + crash.info().explanation()
				+ "\nWhat to try: " + crash.info().advice() + "\n\nAttach this zip to a bug report for the mod you think is at fault.\n");
		}
		return zip;
	}

	private static String system() {
		Runtime rt = Runtime.getRuntime();
		return "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n"
			+ "Java: " + System.getProperty("java.version") + " " + System.getProperty("java.vendor") + "\n"
			+ "Max memory: " + rt.maxMemory() / (1024 * 1024) + " MB\n"
			+ "CPUs: " + rt.availableProcessors() + "\n";
	}

	private static void add(ZipOutputStream z, String name, Path file) throws IOException {
		z.putNextEntry(new ZipEntry(name));
		try (InputStream in = Files.newInputStream(file)) {
			in.transferTo(z);
		}
		z.closeEntry();
	}

	private static void text(ZipOutputStream z, String name, String text) throws IOException {
		z.putNextEntry(new ZipEntry(name));
		z.write(text.getBytes(StandardCharsets.UTF_8));
		z.closeEntry();
	}

	private static String stamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
	}
}
