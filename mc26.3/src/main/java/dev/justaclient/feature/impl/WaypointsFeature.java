package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.Projection;
import dev.justaclient.render.WorldFx;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

/**
 * Marks saved places with a light beam and a label. Add one with the key (B by default) or {@code /jwp add <name>};
 * a death point is dropped automatically. The GPS widget points at the tracked one.
 */
public final class WaypointsFeature extends Feature {
	private final KeySetting addKey = key("add_key", "Add waypoint here", InputConstants.KEY_B);
	private final BoolSetting beams = bool("beams", "Light beams", true);
	private final BoolSetting labels = bool("labels", "Labels", true);
	private final BoolSetting distance = bool("distance", "Show distance", true).visibleWhen(labels::on);
	private final BoolSetting deaths = bool("deaths", "Mark where you died", true);
	private final NumberSetting range = number("range", "Hide beyond", 0, 0, 5000, 100, v -> v == 0 ? "Never" : String.format("%.0f m", v));
	private boolean keyWasDown;
	private boolean wasDead;

	public WaypointsFeature() {
		super("waypoints", "Waypoints", "Beams and labels for saved places, plus a death point", Category.UTILITY, Icons.MAP_PIN, true);
	}

	public static void init() {
		WaypointsFeature feature = Features.get(WaypointsFeature.class);
		WorldFx.layer(feature, feature::renderWorld);
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		boolean down = mc.gui.screen() == null && addKey.isDown();
		if (down && !keyWasDown && player != null) {
			Waypoint w = Waypoints.add(Waypoints.nextName(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
			Notifications.push(Icons.MAP_PIN, w.name, "saved at " + w.x + " " + w.y + " " + w.z);
		}
		keyWasDown = down;
		boolean dead = player != null && player.isDeadOrDying();
		if (dead && !wasDead && deaths.on()) {
			Waypoints.death(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		}
		wasDead = dead;
	}

	private boolean inRange(Waypoint w, Vec3 from) {
		return range.get() <= 0 || horizontalDistance(w, from) <= range.get();
	}

	static double horizontalDistance(Waypoint w, Vec3 from) {
		double dx = w.x + 0.5 - from.x;
		double dz = w.z + 0.5 - from.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void renderWorld(WorldFx.Frame f) {
		if (!beams.on()) {
			return;
		}
		for (Waypoint w : Waypoints.here()) {
			if (!w.visible || !inRange(w, f.camera)) {
				continue;
			}
			double x = w.x + 0.5, z = w.z + 0.5;
			double dist = horizontalDistance(w, f.camera);
			// Grow with distance so far beams stay a few pixels wide, and fade out up close so they do not blind you.
			float width = (float) Math.max(0.5, dist * 0.025);
			float near = (float) Math.min(1.0, Math.max(0.15, (dist - 2.0) / 12.0));
			int base = Colors.fade(w.color, 0.75f * near);
			double top = Math.max(w.y + 64, f.camera.y + 96);
			f.ribbon(List.of(new Vec3(x, w.y, z), new Vec3(x, w.y + 24, z), new Vec3(x, top, z)),
				new float[] {width, width * 0.8f, width * 0.4f}, new int[] {base, Colors.fade(base, 0.55f), 0});
			f.sprite(x, w.y + 0.5, z, (float) Math.max(0.8, dist * 0.035), Colors.fade(w.color, near));
		}
	}

	/** Draws the labels over the HUD. Called by the HUD renderer every frame. */
	public void renderLabels(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!labels.on() || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		for (Waypoint w : Waypoints.here()) {
			if (!w.visible || !inRange(w, eye)) {
				continue;
			}
			Vector2f at = Projection.toGui(new Vec3(w.x + 0.5, w.y + 1.4, w.z + 0.5), g.guiWidth(), g.guiHeight());
			if (at == null || at.x < -50 || at.y < -20 || at.x > g.guiWidth() + 50 || at.y > g.guiHeight() + 20) {
				continue;
			}
			double dist = eye.distanceTo(new Vec3(w.x + 0.5, w.y + 0.5, w.z + 0.5));
			String sub = distance.on() ? (dist >= 1000 ? String.format("%.1f km", dist / 1000) : String.format("%.0f m", dist)) : "";
			label(g, w, sub, at.x, at.y);
		}
	}

	private static void label(GuiGraphicsExtractor g, Waypoint w, String sub, float cx, float cy) {
		float size = HudFeature.TEXT;
		float nameW = JacText.width(w.name, size, JacText.Weight.SEMIBOLD);
		float subW = sub.isEmpty() ? 0 : JacText.width(sub, size, JacText.Weight.REGULAR) + 5f;
		float w2 = nameW + subW + 14f;
		float h = 12f;
		float x = cx - w2 / 2f;
		float y = cy - h / 2f;
		Draw.rect(g, x, y, w2, h, h / 2f, 0xB0101218);
		Draw.circle(g, x + 6f, y + h / 2f, 2.2f, w.color);
		JacText.draw(g, w.name, x + 10f, y + 3f, Theme.TEXT, size, JacText.Weight.SEMIBOLD);
		if (!sub.isEmpty()) {
			JacText.draw(g, sub, x + 10f + nameW + 5f, y + 3f, Theme.TEXT_DIM, size, JacText.Weight.REGULAR);
		}
	}
}
