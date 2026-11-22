package dev.justaclient.feature.impl;

import dev.justaclient.common.chat.ChatText;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Mentions: lines that name you or one of your keywords get the words coloured, a soft sound, and a place in the
 * Mentions tab of the chat history.
 */
public final class ChatHighlightsFeature extends Feature {
	private final BoolSetting ownName = bool("own_name", "Your name", true);
	private final ListSetting keywords = add(new ListSetting("keywords", "Keywords", List.of(), 50, 32, "Add a word or phrase"));
	private final ColorSetting color = color("color", "Highlight colour", "#FBBF24");
	private final BoolSetting bold = bool("bold", "Bold", true);
	private final BoolSetting sound = bool("sound", "Sound on a mention", true);
	private final BoolSetting toast = bool("toast", "Notification on a mention", false);
	private long lastSound;

	public ChatHighlightsFeature() {
		super("chat_highlights", "Chat highlights", "Colours your name and keywords in chat, with a sound on mentions", Category.CHAT, Icons.AT_SIGN,
			true);
	}

	public static ChatHighlightsFeature get() {
		return Features.get(ChatHighlightsFeature.class);
	}

	public List<String> keywords() {
		return keywords.get();
	}

	public boolean ownName() {
		return ownName.on();
	}

	public int color() {
		return color.argb();
	}

	public boolean bold() {
		return bold.on();
	}

	public void onMention(ChatText.Entry e) {
		long now = System.currentTimeMillis();
		if (sound.on() && now - lastSound > 1000) {
			lastSound = now;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.6f));
		}
		if (toast.on()) {
			String who = e.sender().isEmpty() ? "Mention" : e.sender();
			String text = e.text().length() > 48 ? e.text().substring(0, 48) + "..." : e.text();
			Notifications.push(Icons.AT_SIGN, who, text);
		}
	}
}
