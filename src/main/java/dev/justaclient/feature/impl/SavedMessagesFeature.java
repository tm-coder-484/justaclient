package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Saved messages (drafts you keep): Ctrl+S in the chat box saves what you typed, Alt+1 to 9 puts saved message N back in
 * the box. Nothing is sent until you press Enter. The list is editable here and in the chat history's Saved tab.
 */
public final class SavedMessagesFeature extends Feature {
	public static final int MAX = 9;
	private final ListSetting messages = add(new ListSetting("messages", "Saved messages", List.of(), MAX, 256, "Ctrl+S in chat adds one"));

	public SavedMessagesFeature() {
		super("chat_saved", "Saved messages", "Ctrl+S in chat keeps a message, Alt+1 to 9 brings it back", Category.CHAT, Icons.STAR, true);
	}

	public static SavedMessagesFeature get() {
		return Features.get(SavedMessagesFeature.class);
	}

	public List<String> all() {
		return messages.get();
	}

	/** Saved message {@code n} (1-based), or null. */
	public @Nullable String get(int n) {
		List<String> list = messages.get();
		return n >= 1 && n <= list.size() ? list.get(n - 1) : null;
	}

	public void save(String text) {
		String t = text.strip();
		if (t.isEmpty()) {
			Notifications.push(Icons.STAR, "Saved messages", "Type something first");
			return;
		}
		if (messages.contains(t)) {
			Notifications.push(Icons.STAR, "Already saved", "Alt+" + (messages.get().indexOf(t) + 1));
			return;
		}
		if (messages.get().size() >= MAX) {
			Notifications.push(Icons.STAR, "Saved messages full", "Remove one in the chat history's Saved tab");
			return;
		}
		messages.add(t);
		dev.justaclient.profile.Profiles.save();
		Notifications.push(Icons.STAR, "Saved as Alt+" + messages.get().size(), t.length() > 40 ? t.substring(0, 40) + "..." : t);
	}

	public void remove(String text) {
		messages.remove(text);
		dev.justaclient.profile.Profiles.save();
	}
}
