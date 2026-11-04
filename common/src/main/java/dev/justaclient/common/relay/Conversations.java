package dev.justaclient.common.relay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Your direct messages, by friend, kept on this computer only (the relay forgets a message once it is delivered).
 * Each conversation keeps its last {@link #MAX} messages.
 */
public final class Conversations {
	public static final int MAX = 300;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Message {
		public String id;
		public boolean mine;
		public String text;
		public long at;
		/** For your own messages: sending, delivered, queued or why it was not sent. */
		public String state;

		public Message(String id, boolean mine, String text, long at, String state) {
			this.id = id;
			this.mine = mine;
			this.text = text;
			this.at = at;
			this.state = state;
		}
	}

	private Map<String, List<Message>> byFriend = new LinkedHashMap<>();
	private Map<String, Integer> unread = new LinkedHashMap<>();

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public List<Message> with(String friend) {
		return byFriend.getOrDefault(key(friend), List.of());
	}

	public void add(String friend, Message m) {
		List<Message> list = byFriend.computeIfAbsent(key(friend), k -> new ArrayList<>());
		if (list.stream().anyMatch(x -> x.id.equals(m.id) && x.mine == m.mine)) {
			return;
		}
		list.add(m);
		while (list.size() > MAX) {
			list.removeFirst();
		}
		if (!m.mine) {
			unread.merge(key(friend), 1, Integer::sum);
		}
	}

	/** Updates the state of one of your messages; returns true if it was found. */
	public boolean setState(String id, String state) {
		for (List<Message> list : byFriend.values()) {
			for (Message m : list) {
				if (m.mine && m.id.equals(id)) {
					m.state = state;
					return true;
				}
			}
		}
		return false;
	}

	public int unread(String friend) {
		return unread.getOrDefault(key(friend), 0);
	}

	public int unreadTotal() {
		return unread.values().stream().mapToInt(Integer::intValue).sum();
	}

	public void read(String friend) {
		unread.remove(key(friend));
	}

	/** The time of the newest message with {@code friend}, or 0. */
	public long lastAt(String friend) {
		List<Message> list = with(friend);
		return list.isEmpty() ? 0 : list.getLast().at;
	}

	public void clear() {
		byFriend.clear();
		unread.clear();
	}

	private record Saved(Map<String, List<Message>> byFriend, Map<String, Integer> unread) {
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(new Saved(byFriend, unread)));
		Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	public void load(Path file) {
		clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			Saved s = GSON.fromJson(Files.readString(file), new TypeToken<Saved>() { }.getType());
			if (s != null && s.byFriend() != null) {
				s.byFriend().forEach((k, v) -> {
					if (v != null) {
						List<Message> list = new ArrayList<>();
						v.stream().filter(m -> m != null && m.id != null && m.text != null).forEach(list::add);
						byFriend.put(key(k), list);
					}
				});
			}
			if (s != null && s.unread() != null) {
				unread.putAll(s.unread());
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			clear();
		}
	}
}
