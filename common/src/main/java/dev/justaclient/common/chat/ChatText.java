package dev.justaclient.common.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Chat logic with no game classes: who sent a line, whether it mentions you, where your keywords are, tab and search
 * filters, timestamps and copy formats. The chat features and the self-test both use it.
 */
public final class ChatText {
	public enum Kind {
		PLAYER, PRIVATE, SYSTEM
	}

	/**
	 * One line as it arrived. {@code sender} is empty when it could not be told; {@code signed} is true for player chat
	 * the game delivered as chat (not a server line that looks like chat).
	 */
	public record Entry(long time, String text, String sender, Kind kind, boolean mention, boolean signed) {
		public Entry(long time, String text, String sender, Kind kind, boolean mention) {
			this(time, text, sender, kind, mention, false);
		}
	}

	public enum TimeStyle {
		SHORT("HH:mm"), SECONDS("HH:mm:ss"), TWELVE("h:mm a");

		final DateTimeFormatter format;

		TimeStyle(String pattern) {
			this.format = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
		}
	}

	public enum CopyFormat {
		/** Just the text. */
		PLAIN,
		/** "[12:34] text". */
		WITH_TIME,
		/** "> text" with the time and sender, for pasting into Discord or notes. */
		QUOTE
	}

	private static final String NAME = "([A-Za-z0-9_]{2,16})";
	private static final List<Pattern> PRIVATE = List.of(
		Pattern.compile("^" + NAME + " whispers(?: to you)?: "),
		Pattern.compile("^\\[" + NAME + " (?:->|→) (?:me|you)\\]"),
		Pattern.compile("^From " + NAME + ": "),
		Pattern.compile("^\\(From " + NAME + "\\)"),
		Pattern.compile("^" + NAME + " (?:->|→) (?:me|you)\\b"));
	private static final List<Pattern> PLAYER = List.of(
		Pattern.compile("^<" + NAME + "> "),
		// "[VIP] Steve: hi", "[Guild] [MVP+] Steve » hi", "Steve: hi"
		Pattern.compile("^(?:\\[[^\\]]{1,24}\\] ?){0,3}" + NAME + " ?(?::|»|>>|\\|) "));
	private static final Pattern LINK = Pattern.compile("(?i)\\bhttps?://\\S+|\\bwww\\.\\S+");

	private ChatText() {
	}

	/** The player who sent {@code text}, or "" for server lines. */
	public static String sender(String text) {
		for (Pattern p : PRIVATE) {
			Matcher m = p.matcher(text);
			if (m.find()) {
				return m.group(1);
			}
		}
		for (Pattern p : PLAYER) {
			Matcher m = p.matcher(text);
			if (m.find()) {
				return m.group(1);
			}
		}
		return "";
	}

	public static Kind kind(String text) {
		for (Pattern p : PRIVATE) {
			if (p.matcher(text).find()) {
				return Kind.PRIVATE;
			}
		}
		return sender(text).isEmpty() ? Kind.SYSTEM : Kind.PLAYER;
	}

