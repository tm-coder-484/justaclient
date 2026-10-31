package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.mixin.BossHealthOverlayAccessor;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;

/** Boss bars as glass panels with smooth bars in each boss's colour. Same bars, names and progress as vanilla. */
public final class BossBarFeature extends Feature {
	private static final float BAR_W = 182f;
	private final BoolSetting panel = bool("panel", "Panel behind each bar", true);
	private final BoolSetting accent = bool("accent", "Use accent colour", false);
	private final Map<UUID, Spring> bars = new HashMap<>();

	public BossBarFeature() {
		super("boss_bar", "Boss bars", "Restyled boss bars with smooth progress", Category.HUD, Icons.SKULL, true);
	}

	/** Returns false to let vanilla draw. */
	public boolean render(GuiGraphicsExtractor g) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).justaclient$events();
		bars.keySet().retainAll(events.keySet());
		if (events.isEmpty()) {
			return true;
		}
		g.nextStratum();
		float y = 4f;
		float x = (g.guiWidth() - BAR_W) / 2f;
		for (Map.Entry<UUID, LerpingBossEvent> entry : events.entrySet()) {
			LerpingBossEvent event = entry.getValue();
			Spring spring = bars.computeIfAbsent(entry.getKey(), k -> Spring.ui(event.getProgress()));
			spring.setTarget(event.getProgress());
			float progress = Math.max(0f, Math.min(1f, spring.update()));
			float h = 19f;
			if (panel.on()) {
				Draw.hudPanel(g, x - 5f, y, BAR_W + 10f, h);
			}
			g.centeredText(mc.font, event.getName(), g.guiWidth() / 2, (int) y + 3, 0xFFFFFFFF);
			float by = y + 13f;
			Draw.rect(g, x, by, BAR_W, 3f, 1.5f, Theme.CONTROL);
			int c0 = accent.on() ? JacConfig.accentAt(0f) : color(event.getColor());
			int c1 = accent.on() ? JacConfig.accentAt(1f) : Colors.lerp(c0, 0xFFFFFFFF, 0.25f);
			Draw.horizontalGradient(g, x, by, BAR_W * progress, 3f, 1.5f, c0, c1);
			y += h + 4f;
			if (y >= g.guiHeight() / 3f) {
				break;
			}
		}
		return true;
	}

	private static int color(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> 0xFFEC4899;
			case BLUE -> 0xFF3B82F6;
			case RED -> 0xFFEF4444;
			case GREEN -> 0xFF22C55E;
			case YELLOW -> 0xFFEAB308;
			case PURPLE -> 0xFFA855F7;
			case WHITE -> 0xFFE5E7EB;
		};
	}
}
