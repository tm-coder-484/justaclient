package dev.justaclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Counts what happened since you joined the current world or server, for the Session stats widget. */
public final class SessionStats {
	private static long joinedMillis = System.currentTimeMillis();
	private static int kills;
	private static int deaths;
	private static int blocks;
	private static double distance;
	private static double lastX = Double.NaN;
	private static double lastZ;
	private static boolean wasDead;

	private SessionStats() {
	}

	public static void reset() {
		joinedMillis = System.currentTimeMillis();
		kills = 0;
		deaths = 0;
		blocks = 0;
		distance = 0;
		lastX = Double.NaN;
	}

	public static void onKill() {
		kills++;
		LifetimeStats.onKill();
	}

	public static void onBlockBroken() {
		blocks++;
		LifetimeStats.onBlockBroken();
	}

	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			lastX = Double.NaN;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			deaths++;
			LifetimeStats.onDeath();
		}
		wasDead = dead;
		if (!Double.isNaN(lastX)) {
			double dx = player.getX() - lastX;
			double dz = player.getZ() - lastZ;
			double step = Math.sqrt(dx * dx + dz * dz);
			// Ignore teleports and respawns.
			if (step < 10) {
				distance += step;
				LifetimeStats.onMoved(step);
			}
		}
		lastX = player.getX();
		lastZ = player.getZ();
	}

	public static long playMillis() {
		return System.currentTimeMillis() - joinedMillis;
	}

	public static int kills() {
		return kills;
	}

	public static int deaths() {
		return deaths;
	}

	public static int blocks() {
		return blocks;
	}

	public static double distance() {
		return distance;
	}
}
