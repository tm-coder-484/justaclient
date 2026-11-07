package dev.justaclient.chat;

import dev.justaclient.common.chat.ChatText;
import dev.justaclient.feature.impl.AutoGgFeature;
import dev.justaclient.feature.impl.ChatHighlightsFeature;
import dev.justaclient.feature.impl.ChatHistoryFeature;
import dev.justaclient.feature.impl.ChatScrollMemoryFeature;
import dev.justaclient.feature.impl.ChatTimestampsFeature;
import dev.justaclient.feature.impl.SavedMessagesFeature;
import dev.justaclient.mixin.ChatComponentAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * The one hook on chat lines shown in the chat box (system 9): every line is logged for the chat history and its
 * tabs, checked for mentions and Auto GG triggers, and decorated (highlights, timestamp) before the game shows it.
 */
public final class ChatHook {
	private static final int MAX = 1000;
	private static final Deque<ChatText.Entry> LOG = new ArrayDeque<>();
	private static int savedScroll;
	/** The last signed player message the game is about to show: its sender and text, and when it came. */
	private static String pendingSender = "";
	private static String pendingText = "";
	private static long pendingAt;

	private ChatHook() {
	}

	public static void init() {
		// Player chat comes with its sender, so those lines are told apart from server lines exactly.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientChatReceivedEvent.Player event) -> {
            var mc=Minecraft.getInstance();
            var sender=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(event.getSender());
            pendingSender=sender==null?"":sender.getProfile().getName();
            pendingText=event.getMessage().getString();
            pendingAt=System.currentTimeMillis();
        });
	}

	/** True if {@code text} is the player message that just arrived through the chat channel. */
	private static String playerSender(String text) {
		if (pendingText.isEmpty() || System.currentTimeMillis() - pendingAt > 2000) {
			return "";
		}
		boolean same = text.contains(pendingText) || pendingText.contains(text);
		String sender = same ? pendingSender : "";
		pendingText = "";
		return sender.isEmpty() && same ? "?" : sender;
	}

	/** Called with each line before the chat box adds it; returns what to show instead. */
	public static Component incoming(Component line) {
		try {
			String text = line.getString();
			if (text.isBlank()) {
				return line;
			}
			ChatHighlightsFeature highlights = ChatHighlightsFeature.get();
			String self = selfName();
			boolean mention = highlights.isEnabled() && ChatText.mentions(text, self, highlights.keywords());
			String player = playerSender(text);
			ChatText.Kind kind = ChatText.kind(text);
			if (!player.isEmpty() && kind == ChatText.Kind.SYSTEM) {
				kind = ChatText.Kind.PLAYER;
			}
			String sender = player.isEmpty() || player.equals("?") ? ChatText.sender(text) : player;
			ChatText.Entry e = new ChatText.Entry(System.currentTimeMillis(), text, sender, kind, mention, !player.isEmpty());
			synchronized (LOG) {
				LOG.addLast(e);
				while (LOG.size() > MAX) {
					LOG.removeFirst();
				}
			}
			ChatHistoryFeature.get().onLine(e);
			if (mention) {
				highlights.onMention(e);
			}
			AutoGgFeature.get().onLine(e);
			Component out = line;
			if (highlights.isEnabled()) {
				List<String> words = new ArrayList<>(highlights.keywords());
				if (highlights.ownName() && !self.isEmpty() && !sender.equalsIgnoreCase(self) && !ChatText.aboutSelf(text, self)) {
					words.add(self);
				}
				out = highlight(out, words, highlights.color(), highlights.bold());
			}
			ChatTimestampsFeature timestamps = ChatTimestampsFeature.get();
			if (timestamps.isEnabled()) {
				out = Component.empty().append(Component.literal(ChatText.time(e.time(), timestamps.style()) + " ")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(timestamps.color() & 0xFFFFFF)))).append(out);
			}
			return out;
		} catch (RuntimeException e) {
			// A chat line must never be lost to a client bug.
			dev.justaclient.Justaclient.LOGGER.warn("Chat hook failed", e);
			return line;
		}
	}

	public static String selfName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? "" : mc.player.getGameProfile().getName();
	}

	/** Every logged line, oldest first. */
	public static List<ChatText.Entry> log() {
		synchronized (LOG) {
			return new ArrayList<>(LOG);
		}
	}

	/** Recolours {@code words} (whole words, any case) inside a line, keeping every other style and click action. */
	static Component highlight(Component line, List<String> words, int color, boolean bold) {
		String full = line.getString();
		List<int[]> spans = ChatText.spans(full, words);
		if (spans.isEmpty()) {
			return line;
		}
		MutableComponent out = Component.empty();
		int[] pos = {0};
		line.visit((style, piece) -> {
			int start = pos[0];
			int end = start + piece.length();
			int i = start;
			while (i < end) {
				int[] cover = null;
				int next = end;
				for (int[] s : spans) {
					if (s[0] <= i && i < s[1]) {
						cover = s;
						break;
					}
					if (s[0] > i) {
						next = Math.min(next, s[0]);
					}
				}
				if (cover != null) {
					int to = Math.min(end, cover[1]);
					Style marked = style.withColor(TextColor.fromRgb(color & 0xFFFFFF));
					out.append(Component.literal(full.substring(i, to)).withStyle(bold ? marked.withBold(true) : marked));
					i = to;
				} else {
					out.append(Component.literal(full.substring(i, next)).withStyle(style));
					i = next;
				}
			}
			pos[0] = end;
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** The chat box is about to jump back to the newest line (it does so when the chat closes). */
	public static void beforeScrollReset(ChatComponent chat) {
		Minecraft mc = Minecraft.getInstance();
		if (ChatScrollMemoryFeature.get().isEnabled() && ChatScreens.isChatOpen(mc)) {
			savedScroll = ((ChatComponentAccessor) chat).jac$scroll();
		}
	}

	/** The chat opened: go back to where you were reading. */
	public static void restoreScroll(ChatComponent chat) {
		if (ChatScrollMemoryFeature.get().isEnabled() && savedScroll > 0) {
			chat.scrollChat(savedScroll);
		}
		savedScroll = 0;
	}

	/** Saved messages: Ctrl+S in chat saves what you typed, Alt+1 to 9 puts saved message N in the box. */
	public static boolean chatKey(String current, int key, boolean ctrl, boolean alt, java.util.function.Consumer<String> setInput) {
		SavedMessagesFeature saved = SavedMessagesFeature.get();
		if (!saved.isEnabled()) {
			return false;
		}
		if (ctrl && key == com.mojang.blaze3d.platform.InputConstants.KEY_S) {
			saved.save(current);
			return true;
		}
		if (alt && key >= com.mojang.blaze3d.platform.InputConstants.KEY_1 && key <= com.mojang.blaze3d.platform.InputConstants.KEY_9) {
			String text = saved.get(key - com.mojang.blaze3d.platform.InputConstants.KEY_1 + 1);
			if (text != null) {
				setInput.accept(text);
			}
			return true;
		}
		return false;
	}
}
