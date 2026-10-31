package dev.justaclient.hud.widgets;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

/** A small bar with the client name and a few live facts. Off by default; nothing is branded unless you want it. */
public final class WatermarkHud extends HudFeature {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	private final TextSetting title = add(new TextSetting("title", "Title", "justaclient", 20));
	private final BoolSetting fps = bool("fps", "FPS", true);
	private final BoolSetting ping = bool("ping", "Ping", true);
	private final BoolSetting time = bool("time", "Clock", true);
	private final BoolSetting server = bool("server", "Server address", false);
	private final List<String> parts = new ArrayList<>();
	private float titleW;

	public WatermarkHud() {
		super("watermark", "Watermark", "Client name with FPS, ping, time and server", Icons.STAR, false, 0f, 0.22f);
	}

	@Override
	public void layout(boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		parts.clear();
		if (fps.on()) {
			parts.add(mc.getFps() + " fps");
		}
		if (ping.on()) {
			parts.add(preview ? "32 ms" : ping(mc));
		}
		if (time.on()) {
			parts.add(LocalTime.now().format(CLOCK));
		}
		if (server.on()) {
			ServerData data = mc.getCurrentServer();
			parts.add(preview ? "play.example.net" : data != null ? data.ip : "Singleplayer");
		}
		String t = title.get().isBlank() ? "justaclient" : title.get();
		titleW = JacText.width(t, TEXT + 0.5f, JacText.Weight.SEMIBOLD);
		float w = PAD * 2 + 3f + 5f + titleW;
		for (String p : parts) {
			w += 9f + JacText.width(p, TEXT, JacText.Weight.REGULAR);
		}
		width = w;
		height = PAD * 2 + JacText.lineHeight(TEXT + 0.5f);
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		Draw.verticalGradient(g, PAD, PAD, 2f, height - PAD * 2, 1f, JacConfig.accentAt(0f), JacConfig.accentAt(1f));
		float x = PAD + 3f + 5f;
		String t = title.get().isBlank() ? "justaclient" : title.get();
		text(g, t, x, PAD, JacConfig.accentAt(0.3f), TEXT + 0.5f, JacText.Weight.SEMIBOLD);
		x += titleW;
		float y = PAD + 0.5f;
		for (String p : parts) {
			Draw.circle(g, x + 4.5f, PAD + JacText.lineHeight(TEXT + 0.5f) / 2f, 0.9f, Theme.TEXT_DIM);
			x += 9f;
			text(g, p, x, y, Theme.TEXT, TEXT, JacText.Weight.REGULAR);
			x += JacText.width(p, TEXT, JacText.Weight.REGULAR);
		}
	}

	private static String ping(Minecraft mc) {
		if (mc.hasSingleplayerServer() || mc.getConnection() == null || mc.player == null) {
			return "0 ms";
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? "- ms" : info.getLatency() + " ms";
	}
}
