package dev.justaclient.common.chat;

import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Decides when Auto GG may speak: a line has to match one of the triggers, and the client sends at most one message per
 * game (per world joined) and never twice inside the cooldown, whatever the server prints.
 */
public final class GgGate {
	private long lastSent = Long.MIN_VALUE / 2;
	private @Nullable Object sentIn;

	/** The trigger {@code line} matches (ignoring case), or null. */
	public static @Nullable String trigger(String line, List<String> triggers) {
		return trigger(line, "", triggers);
	}

	/**
	 * As above, knowing {@code sender}: who {@link ChatText#sender} thinks wrote the line ("" for a server line). Many
	 * servers send player chat unsigned, so "[MVP+] Bob: won the game" must not count. A line that looks like chat only
	 * counts when it starts with the trigger, as the server's own "Winner: Steve" does.
	 */
	public static @Nullable String trigger(String line, @Nullable String sender, List<String> triggers) {
		String lower = line.toLowerCase(Locale.ROOT);
		for (String t : triggers) {
			String w = t.trim().toLowerCase(Locale.ROOT);
			if (!w.isEmpty() && (sender == null || sender.isEmpty() ? lower.contains(w) : lower.startsWith(w))) {
				return t.trim();
			}
		}
		return null;
	}

	/** True if a message may go out now in {@code game} (any object that changes per world joined). */
	public boolean allow(long now, Object game, long cooldownMillis) {
		return game != sentIn && now - lastSent >= cooldownMillis;
	}

	public void sent(long now, Object game) {
		lastSent = now;
		sentIn = game;
	}

	/** True if the server you are on is in {@code servers} (empty means every server). Matches part of the address. */
	public static boolean serverAllowed(@Nullable String address, List<String> servers) {
		if (servers.stream().allMatch(String::isBlank)) {
			return true;
		}
		if (address == null) {
			return false;
		}
		String a = address.toLowerCase(Locale.ROOT);
		return servers.stream().anyMatch(s -> !s.isBlank() && a.contains(s.trim().toLowerCase(Locale.ROOT)));
	}
}
