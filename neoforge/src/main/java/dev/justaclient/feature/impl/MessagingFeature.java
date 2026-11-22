package dev.justaclient.feature.impl;

import com.google.gson.JsonObject;
import dev.justaclient.Justaclient;
import dev.justaclient.common.relay.Conversations;
import dev.justaclient.common.relay.RelayProtocol;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ActionSetting;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.feedback.RelayClient;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * Friends, direct messages and profiles over the Justaclient relay (a Cloudflare Worker you deploy once, see
 * cloudflare/README.md). Off by default: nothing connects until you switch it on. Only friends who have added each other
 * can message; the relay keeps a message only until it is delivered, and your history stays on this computer.
 */
public final class MessagingFeature extends Feature {
	private static final Path HISTORY = PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("messages.json");

	public final TextSetting server = add(new TextSetting("server", "Relay address", "", 120, "Blank: the Feedback server address"));
	private final TextSetting status = add(new TextSetting("status", "Your status", "", 64, "What you are up to"));
	private final TextSetting bio = add(new TextSetting("bio", "Your profile text", "", 200, "A line about you, for friends"));
	private final BoolSetting shareServer = bool("share_server", "Show friends the server you are on", false);
	private final BoolSetting notify = bool("notify", "Pop up new messages", true);
	private final BoolSetting history = bool("history", "Keep message history on this computer", true);
	private final ActionSetting open = add(new ActionSetting("open", "Open messages...", () -> openScreen(null)));
	private final RelayClient relay = new RelayClient();
	private final Conversations conversations = new Conversations();
	private final Map<String, RelayProtocol.Presence> presence = new LinkedHashMap<>();
	/** What the relay was last told on this connection; null means nothing yet, so even an empty list goes out. */
	private @Nullable String sentFriends;
	private @Nullable String sentProfile;
	private boolean loaded;
	private boolean dirty;
	private long savedAt;
	/** The friend whose conversation is open on screen, so their messages do not pop up. */
	public @Nullable String viewing;

	public MessagingFeature() {
		super("messaging", "Messages", "Friends, direct messages and profiles over your own relay", Category.SOCIAL, Icons.MESSAGE_CIRCLE, false);
	}

	public static MessagingFeature get() {
		return Features.get(MessagingFeature.class);
	}

