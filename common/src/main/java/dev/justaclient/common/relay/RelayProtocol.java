package dev.justaclient.common.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The messages the client and the relay Worker (cloudflare/src/relay.js) send each other over the WebSocket, as JSON
 * objects with a type in {@code t}. The client sends hello, friends, profile, dm and ping; the relay sends challenge,
 * welcome, presence, dm, ack, error and pong.
 */
public final class RelayProtocol {
	public static final int MAX_TEXT = 500;

	/** A friend's state as the relay sees it. {@code pending} means they have not added you back yet. */
	public record Presence(String name, boolean online, boolean pending, String status, String bio, String server, long lastSeen) {
	}

	public record Dm(String id, String from, String text, long at, boolean queued) {
	}

	/** What happened to a message you sent: delivered, queued, not_friends, inbox_full, slow_down or invalid. */
	public record Ack(String id, String state) {
	}

	private RelayProtocol() {
	}

	/** The relay's WebSocket address for a Worker address: https becomes wss, http becomes ws, and /relay is added. */
	public static @Nullable String socketUrl(String server) {
		String s = server == null ? "" : server.trim();
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.isEmpty()) {
			return null;
		}
		String lower = s.toLowerCase(Locale.ROOT);
		if (lower.startsWith("https://")) {
			s = "wss://" + s.substring(8);
		} else if (lower.startsWith("http://")) {
			s = "ws://" + s.substring(7);
		} else if (!lower.startsWith("ws://") && !lower.startsWith("wss://")) {
			s = "wss://" + s;
		}
		return s.endsWith("/relay") ? s : s + "/relay";
	}

	public static String hello(String name, String version) {
		JsonObject o = type("hello");
		o.addProperty("name", name);
		o.addProperty("version", version);
		return o.toString();
	}

	public static String friends(List<String> names) {
		JsonObject o = type("friends");
		JsonArray a = new JsonArray();
		names.forEach(a::add);
		o.add("names", a);
		return o.toString();
	}

	public static String profile(String status, String bio, String server) {
		JsonObject o = type("profile");
		o.addProperty("status", status);
		o.addProperty("bio", bio);
		o.addProperty("server", server);
		return o.toString();
	}

	public static String dm(String id, String to, String text) {
		JsonObject o = type("dm");
		o.addProperty("id", id);
		o.addProperty("to", to);
		o.addProperty("text", text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
		return o.toString();
	}

	public static String ping() {
		return type("ping").toString();
	}

	private static JsonObject type(String t) {
		JsonObject o = new JsonObject();
		o.addProperty("t", t);
		return o;
	}

	/** Parses a frame from the relay; null if it is not a JSON object with a type. */
	public static @Nullable JsonObject parse(String frame) {
		try {
			JsonElement e = JsonParser.parseString(frame);
			if (e.isJsonObject() && e.getAsJsonObject().has("t")) {
				return e.getAsJsonObject();
			}
		} catch (RuntimeException ignored) {
			// Not JSON.
		}
		return null;
	}

	public static String type(JsonObject o) {
		return str(o, "t");
	}

	public static Presence presence(JsonObject o) {
		return new Presence(str(o, "name"), bool(o, "online"), bool(o, "pending"), str(o, "status"), str(o, "bio"), str(o, "server"),
			num(o, "lastSeen"));
	}

	public static Dm dmIn(JsonObject o) {
		return new Dm(str(o, "id"), str(o, "from"), str(o, "text"), num(o, "at"), bool(o, "queued"));
	}

	public static Ack ack(JsonObject o) {
		return new Ack(str(o, "id"), str(o, "state"));
	}

	public static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	static long num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0L;
		} catch (NumberFormatException ex) {
			return 0L;
		}
	}

	/** Words for an ack state, shown under a sent message. */
	public static String describe(String state) {
		return switch (state) {
			case "delivered" -> "Delivered";
			case "queued" -> "Held for them: they are offline";
			case "not_friends" -> "Not sent: they have not added you as a friend";
			case "inbox_full" -> "Not sent: their inbox is full";
			case "slow_down" -> "Not sent: too many messages, wait a moment";
			case "invalid" -> "Not sent";
			case "sending" -> "Sending";
			case "offline" -> "Not sent: you are not connected";
			default -> state;
		};
	}

	/** "just now", "5 min ago", "3 h ago", "2 days ago". */
	public static String ago(long then, long now) {
		long s = Math.max(0, (now - then) / 1000);
		if (s < 60) {
			return "just now";
		}
		if (s < 3600) {
			return s / 60 + " min ago";
		}
		if (s < 86400) {
			return s / 3600 + " h ago";
		}
		long d = s / 86400;
		return d == 1 ? "yesterday" : d + " days ago";
	}
}
