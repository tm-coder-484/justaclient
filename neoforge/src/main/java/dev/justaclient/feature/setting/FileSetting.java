package dev.justaclient.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A file picked from one folder, such as a background picture. The value is the file name inside the folder; blank means
 * "the first file there". Names that would leave the folder are refused.
 */
public final class FileSetting extends Setting<String> {
	private final Supplier<Path> folder;
	public final List<String> extensions;
	public final String placeholder;

	public FileSetting(String id, String name, Supplier<Path> folder, List<String> extensions, String placeholder) {
		super(id, name, "");
		this.folder = folder;
		this.extensions = extensions.stream().map(e -> e.toLowerCase(Locale.ROOT)).toList();
		this.placeholder = placeholder;
	}

	public Path folder() {
		return folder.get();
	}

	@Override
	public void set(String value) {
		String v = value == null ? "" : value.trim();
		super.set(v.contains("/") || v.contains("\\") || v.startsWith(".") || v.length() > 128 ? "" : v);
	}

	public boolean accepts(Path file) {
		String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return Files.isRegularFile(file) && extensions.stream().anyMatch(n::endsWith);
	}

	/** The usable files in the folder, sorted by name. Creates the folder if it is missing. */
	public List<String> choices() {
		Path dir = folder();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(this::accepts).map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** The chosen file, the first file when blank, or null if there is none (or the chosen one is gone). */
	public @Nullable Path resolve() {
		Path dir = folder().toAbsolutePath().normalize();
		if (!value.isEmpty()) {
			Path path = dir.resolve(value).normalize();
			return path.startsWith(dir) && accepts(path) ? path : null;
		}
		List<String> files = choices();
		return files.isEmpty() ? null : dir.resolve(files.getFirst());
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(@Nullable JsonElement json) {
		if (json != null && json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
