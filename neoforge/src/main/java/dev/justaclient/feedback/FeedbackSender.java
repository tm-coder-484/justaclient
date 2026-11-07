package dev.justaclient.feedback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.FeedbackFeature;
import dev.justaclient.profile.Profiles;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import dev.justaclient.platform.PlatformLoader;
import dev.justaclient.platform.PlatformLoader.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

/** Builds a feedback report (message plus the chosen attachments) and posts it to the Worker. */
public final class FeedbackSender {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SENT = PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("feedback-sent.json");
	private static final int LOG_BYTES = 300_000;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	/** One report you sent, kept so the Feedback page can list them. */
	public record Sent(String id, String created, String kind, String summary) {
	}

	private FeedbackSender() {
	}

	public record Attach(boolean log, boolean crash, boolean mods, boolean settings) {
	}

	/** Sends in the background; {@code done} runs on the render thread with null on success or an error to show. */
	public static void send(String kind, String message, Attach attach, Consumer<String> done) {
		FeedbackFeature feature = Features.get(FeedbackFeature.class);
		String server = feature.server.get().trim().replaceAll("/+$", "");
		if (server.isEmpty()) {
			done.accept("Set the server address in Utility > Feedback first.");
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		JsonObject body = new JsonObject();
		body.addProperty("kind", kind);
		body.addProperty("message", message);
		if (!feature.anonymous.on()) {
			body.addProperty("player", mc.getUser().getName());
		}
		body.addProperty("clientVersion", PlatformLoader.getInstance().getModContainer(Justaclient.MOD_ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		body.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
		body.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + ", Java " + System.getProperty("java.version"));
		if (attach.mods()) {
			body.addProperty("mods", modList());
		}
		if (attach.settings()) {
			Profiles.save();
			read(Profiles.DIR.resolve(Profiles.active() + ".json"), 200_000).ifPresent(s -> body.addProperty("settings", s));
		}
		Path game = PlatformLoader.getInstance().getGameDir();
		Thread thread = new Thread(() -> {
			if (attach.log()) {
				read(game.resolve("logs").resolve("latest.log"), LOG_BYTES).ifPresent(s -> body.addProperty("latestLog", s));
			}
			if (attach.crash()) {
				newestCrash(game).flatMap(p -> read(p, LOG_BYTES)).ifPresent(s -> body.addProperty("crashReport", s));
			}
			String error;
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(server + "/feedback"))
					.timeout(Duration.ofSeconds(20))
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				JsonObject reply = JsonParser.parseString(response.body()).getAsJsonObject();
				if (response.statusCode() == 200 && reply.has("id")) {
					remember(new Sent(reply.get("id").getAsString(), Instant.now().toString(), kind, summary(message)));
					error = null;
				} else {
					error = reply.has("error") ? reply.get("error").getAsString() : "The server said " + response.statusCode() + ".";
				}
			} catch (IOException | RuntimeException e) {
				error = "Could not reach the server (" + e.getClass().getSimpleName() + ").";
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				error = "Cancelled.";
			}
			String result = error;
			mc.execute(() -> done.accept(result));
		}, "Justaclient feedback");
		thread.setDaemon(true);
		thread.start();
	}

	public static String modList() {
		StringBuilder out = new StringBuilder();
		PlatformLoader.getInstance().getAllMods().stream()
			.sorted(Comparator.comparing((ModContainer m) -> m.getMetadata().getId()))
			.forEach(m -> out.append(m.getMetadata().getId()).append(' ').append(m.getMetadata().getVersion().getFriendlyString()).append('\n'));
		return out.toString();
	}

	public static Optional<Path> newestCrash(Path game) {
		Path dir = game.resolve("crash-reports");
		if (!Files.isDirectory(dir)) {
			return Optional.empty();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.toString().endsWith(".txt")).max(Comparator.comparingLong(p -> p.toFile().lastModified()));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** The last {@code max} bytes of a file, as text. */
	static Optional<String> read(Path file, int max) {
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
			long length = raf.length();
			long from = Math.max(0, length - max);
			byte[] bytes = new byte[(int) (length - from)];
			raf.seek(from);
			raf.readFully(bytes);
			return Optional.of(new String(bytes, StandardCharsets.UTF_8));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static String summary(String message) {
		String line = message.strip().replace('\n', ' ');
		return line.length() > 70 ? line.substring(0, 70) + "..." : line;
	}

	public static synchronized List<Sent> sent() {
		List<Sent> list = new ArrayList<>();
		if (Files.exists(SENT)) {
			try {
				JsonElement root = JsonParser.parseString(Files.readString(SENT));
				for (JsonElement e : root.getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					list.add(new Sent(o.get("id").getAsString(), o.get("created").getAsString(), o.get("kind").getAsString(), o.get("summary").getAsString()));
				}
			} catch (IOException | RuntimeException e) {
				Justaclient.LOGGER.warn("Could not read {}", SENT, e);
			}
		}
		return list;
	}

	private static synchronized void remember(Sent sent) {
		List<Sent> list = sent();
		list.addFirst(sent);
		JsonArray array = new JsonArray();
		for (Sent s : list.subList(0, Math.min(50, list.size()))) {
			JsonObject o = new JsonObject();
			o.addProperty("id", s.id());
			o.addProperty("created", s.created());
			o.addProperty("kind", s.kind());
			o.addProperty("summary", s.summary());
			array.add(o);
		}
		try {
			Files.createDirectories(SENT.getParent());
			Files.writeString(SENT, GSON.toJson(array));
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save {}", SENT, e);
		}
	}
}
