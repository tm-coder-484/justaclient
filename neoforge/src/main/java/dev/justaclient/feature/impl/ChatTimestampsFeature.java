package dev.justaclient.feature.impl;

import dev.justaclient.common.chat.ChatText;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.render.Icons;

/** The time each chat line arrived, in front of it. */
public final class ChatTimestampsFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		SHORT("12:34", ChatText.TimeStyle.SHORT), SECONDS("12:34:56", ChatText.TimeStyle.SECONDS), TWELVE("12:34 PM", ChatText.TimeStyle.TWELVE);

		private final String label;
		final ChatText.TimeStyle style;

		Style(String label, ChatText.TimeStyle style) {
			this.label = label;
			this.style = style;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Format", Style.SHORT);
	private final ColorSetting color = color("color", "Colour", "#8A8F98");

	public ChatTimestampsFeature() {
		super("chat_timestamps", "Chat timestamps", "The time each chat line arrived", Category.CHAT, Icons.CLOCK, false);
	}

	public static ChatTimestampsFeature get() {
		return Features.get(ChatTimestampsFeature.class);
	}

	public ChatText.TimeStyle style() {
		return style.get().style;
	}

	public int color() {
		return color.argb();
	}
}
