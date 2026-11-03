package dev.justaclient.waypoint;

import dev.justaclient.anim.Colors;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One place the GPS, beams and outlines share: a waypoint, a remembered chest, a structure or a death point. Features
 * hand a {@link Target} to {@link #track} (the GPS points at it until you get there) or {@link #highlight} (an outline for
 * a while). Only places you have been or seen are ever passed in.
 */
public final class Locate {
	/** A place: a block, or a box of blocks such as a double chest. */
	public record Target(String label, Icons icon, int x, int y, int z, String dimension, int color, int x2, int y2, int z2) {
		public Target(String label, Icons icon, int x, int y, int z, String dimension, int color) {
			this(label, icon, x, y, z, dimension, color, x, y, z);
		}

		public double centreX() {
			return (Math.min(x, x2) + Math.max(x, x2) + 1) / 2.0;
		}

		public double centreZ() {
			return (Math.min(z, z2) + Math.max(z, z2) + 1) / 2.0;
		}
	}

	private record Highlight(Target target, String world, long until) {
	}

	private static @Nullable Target tracked;
	private static @Nullable String trackedWorld;
	private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

	private Locate() {
	}

	/** Points the GPS at a place in this world. Replaces any waypoint the GPS was tracking. */
	public static void track(Target target) {
		Waypoints.track(null);
		tracked = target;
		trackedWorld = Waypoints.worldKey();
	}

	public static void clear() {
		tracked = null;
		trackedWorld = null;
	}

	/** The tracked place, while you are in its world and dimension. */
	public static @Nullable Target tracked() {
		Target t = tracked;
		if (t == null || trackedWorld == null || !trackedWorld.equals(Waypoints.worldKey()) || !t.dimension.equals(Waypoints.dimension())) {
			return null;
		}
		return t;
	}

	/** True if this place is the one being tracked. */
	public static boolean isTracked(Target t) {
		Target cur = tracked;
		return cur != null && cur.x == t.x && cur.y == t.y && cur.z == t.z && cur.dimension.equals(t.dimension);
	}

	/** Outlines a place for {@code seconds}. */
	public static void highlight(Target target, float seconds) {
		String world = Waypoints.worldKey();
		if (world == null) {
			return;
		}
		HIGHLIGHTS.removeIf(h -> h.target.x == target.x && h.target.y == target.y && h.target.z == target.z);
		HIGHLIGHTS.add(new Highlight(target, world, System.currentTimeMillis() + (long) (seconds * 1000)));
	}

	/** Called every tick: ends the trip on arrival and drops outlines that ran out. */
	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		long now = System.currentTimeMillis();
		HIGHLIGHTS.removeIf(h -> h.until < now);
		Target t = tracked();
		if (player == null || t == null) {
			return;
		}
		double dx = t.centreX() - player.getX();
		double dz = t.centreZ() - player.getZ();
		if (dx * dx + dz * dz < 9 && Math.abs(t.y + 0.5 - player.getY()) < 5) {
			Notifications.push(Icons.CHECK, "Arrived", t.label);
			// Keep it outlined a little longer so the right chest is easy to spot.
			highlight(t, 8f);
			clear();
		}
	}

	/** Outlines the highlighted and tracked places in this dimension, within {@code range} blocks. */
	public static void render(WorldFx.Frame f, double range) {
		String world = Waypoints.worldKey();
		String dim = Waypoints.dimension();
		if (world == null) {
			return;
		}
		long now = System.currentTimeMillis();
		float pulse = 0.75f + 0.25f * (float) Math.sin(now / 260.0);
		List<Target> draw = new ArrayList<>();
		Iterator<Highlight> it = HIGHLIGHTS.iterator();
		while (it.hasNext()) {
			Highlight h = it.next();
			if (h.world.equals(world) && h.target.dimension.equals(dim)) {
				draw.add(h.target);
			}
		}
		Target t = tracked();
		if (t != null && draw.stream().noneMatch(d -> d.x == t.x && d.y == t.y && d.z == t.z)) {
			draw.add(t);
		}
		if (draw.isEmpty()) {
			return;
		}
		for (Target d : draw) {
			double cx = d.centreX();
			double cz = d.centreZ();
			Vec3 cam = f.camera;
			if (range > 0 && (cx - cam.x) * (cx - cam.x) + (cz - cam.z) * (cz - cam.z) > range * range) {
				continue;
			}
			// Wider with distance so a far outline stays a few pixels thick.
			double dist = Math.sqrt((cx - cam.x) * (cx - cam.x) + (d.y + 0.5 - cam.y) * (d.y + 0.5 - cam.y) + (cz - cam.z) * (cz - cam.z));
			float width = (float) Math.max(0.08, dist * 0.012);
			f.boxOutline(Math.min(d.x, d.x2), Math.min(d.y, d.y2), Math.min(d.z, d.z2), Math.max(d.x, d.x2) + 1, Math.max(d.y, d.y2) + 1,
				Math.max(d.z, d.z2) + 1, width, Colors.fade(d.color, pulse));
			// A soft glow in the middle, so a far box is still easy to spot.
			f.sprite(cx, (Math.min(d.y, d.y2) + Math.max(d.y, d.y2) + 1) / 2.0, cz, (float) Math.max(1.2, dist * 0.05), Colors.fade(d.color, 0.35f * pulse));
		}
	}
}
