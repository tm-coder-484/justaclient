package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.ClickTracker;
import dev.justaclient.hud.RowsHud;
import dev.justaclient.render.Icons;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class PlayerInfoHud extends RowsHud {
	private final BoolSetting fps = bool("fps", "Frames per second", true);
	private final BoolSetting ping = bool("ping", "Ping", true);
	private final BoolSetting cps = bool("cps", "Clicks per second", true);
	private final BoolSetting rightCps = bool("right_cps", "Include right clicks", false).visibleWhen(cps::on);
	private final BoolSetting speed = bool("speed", "Speed", false);
	private Vec3 lastPos;
	private double blocksPerSecond;

	public PlayerInfoHud() {
		super("player_info", "Player info", "FPS, ping, clicks per second and speed", Icons.GAUGE, true, 0f, 0f);
	}

	@Override
	public void tick() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Vec3 pos = player.position();
		if (lastPos != null) {
			double dx = pos.x - lastPos.x;
			double dz = pos.z - lastPos.z;
			// Ease the readout a little so it does not flicker between ticks.
			blocksPerSecond += (Math.sqrt(dx * dx + dz * dz) * 20.0 - blocksPerSecond) * 0.5;
		}
		lastPos = pos;
	}

	@Override
	protected void collect(List<Row> rows, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		if (fps.on()) {
			rows.add(new Row("FPS", String.valueOf(mc.getFps())));
		}
		if (ping.on()) {
			rows.add(new Row("Ping", preview ? "32 ms" : ping(mc)));
		}
		if (cps.on()) {
			int left = preview ? 9 : ClickTracker.left();
			rows.add(new Row("CPS", rightCps.on() ? left + " | " + (preview ? 3 : ClickTracker.right()) : String.valueOf(left)));
		}
		if (speed.on()) {
			rows.add(new Row("Speed", String.format("%.1f b/s", preview ? 5.6 : blocksPerSecond)));
		}
	}

	private static String ping(Minecraft mc) {
		if (mc.hasSingleplayerServer() || mc.getConnection() == null || mc.player == null) {
			return "0 ms";
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? "-" : info.getLatency() + " ms";
	}
}