	public static void openScreen(@Nullable String friend) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.setScreen(new dev.justaclient.ui.MessagesScreen(mc.screen, friend)));
	}

	/** The relay's WebSocket address, from this feature's setting or the Feedback server address. */
	public @Nullable String relayUrl() {
		String s = server.get().trim();
		if (s.isEmpty()) {
			FeedbackFeature feedback = Features.get(FeedbackFeature.class);
			s = feedback == null ? "" : feedback.server.get().trim();
		}
		return RelayProtocol.socketUrl(s);
	}

	@Override
	protected void onEnable() {
		resetSync();
	}

	@Override
	protected void onDisable() {
		relay.close();
		presence.clear();
		save();
	}

	/**
	 * Reads the saved history once, the first time anything needs it while history is on. Not marking it loaded while
	 * history is off keeps a later save (after history is turned on) from writing over the file unread.
	 */
	public void ensureLoaded() {
		if (!loaded && history.on()) {
			loaded = true;
			conversations.loadKeeping(HISTORY);
		}
	}

	@Override
	public void tick() {
		ensureLoaded();
		String url = relayUrl();
		relay.keep(url);
		for (JsonObject o = relay.poll(); o != null; o = relay.poll()) {
			handle(o);
		}
		if (relay.state() == RelayClient.State.ONLINE) {
			syncState(relay::send, FriendsFeature.get().list(), status.get(), bio.get(), sharedServer());
		} else {
			resetSync();
		}
		if (dirty && System.currentTimeMillis() - savedAt > 5000) {
			save();
		}
	}

	/**
	 * Tells the relay the friends list and profile when they differ from what this connection was last told. The friends
	 * list always goes out once per connection, even when it is empty: that is how the relay learns every friend was
	 * removed. Returns how many frames were sent.
	 */
	public int syncState(java.util.function.Predicate<String> send, List<String> friends, String status, String bio, String server) {
		int sent = 0;
		String f = String.join(",", friends);
		if (!f.equals(sentFriends) && send.test(RelayProtocol.friends(friends))) {
			sentFriends = f;
			sent++;
		}
		String p = status + "\n" + bio + "\n" + server;
		if (!p.equals(sentProfile) && send.test(RelayProtocol.profile(status, bio, server))) {
			sentProfile = p;
			sent++;
		}
		return sent;
	}

	/** Forgets what the relay was told, so everything is sent again (a new connection starts empty on the relay side). */
	public void resetSync() {
		sentFriends = null;
		sentProfile = null;
	}

	private String sharedServer() {
		if (!shareServer.on()) {
			return "";
		}
		Minecraft mc = Minecraft.getInstance();
		ServerData data = mc.getCurrentServer();
		if (data != null) {
			return data.ip;
		}
		return mc.hasSingleplayerServer() ? "Singleplayer" : "";
	}

	private void handle(JsonObject o) {
		switch (RelayProtocol.type(o)) {
			case "welcome" -> {
				// A new session: the relay must hear the friends list again, even if it is empty now.
				presence.clear();
				resetSync();
			}
			case "presence" -> {
				RelayProtocol.Presence p = RelayProtocol.presence(o);
				RelayProtocol.@Nullable Presence before = presence.put(key(p.name()), p);
				if (before != null && !before.online() && p.online() && !p.pending() && notify.on()) {
					Notifications.push(Icons.USERS, p.name(), "is online");
				}
			}
			case "dm" -> {
				RelayProtocol.Dm dm = RelayProtocol.dmIn(o);
				conversations.add(dm.from(), new Conversations.Message(dm.id(), false, dm.text(), dm.at() > 0 ? dm.at() : System.currentTimeMillis(), ""));
				if (dm.from().equalsIgnoreCase(viewing)) {
					conversations.read(dm.from());
				} else if (notify.on()) {
					Notifications.push(Icons.MESSAGE_CIRCLE, dm.from(), dm.text().length() > 60 ? dm.text().substring(0, 57) + "..." : dm.text());
				}
				dirty = true;
			}
			case "ack" -> {
				RelayProtocol.Ack ack = RelayProtocol.ack(o);
				dirty |= conversations.setState(ack.id(), ack.state());
			}
			case "error" -> Notifications.push(Icons.MESSAGE_CIRCLE, "Messages", RelayProtocol.str(o, "message"));
			default -> {
			}
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/** Sends {@code text} to {@code friend}; returns false (and records why) when it cannot go now. */
	public boolean send(String friend, String text) {
		String clean = text.strip();
		if (clean.isEmpty()) {
			return false;
		}
		clean = RelayProtocol.cut(clean);
		String id = UUID.randomUUID().toString().substring(0, 12);
		boolean sent = relay.send(RelayProtocol.dm(id, friend, clean));
		conversations.add(friend, new Conversations.Message(id, true, clean, System.currentTimeMillis(), sent ? "sending" : "offline"));
		dirty = true;
		return sent;
	}

	public void save() {
		savedAt = System.currentTimeMillis();
		dirty = false;
		if (!history.on() || !loaded) {
			return;
		}
		try {
			conversations.save(HISTORY);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save messages", e);
		}
	}

	public RelayClient relay() {
		return relay;
	}

	public Conversations conversations() {
		ensureLoaded();
		return conversations;
	}

	/** What the relay last said about {@code friend}, or null if nothing yet. */
	public RelayProtocol.@Nullable Presence presence(String friend) {
		return presence.get(key(friend));
	}

	/** A short line for the connection: "Online", "Signing in", "Reconnecting soon: could not reach the relay". */
	public String connectionLine() {
		if (!isEnabled()) {
			return "Messages is off";
		}
		if (relayUrl() == null) {
			return "No relay address yet: set it in Social > Messages";
		}
		RelayClient.State s = relay.state();
		String line = s.label;
		if (s == RelayClient.State.ONLINE && !relay.verified()) {
			line += " (test mode: not checked with Mojang)";
		}
		if (s == RelayClient.State.WAITING && relay.error() != null) {
			line += ": " + relay.error();
		}
		return line;
	}
}
