package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/**
 * Where the day is: a track from sunrise round to the next sunrise with the sun or moon on it, the day number, the
 * in-game time and how long until night (or morning) in real minutes. Only in dimensions with a day cycle.
 */
public final class DayCycleHud extends HudFeature {
	/** Ticks of day time where night begins and ends (monsters spawn from about here). */
	private static final int DUSK = 12_542;
	private static final int DAWN = 23_460;
	private static final float TRACK_W = 90f;
	private final BoolSetting dayNumber = bool("day_number", "Day number", true);
	private final BoolSetting countdown = bool("countdown", "Time until night or morning", true);
	private long dayTime;
	private boolean show;
	private String top = "";
	private String bottom = "";

	public DayCycleHud() {
		super("day_cycle", "Day cycle", "Sun position, day number and time until night", Icons.SUN, false, 0.5f, 0.12f);
	}

	@Override
	public void layout(boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		show = preview || level != null && !level.dimensionType().hasFixedTime();
		if (!show) {
			return;
		}
		long total = preview ? 3 * 24_000L + 9_000L : level.getDayTime();
		dayTime = Math.floorMod(total, 24_000L);
		long ticks = (dayTime + 6000L) % 24000L;
		String clock = String.format(Locale.ROOT, "%02d:%02d", ticks / 1000L, ticks % 1000L * 60L / 1000L);
		top = (dayNumber.on() ? "Day " + (total / 24_000L + 1) + " · " : "") + clock;
		boolean night = isNight(dayTime);
		long until = night ? Math.floorMod(DAWN - dayTime, 24_000L) : Math.floorMod(DUSK - dayTime, 24_000L);
		long seconds = until / 20L;
		bottom = countdown.on() ? (night ? "Morning in " : "Night in ") + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60) : "";
		float w = Math.max(TRACK_W, Math.max(JacText.width(top, TEXT, JacText.Weight.SEMIBOLD), JacText.width(bottom, TEXT, JacText.Weight.REGULAR)));
		width = PAD * 2 + w;
		height = PAD * 2 + 8f + 4f + JacText.lineHeight(TEXT) + (bottom.isEmpty() ? 0f : 1f + JacText.lineHeight(TEXT));
	}

	private static boolean isNight(long t) {
		return t >= DUSK && t < DAWN;
	}

	@Override
	public boolean hasContent() {
		return show;
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float tw = width - PAD * 2;
		float ty = PAD + 3f;
		// The track runs from sunrise (day time 23000, about 05:00) through noon and night to the next sunrise.
		float dayShare = (DUSK + 24_000f - DAWN) / 24_000f;
		Draw.rect(g, PAD, ty, tw, 2f, 1f, Theme.CONTROL);
		Draw.horizontalGradient(g, PAD, ty, tw * dayShare, 2f, 1f, 0xFFF5C542, 0xFFF59E42);
		Draw.horizontalGradient(g, PAD + tw * dayShare, ty, tw * (1f - dayShare), 2f, 1f, 0xFF4B5BB8, 0xFF7C6FE0);
		float pos = Math.floorMod(dayTime - DAWN, 24_000L) / 24_000f;
		float cx = PAD + tw * pos;
		boolean night = isNight(dayTime);
		int dot = night ? 0xFFDDE3FF : 0xFFFFD45C;
		Draw.circle(g, cx, ty + 1f, 3.5f, Colors.withAlpha(dot, 0x50));
		Draw.circle(g, cx, ty + 1f, 2.3f, dot);
		float y = PAD + 8f + 4f;
		text(g, top, PAD, y, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
		if (!bottom.isEmpty()) {
			text(g, bottom, PAD, y + JacText.lineHeight(TEXT) + 1f, night ? Colors.lerp(Theme.TEXT_DIM, JacConfig.accent(), 0.3f) : Theme.TEXT_DIM, TEXT,
				JacText.Weight.REGULAR);
		}
	}
}
