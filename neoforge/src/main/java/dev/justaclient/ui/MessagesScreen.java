package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.relay.Conversations;
import dev.justaclient.common.relay.RelayProtocol;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.FriendsFeature;
import dev.justaclient.feature.impl.MessagingFeature;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Messages: your friends on the left (online first, with unread counts and who has not added you back yet), the open
 * conversation on the right with the friend's profile on top, and a box to write in. Enter sends.
 */
public final class MessagesScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float LIST_W = 180f;
	private static final float ROW = 26f;
	private static final float TEXT = Theme.LABEL;
	private static final int GREEN = 0xFF4ADE80;
	private static final int AMBER = 0xFFFBBF24;

	private final InputBox add = new InputBox("", 16, "Add a friend by name", Icons.USER_PLUS);
	private final InputBox input = new InputBox("", RelayProtocol.MAX_TEXT, "Write a message, Enter to send", Icons.SEND);
	private final Scroller listScroll = new Scroller();
	private final Scroller chatScroll = new Scroller();
	private @Nullable String friend;
	private boolean stick = true;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float lx;
	private float top;
	private float bottom;
	private float cx;
	private float cw;
	private float chatTop;
	private float chatBottom;

	public MessagesScreen(@Nullable Screen parent, @Nullable String friend) {
		super("Messages", parent);
		List<String> friends = friends();
		this.friend = friend != null ? friend : friends.isEmpty() ? null : friends.getFirst();
		open(this.friend);
	}

	private void open(@Nullable String name) {
		MessagingFeature m = MessagingFeature.get();
		friend = name;
		m.viewing = name;
		if (name != null) {
			m.conversations().read(name);
		}
		stick = true;
		input.focused = name != null;
	}

	/** Friends: online first, then the newest conversation, then by name. */
	private static List<String> friends() {
		MessagingFeature m = MessagingFeature.get();
		List<String> out = new ArrayList<>(FriendsFeature.get().list());
		out.sort(Comparator.<String, Boolean>comparing(n -> {
			RelayProtocol.Presence p = m.presence(n);
			return p == null || !p.online();
		}).thenComparing(n -> -m.conversations().lastAt(n)).thenComparing(String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private void layout() {
		pw = Math.min(700f, width - 24f);
		ph = Math.min(420f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		lx = px + PAD;
		top = py + 58;
		bottom = py + ph - PAD;
		add.place(lx, py + 36, LIST_W);
		cx = lx + LIST_W + 14;
		cw = px + pw - PAD - cx;
		chatTop = top + 46;
		chatBottom = bottom - 22;
		input.place(cx, bottom - 16, cw);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		MessagingFeature m = MessagingFeature.get();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.MESSAGE_CIRCLE, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Messages", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String line = m.connectionLine();
		boolean online = m.isEnabled() && m.relay().state() == dev.justaclient.feedback.RelayClient.State.ONLINE;
		JacText.draw(g, line, px + PAD + 15, py + PAD + 12, Colors.fade(online ? GREEN : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (!m.isEnabled()) {
			String off = "Turn Messages on";
			float w = chipW(off);
			boolean hot = offHit(mx, my, w);
			Draw.rect(g, px + pw - PAD - 20 - w, py + 11, w, 15, 7.5f, Colors.fade(Colors.withAlpha(AMBER, hot ? 0x60 : 0x38), a));
			JacText.draw(g, off, px + pw - PAD - 13 - w, py + 15, Colors.fade(AMBER, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		}
		add.draw(g, mx, my, a);

		// Friends
		List<String> list = friends();
		listScroll.setMax(list.size() * ROW - (bottom - top));
		float s = listScroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) lx, (int) top, (int) (lx + LIST_W), (int) bottom);
		long now = System.currentTimeMillis();
		for (int i = 0; i < list.size(); i++) {
			float y = top + i * ROW - s;
			if (y + ROW < top || y > bottom) {
				continue;
			}
			String name = list.get(i);
			RelayProtocol.Presence p = m.presence(name);
			boolean on = name.equalsIgnoreCase(friend);
			boolean hot = mx >= lx && mx < lx + LIST_W && my >= y && my < y + ROW - 2 && my >= top && my < bottom;
			if (on || hot) {
				Draw.rect(g, lx, y, LIST_W, ROW - 2, 5f, Colors.fade(on ? Colors.withAlpha(accent, 0x40) : Theme.CONTROL_HOVER, a));
			}
			int dot = p == null ? 0x40FFFFFF : p.pending() ? AMBER : p.online() ? GREEN : 0x60FFFFFF;
			Draw.circle(g, lx + 9, y + 12, 3.5f, Colors.fade(dot, a));
			int unread = m.conversations().unread(name);
			float right = unread > 0 ? 24 : 6;
			JacText.draw(g, fit(name, TEXT, JacText.Weight.SEMIBOLD, LIST_W - 24 - right), lx + 18, y + 4, Colors.fade(Theme.TEXT, a), TEXT,
				JacText.Weight.SEMIBOLD);
			String sub = p == null ? (online ? "Not on the relay yet" : "") : p.pending() ? "Has not added you back"
				: p.online() ? (p.status().isEmpty() ? "Online" : p.status()) : p.lastSeen() > 0 ? "Seen " + RelayProtocol.ago(p.lastSeen(), now) : "Offline";
			JacText.draw(g, fit(sub, Theme.SMALL, JacText.Weight.REGULAR, LIST_W - 24 - right), lx + 18, y + 14, Colors.fade(Theme.TEXT_DIM, a),
				Theme.SMALL, JacText.Weight.REGULAR);
			if (unread > 0) {
				String n = unread > 99 ? "99+" : String.valueOf(unread);
				float w = Math.max(14, JacText.width(n, Theme.SMALL, JacText.Weight.SEMIBOLD) + 8);
				Draw.rect(g, lx + LIST_W - w - 5, y + 6, w, 12, 6f, Colors.fade(accent, a));
				JacText.draw(g, n, lx + LIST_W - w - 5 + (w - JacText.width(n, Theme.SMALL, JacText.Weight.SEMIBOLD)) / 2f, y + 9,
					Colors.fade(0xFFFFFFFF, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}
		g.disableScissor();
		if (list.isEmpty()) {
			List<String> help = wrap("No friends yet. Add one above, or middle-click a player. They need Justaclient and to add you back.",
				Theme.SMALL, JacText.Weight.REGULAR, LIST_W - 4);
			for (int i = 0; i < help.size(); i++) {
				JacText.draw(g, help.get(i), lx + 2, top + 4 + i * 10, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			}
		}
		Draw.rect(g, cx - 7, top, 0.75f, bottom - top, 0, Colors.fade(Theme.DIVIDER, a));

		if (friend == null) {
			JacText.draw(g, "Pick a friend to message", cx, top + 4, Colors.fade(Theme.TEXT_DIM, a), TEXT, JacText.Weight.REGULAR);
			return;
		}
		drawProfile(g, m, a, now);
		drawChat(g, m, a, now);
		input.draw(g, mx, my, a);
	}

	private void drawProfile(GuiGraphics g, MessagingFeature m, float a, long now) {
		RelayProtocol.Presence p = m.presence(friend);
		JacText.draw(g, friend, cx, top, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String state;
		int color = Theme.TEXT_DIM;
		if (p == null) {
			state = "Not seen on the relay";
		} else if (p.pending()) {
			state = "Waiting for them to add you as a friend";
			color = AMBER;
		} else if (p.online()) {
			state = "Online" + (p.server().isEmpty() ? "" : " on " + p.server());
			color = GREEN;
		} else {
			state = p.lastSeen() > 0 ? "Last seen " + RelayProtocol.ago(p.lastSeen(), now) : "Offline";
		}
		float nameW = JacText.width(friend, Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, state, cx + nameW + 8, top + 3, Colors.fade(color, a), Theme.SMALL, JacText.Weight.REGULAR);
		String about = p == null || p.pending() ? "" : (p.status().isEmpty() ? "" : p.status()) + (p.bio().isEmpty() ? "" : (p.status().isEmpty()
			? "" : "  ·  ") + p.bio());
		if (!about.isEmpty()) {
			List<String> lines = wrap(about, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, cw);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				JacText.draw(g, lines.get(i), cx, top + 14 + i * 10, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			}
		}
		Draw.rect(g, cx, chatTop - 6, cw, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
	}

	private void drawChat(GuiGraphics g, MessagingFeature m, float a, long now) {
		List<Conversations.Message> msgs = m.conversations().with(friend);
		float maxW = cw * 0.72f;
		List<float[]> heights = new ArrayList<>();
		float total = 0;
		List<List<String>> wrapped = new ArrayList<>();
		for (Conversations.Message msg : msgs) {
			List<String> lines = wrap(msg.text, TEXT, JacText.Weight.REGULAR, maxW - 14);
			wrapped.add(lines);
			boolean note = msg.mine && !"delivered".equals(msg.state);
			float h = lines.size() * 10 + 10 + (note ? 10 : 0);
			heights.add(new float[] {h});
			total += h + 5;
		}
		float view = chatBottom - chatTop;
		chatScroll.setMax(total - view);
		if (stick) {
			chatScroll.scrollTo(Math.max(0, total - view));
		}
		float s = chatScroll.get();
		int accent = JacConfig.accent();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) cx, (int) chatTop, (int) (cx + cw), (int) chatBottom);
		float y = chatTop - s + Math.max(0, view - total);
		for (int i = 0; i < msgs.size(); i++) {
			Conversations.Message msg = msgs.get(i);
			List<String> lines = wrapped.get(i);
			float h = heights.get(i)[0];
			if (y + h >= chatTop && y <= chatBottom) {
				float w = 14;
				for (String l : lines) {
					w = Math.max(w, JacText.width(l, TEXT, JacText.Weight.REGULAR) + 14);
				}
				String time = dev.justaclient.common.chat.ChatText.time(msg.at, dev.justaclient.common.chat.ChatText.TimeStyle.SHORT);
				w = Math.max(w, JacText.width(time, Theme.SMALL, JacText.Weight.REGULAR) + 14);
				float bx = msg.mine ? cx + cw - w : cx;
				float bh = lines.size() * 10 + 10;
				Draw.rect(g, bx, y, w, bh, 7f, Colors.fade(msg.mine ? Colors.withAlpha(accent, 0xB0) : Theme.CONTROL, a));
				for (int k = 0; k < lines.size(); k++) {
					JacText.draw(g, lines.get(k), bx + 7, y + 5 + k * 10, Colors.fade(Theme.TEXT, a), TEXT, JacText.Weight.REGULAR);
				}
				boolean note = msg.mine && !"delivered".equals(msg.state);
				String under = note ? RelayProtocol.describe(msg.state) + "  ·  " + time : time;
				float uw = JacText.width(under, Theme.SMALL, JacText.Weight.REGULAR);
				if (note) {
					int c = "queued".equals(msg.state) || "sending".equals(msg.state) ? Theme.TEXT_DIM : 0xFFF87171;
					JacText.draw(g, under, msg.mine ? cx + cw - uw - 2 : cx + 2, y + bh + 1, Colors.fade(c, a), Theme.SMALL, JacText.Weight.REGULAR);
				}
			}
			y += h + 5;
		}
		g.disableScissor();
		if (msgs.isEmpty()) {
			JacText.draw(g, "No messages yet. Say hi!", cx, chatTop + 4, Colors.fade(Theme.TEXT_DIM, a), TEXT, JacText.Weight.REGULAR);
		}
	}

	private static float chipW(String s) {
		return JacText.width(s, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
	}

	private boolean offHit(double mx, double my, float w) {
		float ox = px + pw - PAD - 20 - w;
		return mx >= ox && mx < ox + w && my >= py + 11 && my < py + 26;
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		layout();
		if (button != 0) {
			return false;
		}
		add.click(mx, my);
		if (friend != null) {
			input.click(mx, my);
		}
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		MessagingFeature m = MessagingFeature.get();
		if (!m.isEnabled() && offHit(mx, my, chipW("Turn Messages on"))) {
			m.setEnabled(true);
			Profiles.save();
			return true;
		}
		if (mx >= lx && mx < lx + LIST_W && my >= top && my < bottom) {
			List<String> list = friends();
			int i = (int) ((my - top + listScroll.get()) / ROW);
			if (i >= 0 && i < list.size()) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				open(list.get(i));
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx < cx - 7) {
			listScroll.scroll(amount, ROW * 2);
		} else {
			chatScroll.scroll(amount, 30);
			stick = chatScroll.target() >= chatScroll.max() - 1;
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		if (add.focused) {
			if (enter) {
				String name = add.get().trim();
				if (name.matches("[A-Za-z0-9_]{2,16}") && !FriendsFeature.get().isFriend(name)) {
					FriendsFeature.get().toggleFriend(name);
					open(name);
				}
				add.set("");
				return true;
			}
			return add.key(key, ctrl);
		}
		if (input.focused && friend != null) {
			if (enter) {
				if (!input.get().isBlank()) {
					MessagingFeature.get().send(friend, input.get());
					input.set("");
					stick = true;
				}
				return true;
			}
			return input.key(key, ctrl);
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (add.focused) {
			return add.typed(text);
		}
		if (friend != null) {
			input.focused = true;
			return input.typed(text);
		}
		return false;
	}

	@Override
	public void onClose() {
		MessagingFeature m = MessagingFeature.get();
		m.viewing = null;
		m.save();
		super.onClose();
	}
}
