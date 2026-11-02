package dev.justaclient.hud.widgets;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import dev.justaclient.waypoint.Waypoints;
import dev.justaclient.waypoint.Waypoints.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** An arrow towards the waypoint you are tracking ({@code /jwp gps <name>}), with its name and distance. */
public final class GpsHud extends HudFeature {
	private final BoolSetting nearest = bool("nearest", "Track nearest when none is chosen", false);
	private @Nullable Waypoint target;
	private String name = "";
	private String distance = "";

	public GpsHud() {
		super("gps", "GPS", "Arrow to the waypoint you are tracking", Icons.COMPASS, true, 0.5f, 0.12f);
	}

	private @Nullable Waypoint pick(LocalPlayer player) {
		Waypoint chosen = Waypoints.tracking();
		if (chosen != null || !nearest.on()) {
			return chosen;
		}
		Waypoint best = null;
		double bestDist = Double.MAX_VALUE;
		for (Waypoint w : Waypoints.here()) {
			double d = player.distanceToSqr(w.x + 0.5, w.y, w.z + 0.5);
			if (w.visible && d < bestDist) {
				best = w;
				bestDist = d;
			}
		}
		return best;
	}

	@Override
	public void layout(boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		target = preview || player == null ? null : pick(player);
		if (preview) {
			name = "Base";
			distance = "214 m";
		} else if (target != null) {
			name = target.name;
			double dx = target.x + 0.5 - player.getX();
			double dz = target.z + 0.5 - player.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			distance = d >= 1000 ? String.format("%.1f km", d / 1000) : String.format("%.0f m", d);
		}
		width = PAD * 2 + 16f + 4f + Math.max(JacText.width(name, TEXT, JacText.Weight.SEMIBOLD), JacText.width(distance, TEXT, JacText.Weight.REGULAR));
		height = PAD * 2 + 16f;
	}

	@Override
	public boolean hasContent() {
		return target != null;
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float angle = 0f;
		LocalPlayer player = Minecraft.getInstance().player;
		if (!preview && target != null && player != null) {
			double dx = target.x + 0.5 - player.getX();
			double dz = target.z + 0.5 - player.getZ();
			float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
			angle = Mth.wrapDegrees(bearing - player.getYRot());
		}
		int accent = preview || target == null ? JacConfig.accent() : target.color;
		g.pose().pushMatrix();
		g.pose().translate(PAD + 8f, PAD + 8f);
		g.pose().rotate((float) Math.toRadians(angle));
		JacText.icon(g, Icons.NAVIGATION, -7f, -7f, 14f, accent);
		g.pose().popMatrix();
		float tx = PAD + 20f;
		text(g, name, tx, PAD, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
		text(g, distance, tx, PAD + 9f, Theme.TEXT_DIM, TEXT, JacText.Weight.REGULAR);
	}
}
