package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Icons;

/** Reopening the chat goes back to where you were reading instead of the newest line. */
public final class ChatScrollMemoryFeature extends Feature {
	public ChatScrollMemoryFeature() {
		super("chat_scroll_memory", "Chat scroll memory", "Reopening chat goes back to where you were reading", Category.CHAT, Icons.HISTORY, true);
	}

	public static ChatScrollMemoryFeature get() {
		return Features.get(ChatScrollMemoryFeature.class);
	}
}
