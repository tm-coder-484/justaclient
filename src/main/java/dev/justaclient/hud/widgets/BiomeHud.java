package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** A small card naming the biome you are in, tinted by its grass and water colours, with its climate below. */
public final class BiomeHud extends HudFeature {
	private final BoolSetting climate = bool("climate", "Climate line", true);
	private final BoolSetting dimension = bool("dimension", "Dimension", false);
	private String name = "";
	private String detail = "";
	private int left;
	private int right;

	public BiomeHud() {
		super("biome", "Biome card", "The biome you are in, tinted by its colours", Icons.MOUNTAIN, false, 0.5f, 0.06f);
	}

	@Override
	public void layout(boolean preview) {
		Player player = Minecraft.getInstance().player;
		if (preview) {
			name = "Cherry Grove";
			detail = "Temperate · Rain";
			left = 0xFFB6DB61;
			right = 0xFF5DB7EF;
		} else if (player == null || StreamModeFeature.hidesCoordinates()) {
			name = "";
		} else {
			BlockPos pos = player.blockPosition();
			Holder<Biome> holder = player.level().getBiome(pos);
			Biome biome = holder.value();
			name = holder.unwrapKey().map(key -> pretty(key.identifier().getPath())).orElse("Unknown");
			float temp = biome.getBaseTemperature();
			String feel = temp < 0.15f ? "Cold" : temp < 0.95f ? "Temperate" : temp < 1.5f ? "Warm" : "Hot";
			String weather = !biome.hasPrecipitation() ? "Dry" : biome.coldEnoughToSnow(pos, player.level().getSeaLevel()) ? "Snow" : "Rain";
			String dim = player.level().dimension() == Level.NETHER ? "Nether" : player.level().dimension() == Level.END ? "The End" : "Overworld";
			detail = (climate.on() ? feel + " · " + weather : "") + (dimension.on() ? (climate.on() ? " · " : "") + dim : "");
			left = 0xFF000000 | biome.getGrassColor(pos.getX(), pos.getZ());
			right = 0xFF000000 | biome.getWaterColor();
		}
		float w = Math.max(JacText.width(name, TEXT + 2f, JacText.Weight.SEMIBOLD), JacText.width(detail, TEXT, JacText.Weight.REGULAR));
		width = Math.max(70f, w + PAD * 2);
		height = PAD * 2 + 3f + 4f + JacText.lineHeight(TEXT + 2f) + (detail.isEmpty() ? 0f : 2f + JacText.lineHeight(TEXT));
	}

	static String pretty(String path) {
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	@Override
	public boolean hasContent() {
		return !name.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		Draw.horizontalGradient(g, PAD, PAD, width - PAD * 2, 3f, 1.5f, left, right);
		float y = PAD + 3f + 4f;
		text(g, name, PAD, y, Theme.TEXT, TEXT + 2f, JacText.Weight.SEMIBOLD);
		if (!detail.isEmpty()) {
			text(g, detail, PAD, y + JacText.lineHeight(TEXT + 2f) + 2f, Colors.lerp(Theme.TEXT_DIM, left, 0.25f), TEXT, JacText.Weight.REGULAR);
		}
	}
}