	/**
	 * Where any of {@code words} appears in {@code text} as a whole word, ignoring case: pairs of start and end
	 * indexes, in order and never overlapping.
	 */
	public static List<int[]> spans(String text, List<String> words) {
		List<int[]> out = new ArrayList<>();
		String lower = text.toLowerCase(Locale.ROOT);
		for (String raw : words) {
			String w = raw.trim().toLowerCase(Locale.ROOT);
			if (w.isEmpty()) {
				continue;
			}
			int from = 0;
			while (true) {
				int at = lower.indexOf(w, from);
				if (at < 0) {
					break;
				}
				int end = at + w.length();
				boolean startOk = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1)) && lower.charAt(at - 1) != '_';
				boolean endOk = end == lower.length() || !Character.isLetterOrDigit(lower.charAt(end)) && lower.charAt(end) != '_';
				if (startOk && endOk && out.stream().noneMatch(s -> at < s[1] && end > s[0])) {
					out.add(new int[] {at, end});
				}
				from = at + 1;
			}
		}
		out.sort((a, b) -> Integer.compare(a[0], b[0]));
		return out;
	}

	/** True if a line you did not send yourself names you or one of your keywords. */
	public static boolean mentions(String text, String self, List<String> keywords) {
		String sender = sender(text);
		if (!self.isEmpty() && (sender.equalsIgnoreCase(self) || aboutSelf(text, self))) {
			return false;
		}
		// Look past the sender's name, so "<You> hi" style echoes and your own name as a prefix do not count.
		String body = sender.isEmpty() ? text : text.substring(Math.min(text.length(), text.indexOf(sender) + sender.length()));
		List<String> words = new ArrayList<>(keywords);
		if (!self.isEmpty()) {
			words.add(self);
		}
		return !spans(body, words).isEmpty();
	}

	/** True for server lines about you ("Steve joined the game", "Steve has made the advancement..."), which are not mentions. */
	public static boolean aboutSelf(String text, String self) {
		return !self.isEmpty() && text.regionMatches(true, 0, self, 0, self.length()) && text.length() > self.length()
			&& text.charAt(self.length()) == ' ';
	}

	public static String time(long millis, TimeStyle style) {
		return style.format.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	public static String copy(Entry e, CopyFormat format, TimeStyle style) {
		return switch (format) {
			case PLAIN -> e.text;
			case WITH_TIME -> "[" + time(e.time, style) + "] " + e.text;
			case QUOTE -> "> " + e.text + "\n> — " + (e.sender.isEmpty() ? "server" : e.sender) + ", " + time(e.time, style);
		};
	}

	/** A custom tab: a name and the words that put a line in it. Written "Trade: buy, sell, wts". */
	public record Tab(String name, List<String> words) {
		public static @Nullable Tab parse(String raw) {
			int colon = raw.indexOf(':');
			if (colon <= 0) {
				return null;
			}
			String name = raw.substring(0, colon).trim();
			List<String> words = new ArrayList<>();
			for (String w : raw.substring(colon + 1).split(",")) {
				if (!w.isBlank()) {
					words.add(w.trim());
				}
			}
			return name.isEmpty() || words.isEmpty() ? null : new Tab(name, words);
		}

		public boolean matches(Entry e) {
			return !spans(e.text, words).isEmpty();
		}
	}

	/**
	 * A search: free words (all must appear) plus filters {@code from:Name}, {@code has:link}, {@code is:mention},
	 * {@code is:private}, {@code is:system}, {@code is:player}.
	 */
	public record Query(List<String> words, @Nullable String from, boolean link, @Nullable Kind kind, boolean mention) {
		public static Query parse(String raw) {
			List<String> words = new ArrayList<>();
			String from = null;
			boolean link = false;
			boolean mention = false;
			Kind kind = null;
			for (String part : raw.trim().split("\\s+")) {
				String p = part.toLowerCase(Locale.ROOT);
				if (p.isEmpty()) {
					continue;
				}
				if (p.startsWith("from:") && p.length() > 5) {
					from = part.substring(5);
				} else if (p.equals("has:link")) {
					link = true;
				} else if (p.equals("is:mention")) {
					mention = true;
				} else if (p.equals("is:private")) {
					kind = Kind.PRIVATE;
				} else if (p.equals("is:system")) {
					kind = Kind.SYSTEM;
				} else if (p.equals("is:player")) {
					kind = Kind.PLAYER;
				} else {
					words.add(p);
				}
			}
			return new Query(words, from, link, kind, mention);
		}

		public boolean matches(Entry e) {
			if (from != null && !e.sender.equalsIgnoreCase(from)) {
				return false;
			}
			if (link && !LINK.matcher(e.text).find()) {
				return false;
			}
			if (kind != null && e.kind != kind) {
				return false;
			}
			if (mention && !e.mention) {
				return false;
			}
			String lower = e.text.toLowerCase(Locale.ROOT);
			for (String w : words) {
				if (!lower.contains(w)) {
					return false;
				}
			}
			return true;
		}
	}
}
