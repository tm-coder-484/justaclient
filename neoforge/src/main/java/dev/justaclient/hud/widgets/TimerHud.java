package dev.justaclient.hud.widgets;

import dev.justaclient.common.Durations;
import dev.justaclient.common.Stopwatch;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** One stopwatch for speedruns, farms and brewing: a start/pause key and a reset key. */
public final class TimerHud extends HudFeature {
	private final KeySetting startKey = key("start_key", "Start / pause key", GLFW.GLFW_KEY_UNKNOWN);
	private final KeySetting resetKey = key("reset_key", "Reset key", GLFW.GLFW_KEY_UNKNOWN);
	private final BoolSetting autoStart = bool("auto_start", "Start when you join a world", false);
	private final BoolSetting tenths = bool("tenths", "Show tenths of a second", true);
	private final Stopwatch watch = new Stopwatch();
	private boolean startWasDown;
	private boolean resetWasDown;
	private String shown = "0:00";

	public TimerHud() {
		super("timer", "Timer", "One stopwatch with start, pause and reset keys", Icons.TIMER, false, 0.5f, 0.2f);
	}

	/** Called when a world is joined. */
	public void onJoin() {
		long now = System.currentTimeMillis();
		if (isEnabled() && autoStart.on()) {
			watch.reset(now);
			watch.start(now);
		}
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean inGame = mc.screen == null;
		boolean start = inGame && startKey.isDown();
		boolean reset = inGame && resetKey.isDown();
		long now = System.currentTimeMillis();
		if (start && !startWasDown) {
			watch.toggle(now);
			Notifications.push(watch.running() ? Icons.PLAY : Icons.PAUSE, "Timer", watch.running() ? "started" : "paused at " + shown);
		}
		if (reset && !resetWasDown) {
			watch.reset(now);
			Notifications.push(Icons.ROTATE_CCW, "Timer", "reset");
		}
		startWasDown = start;
		resetWasDown = reset;
	}

	@Override
	public void layout(boolean preview) {
		long ms = preview ? 754_300L : watch.elapsedMillis(System.currentTimeMillis());
		shown = Durations.clock(ms) + (tenths.on() ? "." + ms / 100 % 10 : "");
		width = PAD * 2 + 10f + 5f + JacText.width(shown, TEXT + 2f, JacText.Weight.SEMIBOLD);
		height = PAD * 2 + JacText.lineHeight(TEXT + 2f);
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		boolean running = preview || watch.running();
		JacText.icon(g, running ? Icons.TIMER : Icons.PAUSE, PAD, PAD + (JacText.lineHeight(TEXT + 2f) - 8f) / 2f, 8f,
			running ? JacConfig.accent() : Theme.TEXT_DIM);
		text(g, shown, PAD + 15f, PAD, running ? Theme.TEXT : Theme.TEXT_DIM, TEXT + 2f, JacText.Weight.SEMIBOLD);
	}
}
