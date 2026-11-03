package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.mixin.DisconnectedScreenAccessor;
import dev.justaclient.render.Icons;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * Adds a Reconnect button to the disconnect screen that counts down and rejoins the last server by itself. Press it to
 * rejoin straight away, or leave the screen to cancel.
 * <p>
 * When a server kicks you because your last session is still online (common right after a crash or a quick server
 * switch), it retries a few times on a short countdown instead, the way Tom's server-switcher mod does.
 */
public final class AutoReconnectFeature extends Feature {
	private static final int MAX_ATTEMPTS = 5;
	private static final long RETRY_WINDOW_MILLIS = 120_000L;
	private static final Pattern ALREADY_ONLINE = Pattern.compile(
		"already (connected|online|logged|playing|on this|in th|joined)|another location|duplicate.?login|is already (online|playing|in)");
	private final BoolSetting always = bool("always", "Rejoin after any disconnect", true);
	private final NumberSetting delay = number("delay", "Wait before rejoining", 5, 1, 60, 1, NumberSetting.unit(" s")).visibleWhen(always::on);
	private final BoolSetting alreadyOnline = bool("already_online", "Retry already-online", true);
	private static @Nullable ServerData lastServer;
	private static int attempt;
	private static long firstAttempt;

	public AutoReconnectFeature() {
		super("auto_reconnect", "Auto reconnect", "Rejoin the last server after a disconnect, with a countdown", Category.UTILITY, Icons.ROTATE_CCW, false);
	}

	/**
	 * Called whenever a connection to a server starts, from the server list, direct connect or a retry. The retry target
	 * is the server being tried, not the last one that worked, so a kick from B never sends you back to A.
	 */
	public static void connecting(@Nullable ServerData data) {
		if (data == null) {
			return;
		}
		if (lastServer == null || !lastServer.ip.equalsIgnoreCase(data.ip)) {
			attempt = 0;
		}
		lastServer = data;
	}

	/** The address a retry would go to, or null. */
	/** The retry state, so a check can put it back exactly as it was. */
	public record Target(@Nullable ServerData server, int attempt) {
	}

	public static Target saveTarget() {
		return new Target(lastServer, attempt);
	}

	public static void restoreTarget(Target target) {
		lastServer = target.server();
		attempt = target.attempt();
	}

	public static @Nullable String retryTarget() {
		return lastServer == null ? null : lastServer.ip;
	}

	/** Forgets the retry target, e.g. after the self test. */
	public static void clearTarget() {
		lastServer = null;
		attempt = 0;
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData data = client.getCurrentServer();
			if (data != null && !client.isLocalServer()) {
				lastServer = data;
			}
			attempt = 0;
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			AutoReconnectFeature feature = Features.get(AutoReconnectFeature.class);
			ServerData server = lastServer;
			if (!feature.isEnabled() || !(screen instanceof DisconnectedScreen disconnected) || server == null) {
				return;
			}
			Component reason = ((DisconnectedScreenAccessor) disconnected).justaclient$details().reason();
			long now = System.currentTimeMillis();
			boolean retry = feature.alreadyOnline.on() && isAlreadyOnline(reason);
			if (retry && attempt == 0) {
				firstAttempt = now;
			}
			if (retry && (attempt >= MAX_ATTEMPTS || now - firstAttempt > RETRY_WINDOW_MILLIS)) {
				// Still online after several tries: stop and leave the screen as vanilla shows it.
				attempt = 0;
				retry = false;
				if (!feature.always.on()) {
					return;
				}
			}
			if (!retry && !feature.always.on()) {
				return;
			}
			int seconds = retry ? Math.min(1 + attempt, 5) : feature.delay.i();
			String prefix = retry ? "Still online, retrying " + (attempt + 1) + "/" + MAX_ATTEMPTS : "Reconnect";
			boolean counting = retry;
			long deadline = now + seconds * 1000L;
			Button button = Button.builder(label(prefix, deadline), b -> reconnect(client, server, counting))
				.bounds(width / 2 - 100, height - 30, 200, 20).build();
			Screens.getWidgets(screen).add(button);
			ScreenEvents.afterTick(screen).register(s -> {
				button.setMessage(label(prefix, deadline));
				if (System.currentTimeMillis() >= deadline && client.gui.screen() == s) {
					reconnect(client, server, counting);
				}
			});
		});
	}

	/** The vanilla duplicate-login kick, or a proxy's "you are already connected" style message. */
	static boolean isAlreadyOnline(Component reason) {
		if (reason.getContents() instanceof TranslatableContents t && t.getKey().equals("multiplayer.disconnect.duplicate_login")) {
			return true;
		}
		return ALREADY_ONLINE.matcher(reason.getString().toLowerCase(Locale.ROOT)).find();
	}

	private static Component label(String prefix, long deadline) {
		long left = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
		return Component.literal(prefix + " (" + left + ")");
	}

	private static void reconnect(Minecraft client, ServerData server, boolean retry) {
		attempt = retry ? attempt + 1 : 0;
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(server.ip), server, false, null);
	}
}
