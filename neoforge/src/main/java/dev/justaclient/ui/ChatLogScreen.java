package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.chat.ChatText;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.ChatHistoryFeature;
import dev.justaclient.feature.impl.ChatTimestampsFeature;
import dev.justaclient.feature.impl.SavedMessagesFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The chat history: this session's chat lines by tab, newest at the bottom, with a search that understands
 * {@code from:Name}, {@code has:link}, {@code is:mention}, {@code is:private} and {@code is:system}. Clicking a line copies
 * it in the format set in Chat history. The Saved tab lists your saved messages.
 */
public final class ChatLogScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 13f;
	private static final int GREEN = 0xFF4ADE80;

	private final InputBox search = new InputBox("", 64, "Search: words, from:Name, has:link, is:mention", Icons.SEARCH);
	private final Scroller scroll = new Scroller();
	private String tab;
	private boolean stickToBottom = true;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float gx;
	private float gw;
	private float top;
	private float bottom;
	private float chipY;
	private long copiedAt;
	private int copiedRow = -1;

	public ChatLogScreen(@Nullable Screen parent, String tab) {
		super("Chat history", parent);
		this.tab = tab;
		ChatHistoryFeature.get().seen(tab);
	}

	private void layout() {
		pw = Math.min(720f, width - 24f);
		ph = Math.min(420f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		gx = px + PAD;
		gw = pw - PAD * 2;
		search.place(px + pw - PAD - 220, py + 12, 200);
		chipY = py + 38;
		top = py + 60;
		bottom = py + ph - PAD;
	}

	private List<String> tabs() {
		List<String> out = new ArrayList<>(ChatHistoryFeature.get().tabNames());
		out.add(ChatHistoryFeature.SAVED);
		return out;
	}

	private List<ChatText.Entry> lines() {
		return ChatHistoryFeature.get().lines(tab, ChatText.Query.parse(search.get()));
	}

	private static String chipLabel(String t) {
		int n = ChatHistoryFeature.get().unread(t);
		if (t.equals(ChatHistoryFeature.SAVED)) {
			return t + " " + SavedMessagesFeature.get().all().size();
		}
		return n > 0 && !t.equals(ChatHistoryFeature.ALL) ? t + " " + n : t;
	}

	private static float chipWidth(String label) {
		return JacText.width(label, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.MESSAGES_SQUARE, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Chat history", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String sub = tab.equals(ChatHistoryFeature.SAVED) ? "Alt+1 to 9 in chat puts one in the box; Ctrl+S in chat saves one"
			: "This session's chat; click a line to copy it";
		JacText.draw(g, sub, px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (!tab.equals(ChatHistoryFeature.SAVED)) {
			search.draw(g, mx, my, a);
		}

		float cx = gx;
		for (String name : tabs()) {
			String label = chipLabel(name);
			float w = chipWidth(label);
			if (cx + w > gx + gw) {
				break;
			}
			boolean on = name.equals(tab);
			boolean hot = mx >= cx && mx < cx + w && my >= chipY && my < chipY + 15;
			Draw.rect(g, cx, chipY, w, 15, 7.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x50) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, label, cx + 7, chipY + 4, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}

		if (tab.equals(ChatHistoryFeature.SAVED)) {
			drawSaved(g, mx, my, a);
			return;
		}
		List<ChatText.Entry> list = lines();
		float content = list.size() * ROW;
		scroll.setMax(content - (bottom - top));
		if (stickToBottom) {
			scroll.scrollTo(Math.max(0, content - (bottom - top)));
		}
		float s = scroll.get();
		ChatText.TimeStyle style = ChatTimestampsFeature.get().style();
		float timeW = JacText.width(ChatText.time(System.currentTimeMillis(), style), Theme.SMALL + 0.5f, JacText.Weight.REGULAR) + 8;
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) gx, (int) top, (int) (gx + gw), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float y = top + i * ROW - s;
			if (y + ROW < top || y > bottom) {
				continue;
			}
			ChatText.Entry e = list.get(i);
			boolean hot = mx >= gx && mx < gx + gw && my >= y && my < y + ROW && my >= top && my < bottom;
			if (hot) {
				Draw.rect(g, gx, y, gw, ROW, 3f, Colors.fade(Theme.CONTROL_HOVER, a));
			} else if (e.mention()) {
				Draw.rect(g, gx, y, gw, ROW, 3f, Colors.fade(Colors.withAlpha(0xFFFBBF24, 0x26), a));
			}
			JacText.draw(g, ChatText.time(e.time(), style), gx + 4, y + 3, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f,
				JacText.Weight.REGULAR);
			int color = e.kind() == ChatText.Kind.PRIVATE ? 0xFFC4B5FD : e.kind() == ChatText.Kind.SYSTEM ? 0xFFB8BEC8 : Theme.TEXT;
			boolean copied = i == copiedRow && System.currentTimeMillis() - copiedAt < 1200;
			float right = copied ? 50 : 6;
			JacText.draw(g, fit(e.text(), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, gw - timeW - right), gx + timeW, y + 3, Colors.fade(color, a),
				Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			if (copied) {
				JacText.draw(g, "Copied", gx + gw - 40, y + 3, Colors.fade(GREEN, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			}
		}
		g.disableScissor();
		if (list.isEmpty()) {
			JacText.draw(g, search.get().isBlank() ? "Nothing in this tab yet" : "Nothing matches", gx + 2, top + 4, Colors.fade(Theme.TEXT_DIM, a),
				Theme.LABEL, JacText.Weight.REGULAR);
		}
	}

	private void drawSaved(GuiGraphics g, int mx, int my, float a) {
		List<String> saved = SavedMessagesFeature.get().all();
		if (saved.isEmpty()) {
			JacText.draw(g, "No saved messages. Type in chat and press Ctrl+S.", gx + 2, top + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL,
				JacText.Weight.REGULAR);
			return;
		}
		for (int i = 0; i < saved.size(); i++) {
			float y = top + i * (ROW + 4);
			boolean hot = mx >= gx && mx < gx + gw && my >= y && my < y + ROW + 2;
			Draw.rect(g, gx, y, gw, ROW + 2, 3f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, "Alt+" + (i + 1), gx + 6, y + 4, Colors.fade(JacConfig.accent(), a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			JacText.draw(g, fit(saved.get(i), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, gw - 80), gx + 40, y + 4, Colors.fade(Theme.TEXT, a),
				Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			JacText.icon(g, Icons.TRASH, gx + gw - 14, y + 3.5f, 7.5f, Colors.fade(hot && mx >= gx + gw - 20 ? 0xFFF87171 : Theme.TEXT_DIM, a));
		}
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (!tab.equals(ChatHistoryFeature.SAVED)) {
			search.click(mx, my);
		}
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		if (my >= chipY && my < chipY + 15) {
			float cx = gx;
			for (String name : tabs()) {
				float w = chipWidth(chipLabel(name));
				if (mx >= cx && mx < cx + w) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					tab = name;
					ChatHistoryFeature.get().seen(name);
					stickToBottom = true;
					return true;
				}
				cx += w + 4;
			}
			return true;
		}
		if (my < top || my >= bottom || mx < gx || mx >= gx + gw) {
			return false;
		}
		if (tab.equals(ChatHistoryFeature.SAVED)) {
			List<String> saved = SavedMessagesFeature.get().all();
			int i = (int) ((my - top) / (ROW + 4));
			if (i >= 0 && i < saved.size()) {
				String text = saved.get(i);
				if (mx >= gx + gw - 20) {
					SavedMessagesFeature.get().remove(text);
				} else {
					Minecraft.getInstance().keyboardHandler.setClipboard(text);
					Notifications.push(Icons.COPY, "Copied", "Saved message " + (i + 1));
				}
			}
			return true;
		}
		List<ChatText.Entry> list = lines();
		int i = (int) ((my - top + scroll.get()) / ROW);
		if (i >= 0 && i < list.size()) {
			ChatHistoryFeature history = ChatHistoryFeature.get();
			Minecraft.getInstance().keyboardHandler.setClipboard(ChatText.copy(list.get(i), history.copyFormat(), ChatTimestampsFeature.get().style()));
			copiedRow = i;
			copiedAt = System.currentTimeMillis();
		}
		return true;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, ROW * 3);
		stickToBottom = scroll.target() >= scroll.max() - 1;
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused && search.key(key, ctrl)) {
			stickToBottom = true;
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (tab.equals(ChatHistoryFeature.SAVED)) {
			return false;
		}
		if (!search.focused) {
			search.focused = true;
		}
		search.typed(text);
		stickToBottom = true;
		return true;
	}
}
