package dev.justaclient.feature.impl;

import com.google.gson.JsonObject;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.render.Icons;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * Shows "Playing Justaclient" with what you are doing on your Discord profile, over Discord's local IPC pipe. Needs the
 * Discord desktop app running and an application ID from discord.com/developers (the application's name is what
 * Discord shows as the game).
 */
public final class DiscordPresenceFeature extends Feature {
	private final TextSetting appId = add(new TextSetting("app_id", "Application ID", "", 24, "from discord.com/developers"));
	private final BoolSetting showServer = bool("server", "Show server address", true);
	private final BoolSetting showDimension = bool("dimension", "Show dimension", true);
	private final long started = System.currentTimeMillis() / 1000L;
	private volatile @Nullable Thread worker;
	/** Bumped on every enable and disable; a worker from an older generation stops. */
	private volatile int generation;

	public DiscordPresenceFeature() {
		super("discord", "Discord status", "Show what you are playing on your Discord profile", Category.UTILITY, Icons.GLOBE, false);
	}

	@Override
	protected void onEnable() {
		int gen = ++generation;
		Thread t = new Thread(() -> loop(gen), "Justaclient Discord");
		t.setDaemon(true);
		worker = t;
		t.start();
	}

	@Override
	protected void onDisable() {
		generation++;
		Thread t = worker;
		if (t != null) {
			t.interrupt();
		}
	}

	private void loop(int gen) {
		while (gen == generation) {
			String id = appId.get().trim();
			if (!id.matches("\\d{15,22}")) {
				sleep(5000);
				continue;
			}
			try (Pipe pipe = Pipe.open()) {
				if (pipe == null) {
					sleep(15000);
					continue;
				}
				JsonObject hello = new JsonObject();
				hello.addProperty("v", 1);
				hello.addProperty("client_id", id);
				pipe.write(0, hello.toString());
				pipe.read();
				String last = "";
				while (gen == generation && id.equals(appId.get().trim())) {
					JsonObject activity = activity();
					// Only send changes; compare the activity alone since every frame carries a new nonce.
					if (!activity.toString().equals(last)) {
						pipe.write(1, frame(activity));
						pipe.read();
						last = activity.toString();
					}
					sleep(5000);
				}
				if (gen != generation) {
					// Clear the status on the way out instead of leaving it until Discord notices the pipe closed.
					pipe.write(1, frame(null));
					pipe.read();
				}
			} catch (IOException e) {
				Justaclient.LOGGER.debug("Discord IPC unavailable: {}", e.toString());
				sleep(15000);
			}
		}
	}

	private JsonObject activity() {
		Minecraft mc = Minecraft.getInstance();
		String details;
		String state;
		if (mc.level == null) {
			details = "In the menus";
			state = null;
		} else if (mc.isLocalServer()) {
			details = "Singleplayer";
			state = dimension(mc);
		} else {
			ServerData server = mc.getCurrentServer();
			details = server != null && showServer.on() && !StreamModeFeature.hidesServer() ? "On " + server.ip : "Multiplayer";
			state = dimension(mc);
		}
		JsonObject activity = new JsonObject();
		activity.addProperty("details", details);
		if (state != null) {
			activity.addProperty("state", state);
		}
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", started);
		activity.add("timestamps", timestamps);
		return activity;
	}

	private @Nullable String dimension(Minecraft mc) {
		if (!showDimension.on() || mc.level == null) {
			return null;
		}
		String path = mc.level.dimension().identifier().getPath().replace('_', ' ');
		return "In the " + path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
	}

	private static String frame(@Nullable JsonObject activity) {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		if (activity != null) {
			args.add("activity", activity);
		}
		JsonObject root = new JsonObject();
		root.addProperty("cmd", "SET_ACTIVITY");
		root.add("args", args);
		root.addProperty("nonce", UUID.randomUUID().toString());
		return root.toString();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	/** Discord's IPC: a named pipe on Windows, a Unix socket elsewhere. Frames are an op code, a length and JSON. */
	private static final class Pipe implements Closeable {
		private final @Nullable RandomAccessFile file;
		private final @Nullable SocketChannel socket;

		private Pipe(@Nullable RandomAccessFile file, @Nullable SocketChannel socket) {
			this.file = file;
			this.socket = socket;
		}

		static @Nullable Pipe open() {
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			for (int i = 0; i < 10; i++) {
				try {
					if (windows) {
						return new Pipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"), null);
					}
					for (Path dir : socketDirs()) {
						Path path = dir.resolve("discord-ipc-" + i);
						if (Files.exists(path)) {
							return new Pipe(null, SocketChannel.open(UnixDomainSocketAddress.of(path)));
						}
					}
				} catch (IOException ignored) {
					// Try the next pipe number.
				}
			}
			return null;
		}

		private static Path[] socketDirs() {
			String base = System.getenv("XDG_RUNTIME_DIR");
			if (base == null) {
				base = System.getenv("TMPDIR");
			}
			Path root = Path.of(base != null ? base : "/tmp");
			// Flatpak and Snap builds of Discord put the socket in a subfolder.
			return new Path[] {root, root.resolve("app/com.discordapp.Discord"), root.resolve("snap.discord"), Path.of("/tmp")};
		}

		void write(int op, String json) throws IOException {
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
			buf.putInt(op).putInt(body.length).put(body).flip();
			if (file != null) {
				file.write(buf.array());
			} else if (socket != null) {
				while (buf.hasRemaining()) {
					socket.write(buf);
				}
			}
		}

		/** Reads and drops one reply frame. */
		void read() throws IOException {
			ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
			readFully(header);
			header.flip();
			header.getInt();
			int length = header.getInt();
			if (length < 0 || length > 1 << 20) {
				throw new IOException("Bad IPC frame");
			}
			readFully(ByteBuffer.allocate(length));
		}

		private void readFully(ByteBuffer buf) throws IOException {
			if (file != null) {
				file.readFully(buf.array());
				buf.position(buf.limit());
				return;
			}
			while (buf.hasRemaining()) {
				if (socket == null || socket.read(buf) < 0) {
					throw new IOException("Discord closed the pipe");
				}
			}
		}

		@Override
		public void close() throws IOException {
			if (file != null) {
				file.close();
			}
			if (socket != null) {
				socket.close();
			}
		}
	}
}
