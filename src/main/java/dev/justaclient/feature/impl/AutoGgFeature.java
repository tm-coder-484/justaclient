package dev.justaclient.feature.impl;

import dev.justaclient.common.chat.ChatText;
import dev.justaclient.common.chat.GgGate;
import dev.justaclient.common.chat.GgSend;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Auto GG: when a server line says a game ended (your triggers), sends one of your messages once. At most one message
 * per game and never twice inside the cooldown; only on servers in your list (or any server if it is empty), never in
 * singleplayer, and never on player chat the game delivers as chat, or whispers. Off by default.
 */
public final class AutoGgFeature extends Feature {
	private final ListSetting messages = add(new ListSetting("messages", "Messages (one is picked)", List.of("gg"), 10, 100, "gg"));
	private final ListSetting triggers = add(new ListSetting("triggers", "Game end lines", List.of("won the game", "Winner:", "1st Killer",
		"VICTORY!", "GAME OVER!", "has won the"), 30, 64, "Text the server prints at the end"));
	private final ListSetting servers = add(new ListSetting("servers", "Only on these servers", List.of(), 30, 64, "Address, empty means any"));
	private final NumberSetting delay = number("delay", "Delay", 1.5, 0, 5, 0.5, NumberSetting.decimal(" s"));
	private final NumberSetting cooldown = number("cooldown", "Cooldown", 60, 10, 600, 5, NumberSetting.unit(" s"));
	private final GgGate gate = new GgGate();
	private final GgSend pending = new GgSend();

	public AutoGgFeature() {
		super("auto_gg", "Auto GG", "Says gg once when a game ends", Category.CHAT, Icons.TROPHY, false);
	}

	public static AutoGgFeature get() {
		return Features.get(AutoGgFeature.class);
	}

	public void onLine(ChatText.Entry e) {
		Minecraft mc = Minecraft.getInstance();
		// Server lines only: player chat (and whispers) never trigger it, even if someone types "won the game".
		if (!isEnabled() || e.signed() || e.kind() == ChatText.Kind.PRIVATE || mc.level == null || mc.getConnection() == null || mc.isLocalServer()
			|| pending.pending()) {
			return;
		}
		if (!allowedHere(mc)) {
			return;
		}
		if (GgGate.trigger(e.text(), triggers.get()) == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!gate.allow(now, mc.level, (long) (cooldown.get() * 1000))) {
			return;
		}
		List<String> pool = messages.get().stream().filter(s -> !s.isBlank()).toList();
		if (pool.isEmpty()) {
			return;
		}
		gate.sent(now, mc.level);
		String text = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).trim();
		pending.schedule(mc.getConnection(), mc.level, text, (int) Math.round(delay.get() * 20));
	}

	/** Never in singleplayer, and only on a server in the list (any server if it is empty). */
	private boolean allowedHere(Minecraft mc) {
		if (mc.isLocalServer()) {
			return false;
		}
		ServerData server = mc.getCurrentServer();
		return GgGate.serverAllowed(server == null ? null : server.ip, servers.get());
	}

	@Override
	protected void onDisable() {
		pending.cancel();
	}

	@Override
	public void tick() {
		if (!pending.pending()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// Bound to the connection and world it was triggered in, and the server is checked again before it goes out.
		String text = pending.tick(mc.getConnection(), mc.level, isEnabled() && allowedHere(mc));
		if (text != null && mc.getConnection() != null) {
			// A chat line, never a command: Auto GG only says what you wrote.
			mc.getConnection().sendChat(text.startsWith("/") ? text.substring(1) : text);
			Notifications.push(Icons.TROPHY, "Auto GG", text);
		}
	}
}
