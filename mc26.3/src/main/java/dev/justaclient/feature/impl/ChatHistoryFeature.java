package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.chat.ChatHook;
import dev.justaclient.common.chat.ChatText;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.ChatLogScreen;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Chat history: every line this session in a searchable screen ({@code /chatlog}) with tabs (All, Mentions, Private,
 * System and your own), search filters, click to copy in the format you pick. While the chat is open, chips above the
 * text box show each tab's new lines and open the history on that tab.
 */
public final class ChatHistoryFeature extends Feature {
	public enum Copy implements ChoiceSetting.Named {
		PLAIN("Text only", ChatText.CopyFormat.PLAIN), WITH_TIME("With the time", ChatText.CopyFormat.WITH_TIME), QUOTE("Quote with sender",
			ChatText.CopyFormat.QUOTE);

		private final String label;
		public final ChatText.CopyFormat format;

		Copy(String label, ChatText.CopyFormat format) {
			this.label = label;
			this.format = format;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public static final String ALL = "All";
	public static final String MENTIONS = "Mentions";
	public static final String PRIVATE = "Private";
	public static final String SYSTEM = "System";
	public static final String SAVED = "Saved";

	private final BoolSetting chips = bool("chips", "Tab chips above the chat box", true);
	private final ListSetting tabs = add(new ListSetting("tabs", "Your tabs", List.of(), 8, 96, "Trade: buy, sell, wts"));
	private final ChoiceSetting<Copy> copy = choice("copy", "Copy format", Copy.PLAIN);
	private final Map<String, Integer> unread = new HashMap<>();
	private float[][] chipBoxes = new float[0][];
	private List<String> chipNames = List.of();

	public ChatHistoryFeature() {
		super("chat_history", "Chat history", "Search this session's chat, with tabs, filters and copy", Category.CHAT, Icons.MESSAGES_SQUARE, true);
	}

	public static ChatHistoryFeature get() {
		return Features.get(ChatHistoryFeature.class);
	}

	public ChatText.CopyFormat copyFormat() {
		return copy.get().format;
	}

	/** Your own tabs, from entries written "Name: word, word". */
	public List<ChatText.Tab> customTabs() {
		List<ChatText.Tab> out = new ArrayList<>();
		for (String raw : tabs.get()) {
			ChatText.Tab t = ChatText.Tab.parse(raw);
			if (t != null && out.stream().noneMatch(o -> o.name().equalsIgnoreCase(t.name()))) {
				out.add(t);
			}
		}
		return out;
	}

	/** Every tab name in order: the built-in ones, then yours. */
	public List<String> tabNames() {
		List<String> out = new ArrayList<>(List.of(ALL, MENTIONS, PRIVATE, SYSTEM));
		for (ChatText.Tab t : customTabs()) {
			if (!out.contains(t.name())) {
				out.add(t.name());
			}
		}
		return out;
	}

	public boolean inTab(String tab, ChatText.Entry e) {
		return switch (tab) {
			case ALL -> true;
			case MENTIONS -> e.mention();
			case PRIVATE -> e.kind() == ChatText.Kind.PRIVATE;
			case SYSTEM -> e.kind() == ChatText.Kind.SYSTEM;
			default -> customTabs().stream().anyMatch(t -> t.name().equals(tab) && t.matches(e));
		};
	}

	public void onLine(ChatText.Entry e) {
		if (!isEnabled()) {
			return;
		}
		for (String tab : tabNames()) {
			if (!tab.equals(ALL) && inTab(tab, e)) {
				unread.merge(tab, 1, Integer::sum);
			}
		}
	}

	public int unread(String tab) {
		return unread.getOrDefault(tab, 0);
	}

	public void seen(String tab) {
		unread.remove(tab);
	}

	public static void open(@Nullable String tab) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(new ChatLogScreen(null, tab == null ? ALL : tab)));
	}

	/** The chips in the gap between the chat lines and the text box, right-aligned so they stay off the chat text. */
	public void drawChips(GuiGraphicsExtractor g, int width, int height, int mx, int my) {
		if (!isEnabled() || !chips.on()) {
			chipBoxes = new float[0][];
			return;
		}
		List<String> names = new ArrayList<>();
		for (String tab : tabNames()) {
			if (tab.equals(ALL) || unread(tab) > 0 || !tab.equals(MENTIONS) && !tab.equals(PRIVATE) && !tab.equals(SYSTEM)) {
				names.add(tab);
			}
		}
		float y = height - 29;
		float x = width - 4;
		float[][] boxes = new float[names.size()][];
		for (int i = names.size() - 1; i >= 0; i--) {
			String tab = names.get(i);
			int n = unread(tab);
			String label = tab.equals(ALL) ? "History" : n > 0 ? tab + " " + n : tab;
			float w = JacText.width(label, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + (tab.equals(ALL) ? 22 : 12);
			x -= w;
			boolean hot = mx >= x && mx < x + w && my >= y && my < y + 12;
			int fill = n > 0 ? Colors.withAlpha(JacConfig.accent(), hot ? 0xC0 : 0x90) : hot ? 0xE0303540 : 0xB0202328;
			Draw.rect(g, x, y, w, 12, 6f, fill);
			float tx = x + 6;
			if (tab.equals(ALL)) {
				JacText.icon(g, Icons.MESSAGES_SQUARE, x + 6, y + 2.5f, 7f, Theme.TEXT);
				tx += 10;
			}
			JacText.draw(g, label, tx, y + 3, Theme.TEXT, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			boxes[i] = new float[] {x, y, w, 12};
			x -= 3;
		}
		chipBoxes = boxes;
		chipNames = names;
	}

	public boolean clickChips(double mx, double my, int width, int height) {
		for (int i = 0; i < chipBoxes.length; i++) {
			float[] b = chipBoxes[i];
			if (b != null && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
				GuiSoundsFeature.menuClick();
				open(chipNames.get(i));
				return true;
			}
		}
		return false;
	}

	public List<ChatText.Entry> lines(String tab, ChatText.Query query) {
		List<ChatText.Entry> out = new ArrayList<>();
		for (ChatText.Entry e : ChatHook.log()) {
			if (inTab(tab, e) && query.matches(e)) {
				out.add(e);
			}
		}
		return out;
	}
}
