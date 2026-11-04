package dev.justaclient.feedback;

import com.google.gson.JsonObject;
import dev.justaclient.Justaclient;
import dev.justaclient.common.relay.RelayProtocol;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The connection to the messaging relay (system 8): one WebSocket, signed in through Mojang, reconnecting with a growing
 * wait when it drops. Frames from the relay are queued and handed over on the game thread by {@link #poll}.
 */
public final class RelayClient {
	public enum State {
		OFF("Off"), CONNECTING("Connecting"), SIGNING_IN("Signing in"), ONLINE("Online"), WAITING("Reconnecting soon");

		public final String label;

		State(String label) {
			this.label = label;
		}
	}

	private static final long[] BACKOFF = {3_000, 10_000, 30_000, 60_000};
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Queue<JsonObject> inbox = new ConcurrentLinkedQueue<>();
	private volatile State state = State.OFF;
	private volatile @Nullable WebSocket socket;
	private volatile @Nullable String error;
	private volatile String url = "";
	private volatile int failures;
	private volatile long retryAt;
	private long lastPing;
	/** When the relay last sent anything; it answers every ping, so a long silence means the connection is dead. */
	private volatile long lastInbound;
	private static final long SILENCE_MS = 75_000;
	private volatile boolean verified;
	private static final int MAX_PENDING = 64;
	/** The write queue of the current socket; a new socket starts a new one, so a stuck write cannot hold it up. */
	private volatile AtomicInteger pending = new AtomicInteger();
	private CompletableFuture<?> writes = CompletableFuture.completedFuture(null);

	public State state() {
		return state;
	}

	public @Nullable String error() {
		return error;
	}

	public boolean verified() {
		return verified;
	}

	/** Keeps a connection to {@code wanted} open (null closes it). Call every tick. */
	public void keep(@Nullable String wanted) {
		if (wanted == null) {
			close();
			error = null;
			return;
		}
		if (!wanted.equals(url)) {
			close();
			url = wanted;
			failures = 0;
			retryAt = 0;
		}
		long now = System.currentTimeMillis();
		if ((state == State.OFF || state == State.WAITING) && now >= retryAt) {
			connect();
		}
		WebSocket ws = socket;
		// A half-open connection (Wi-Fi change, sleep) never errors on its own; sending keeps "working" into the OS buffer.
		if ((state == State.SIGNING_IN || state == State.ONLINE) && ws != null && now - lastInbound > SILENCE_MS) {
			failed("the relay stopped answering");
			ws.abort();
			return;
		}
		if (state == State.ONLINE && ws != null && now - lastPing > 30_000) {
			lastPing = now;
			send(RelayProtocol.ping());
		}
	}

	private void connect() {
		state = State.CONNECTING;
		String target = url;
		http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(target), new Listener())
			.whenComplete((ws, e) -> {
				if (e != null || !target.equals(url)) {
					if (ws != null) {
						ws.abort();
					}
					if (target.equals(url)) {
						failed("could not reach the relay");
					}
					return;
				}
			});
	}

	private void failed(String why) {
		socket = null;
		error = why;
		long wait = BACKOFF[Math.min(failures, BACKOFF.length - 1)];
		failures++;
		retryAt = System.currentTimeMillis() + wait;
		state = State.WAITING;
	}

	public void close() {
		WebSocket ws = socket;
		socket = null;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(e -> null);
		}
		state = State.OFF;
		url = "";
		inbox.clear();
	}

	/**
	 * Queues a frame once signed in and returns at once; false if not signed in or too much is already waiting. Frames go
	 * out in order, one at a time (the JDK refuses a send while the previous one is still going out). If one fails, the
	 * connection is dropped and reconnects, so nothing after it is sent on a broken socket.
	 */
	public boolean send(String frame) {
		return state == State.ONLINE && write(frame);
	}

	private boolean write(String frame) {
		WebSocket ws = socket;
		if (ws == null) {
			return false;
		}
		synchronized (this) {
			AtomicInteger count = pending;
			if (count.get() >= MAX_PENDING) {
				return false;
			}
			count.incrementAndGet();
			writes = writes.thenCompose(v -> socket == ws ? ws.sendText(frame, true)
					: CompletableFuture.<WebSocket>failedFuture(new IllegalStateException("closed")))
				.handle((v, e) -> {
					count.decrementAndGet();
					if (e != null) {
						writeFailed(ws);
					}
					return null;
				});
		}
		return true;
	}

	/** Frames queued but not yet written. */
	public int pendingWrites() {
		return pending.get();
	}

	private void writeFailed(WebSocket ws) {
		if (socket == ws) {
			ws.abort();
			failed("could not send to the relay");
		}
	}

	private synchronized void newQueue() {
		pending = new AtomicInteger();
		writes = CompletableFuture.completedFuture(null);
	}

	/** Self-test only: use {@code ws} as if it had just signed in. */
	public void testAttach(WebSocket ws) {
		newQueue();
		lastInbound = System.currentTimeMillis();
		socket = ws;
		state = State.ONLINE;
	}

	/** The next frame from the relay that the game should handle, or null. */
	public @Nullable JsonObject poll() {
		return inbox.poll();
	}

	/** The relay asked us to prove who we are: tell Mojang we joined its id, then say hello. Runs off the game thread. */
	private void signIn(String serverId) {
		CompletableFuture.runAsync(() -> {
			Minecraft mc = Minecraft.getInstance();
			try {
				mc.services().sessionService().joinServer(mc.getUser().getProfileId(), mc.getUser().getAccessToken(), serverId);
			} catch (Exception e) {
				// Offline accounts cannot sign in; the relay decides whether to accept them (only in its test mode).
				Justaclient.LOGGER.debug("Relay sign-in with Mojang failed: {}", e.toString());
			}
			String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(Justaclient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
			write(RelayProtocol.hello(mc.getUser().getName(), version));
		});
	}

	private final class Listener implements WebSocket.Listener {
		private final StringBuilder partial = new StringBuilder();

		@Override
		public void onOpen(WebSocket ws) {
			// The challenge can arrive before buildAsync completes, so the socket is known from here.
			newQueue();
			lastInbound = System.currentTimeMillis();
			socket = ws;
			state = State.SIGNING_IN;
			ws.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			lastInbound = System.currentTimeMillis();
			partial.append(data);
			if (last) {
				String frame = partial.toString();
				partial.setLength(0);
				JsonObject o = RelayProtocol.parse(frame);
				if (o != null) {
					switch (RelayProtocol.type(o)) {
						case "challenge" -> signIn(RelayProtocol.str(o, "serverId"));
						case "welcome" -> {
							state = State.ONLINE;
							failures = 0;
							error = null;
							verified = o.has("verified") && o.get("verified").getAsBoolean();
							inbox.add(o);
						}
						case "error" -> {
							error = RelayProtocol.str(o, "message");
							inbox.add(o);
						}
						default -> inbox.add(o);
					}
				}
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
			if (socket == ws) {
				failed(error != null ? error : "the relay closed the connection");
			}
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable e) {
			// Only the current socket: a late error from an old one must not knock out a connect already under way.
			if (socket == ws) {
				failed("connection lost");
			}
		}
	}
}
