package dev.justaclient.hud.widgets;

import dev.justaclient.feature.impl.StreamModeFeature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.render.Icons;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class CoordinatesHud extends RowsHud {
	private final BoolSetting nether = bool("nether", "Nether / overworld conversion", true);
	private final BoolSetting facing = bool("facing", "Facing", true);
	private final BoolSetting biome = bool("biome", "Biome", false);
	private final BoolSetting clock = bool("clock", "Clock", false);
	private final BoolSetting realTime = bool("real_time", "Use real time", false).visibleWhen(clock::on);

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "Position, nether conversion, facing and clock", Icons.MAP_PIN, true, 0f, 0.32f);
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		Player player = Minecraft.getInstance().player;
		if (!preview && (player == null || StreamModeFeature.hidesCoordinates())) {
			return;
		}
		int x = preview ? 128 : player.getBlockX();
		int y = preview ? 64 : player.getBlockY();
		int z = preview ? -342 : player.getBlockZ();
		rows.add(new Row("XYZ", x + " " + y + " " + z));
		boolean inNether = !preview && player.level().dimension() == Level.NETHER;
		boolean inOverworld = preview || player.level().dimension() == Level.OVERWORLD;
		if (nether.on() && (inNether || inOverworld)) {
			rows.add(inNether
				? new Row("Overworld", x * 8 + " " + z * 8)
				: new Row("Nether", Math.floorDiv(x, 8) + " " + Math.floorDiv(z, 8)));
		}
		if (facing.on()) {
			Direction dir = preview ? Direction.NORTH : player.getDirection();
			String name = dir.getName();
			rows.add(new Row("Facing", Character.toUpperCase(name.charAt(0)) + name.substring(1)));
		}
		if (biome.on()) {
			rows.add(new Row("Biome", preview ? "Plains" : biomeName(player)));
		}
		if (clock.on()) {
			rows.add(new Row("Time", preview ? "12:30" : time(player)));
		}
	}

	private static String biomeName(Player player) {
		return player.level().getBiome(player.blockPosition()).unwrapKey()
			.map(key -> {
				String path = key.identifier().getPath().replace('_', ' ');
				return Character.toUpperCase(path.charAt(0)) + path.substring(1);
			})
			.orElse("Unknown");
	}

	private String time(Player player) {
		if (realTime.on()) {
			return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		}
		// Day time 0 is 06:00 in game.
		long ticks = (player.level().getOverworldClockTime() + 6000L) % 24000L;
		long hours = ticks / 1000L;
		long minutes = ticks % 1000L * 60L / 1000L;
		return String.format("%02d:%02d", hours, minutes);
	}
}
