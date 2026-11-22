package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.render.Icons;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** How fast you are moving in blocks per second, and which way you face. */
public final class SpeedHud extends RowsHud {
	public enum Unit implements ChoiceSetting.Named {
		BPS("Blocks/s"), KMH("km/h");

		private final String label;

		Unit(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final int SAMPLES = 5;
	private final ChoiceSetting<Unit> unit = choice("unit", "Unit", Unit.BPS);
	private final BoolSetting horizontal = bool("horizontal", "Ignore up and down", true);
	private final BoolSetting vertical = bool("vertical", "Vertical speed", false);
	private final BoolSetting facing = bool("facing", "Facing (N, NE...) and degrees", true);
	private final double[] speeds = new double[SAMPLES];
	private final double[] climbs = new double[SAMPLES];
	private int sample;

	public SpeedHud() {
		super("speed", "Speed", "Blocks per second and the direction you face", Icons.GAUGE, false, 0f, 0.45f);
	}

	@Override
	public void tick() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		// Ridden entities move the player, so measure whatever is actually carrying you.
		Entity moving = player.getRootVehicle();
		double dx = moving.getX() - moving.xo;
		double dy = moving.getY() - moving.yo;
		double dz = moving.getZ() - moving.zo;
		speeds[sample] = horizontal.on() ? Math.sqrt(dx * dx + dz * dz) : Math.sqrt(dx * dx + dy * dy + dz * dz);
		climbs[sample] = dy;
		sample = (sample + 1) % SAMPLES;
	}

	private static double average(double[] values) {
		double sum = 0;
		for (double v : values) {
			sum += v;
		}
		return sum / values.length * 20.0;
	}

	private String format(double bps) {
		return unit.get() == Unit.KMH ? String.format(Locale.ROOT, "%.1f km/h", bps * 3.6) : String.format(Locale.ROOT, "%.2f b/s", bps);
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		Player player = Minecraft.getInstance().player;
		if (!preview && player == null) {
			return;
		}
		rows.add(new Row("Speed", format(preview ? 5.61 : average(speeds))));
		if (vertical.on()) {
			double v = preview ? 0.0 : average(climbs);
			rows.add(new Row("Vertical", (v > 0.005 ? "+" : "") + format(v)));
		}
		if (facing.on()) {
			float yaw = preview ? 225f : player.getYRot();
			// Minecraft's yaw is 0 facing south; a compass bearing is 0 facing north.
			int bearing = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) + 180f), 360);
			rows.add(new Row("Facing", compass(bearing) + " · " + bearing + "°"));
		}
	}

	static String compass(int bearing) {
		String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		return names[Math.floorMod(Math.round(bearing / 45f), 8)];
	}
}
