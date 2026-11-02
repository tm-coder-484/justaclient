package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** The sidebar scoreboard on a glass panel, with the option to hide the red score numbers. Shows the same lines as vanilla. */
public final class ScoreboardFeature extends Feature {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private final BoolSetting numbers = bool("numbers", "Show score numbers", false);
	private final NumberSetting scale = number("scale", "Scale", 1, 0.6, 1.4, 0.05, NumberSetting.percent());

	private record Line(Component name, Component score, int scoreWidth) {
	}

	public ScoreboardFeature() {
		super("scoreboard", "Scoreboard", "Sidebar scoreboard on a glass panel", Category.HUD, Icons.LAYOUT_DASHBOARD, true);
	}

	/** Returns false to let vanilla draw. */
	public boolean render(GuiGraphics g) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return true;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null) {
				objective = scoreboard.getDisplayObjective(slot);
			}
		}
		if (objective == null) {
			objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		}
		if (objective == null) {
			return true;
		}
		Font font = mc.font;
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<Line> lines = scoreboard.listPlayerScores(objective).stream()
			.filter(e -> !e.isHidden())
			.sorted(ORDER)
			.limit(15)
			.map(e -> {
				Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(e.owner()), e.ownerName());
				Component score = e.formatValue(format);
				return new Line(name, score, numbers.on() ? font.width(score) : 0);
			})
			.toList();
		Component title = objective.getDisplayName();
		int w = font.width(title);
		for (Line l : lines) {
			w = Math.max(w, font.width(l.name) + (l.scoreWidth > 0 ? 8 + l.scoreWidth : 0));
		}
		float pad = 5f;
		float lineH = 9f;
		float pw = w + pad * 2;
		float ph = pad * 2 + 11f + lines.size() * lineH;
		float s = scale.f();
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() - 4f - pw * s, g.guiHeight() / 2f - ph * s / 2f);
		g.pose().scale(s, s);
		Draw.hudPanel(g, 0, 0, pw, ph);
		g.drawString(font, title, (int) (pad + (w - font.width(title)) / 2f), (int) pad, 0xFFFFFFFF, false);
		Draw.rect(g, pad, pad + 9.5f, w, 0.5f, 0f, 0x22FFFFFF);
		float y = pad + 12f;
		for (Line l : lines) {
			g.drawString(font, l.name, (int) pad, (int) y, 0xFFFFFFFF, false);
			if (l.scoreWidth > 0) {
				g.drawString(font, l.score, (int) (pad + w - l.scoreWidth), (int) y, 0xFFFFFFFF, false);
			}
			y += lineH;
		}
		g.pose().popMatrix();
		return true;
	}
}
