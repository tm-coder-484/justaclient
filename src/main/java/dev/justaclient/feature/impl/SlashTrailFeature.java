package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A curved streak that sweeps across your view when you swing. It is drawn in the world just in front of you, so it
 * shows in first and third person alike, and only for you.
 */
public final class SlashTrailFeature extends Feature {
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final NumberSetting width = number("width", "Width", 0.16, 0.05, 0.4, 0.01, v -> String.format("%.2f", v));
	private final NumberSetting duration = number("duration", "Duration", 0.22, 0.1, 0.5, 0.01, NumberSetting.decimal(" s"));
	private final BoolSetting everySwing = bool("every_swing", "On every swing, not just attacks", false);
	private final BoolSetting alternate = bool("alternate", "Alternate direction", true);
	private final Deque<Slash> slashes = new ArrayDeque<>();
	private boolean wasSwinging;
	private boolean flip;

	private record Slash(Vec3 eye, float yaw, float pitch, boolean flip, long start) {
	}

	public SlashTrailFeature() {
		super("slash_trail", "Slash trail", "A streak sweeps across your view when you swing", Category.FIRST_PERSON, Icons.WIND, false);
	}

	public static void init() {
		SlashTrailFeature f = Features.get(SlashTrailFeature.class);
		WorldFx.layer(f, f::render);
	}

	public void onAttack() {
		if (isEnabled() && !everySwing.on()) {
			spawn();
		}
	}

	@Override
	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean swinging = player != null && player.swinging;
		if (swinging && !wasSwinging && everySwing.on()) {
			spawn();
		}
		wasSwinging = swinging;
	}

	private void spawn() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		slashes.add(new Slash(player.getEyePosition(), player.getYRot(), player.getXRot(), alternate.on() && flip, System.nanoTime()));
		flip = !flip;
		while (slashes.size() > 6) {
			slashes.removeFirst();
		}
	}

	private void render(WorldFx.Frame frame) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || slashes.isEmpty()) {
			return;
		}
		float life = duration.f();
		long now = System.nanoTime();
		slashes.removeIf(s -> (now - s.start) / 1e9f > life * 1.6f);
		Vec3 eye = player.getEyePosition(frame.partialTick);
		int base = color.argb();
		int n = 18;
		for (Slash s : slashes) {
			float t = (now - s.start) / 1e9f / life;
			// The head sweeps across in the first part of the life, the tail follows and the streak fades.
			float head = Math.min(1f, t);
			float tail = Math.max(0f, Math.min(1f, (t - 0.35f) / 0.9f));
			if (head <= tail) {
				continue;
			}
			float fade = 1f - Math.max(0f, (t - 1f) / 0.6f);
			// Follow the player's body but keep the angle of the moment the swing started.
			Vec3 origin = eye;
			List<Vec3> points = new ArrayList<>();
			float[] widths = new float[n];
			int[] colors = new int[n];
			for (int i = 0; i < n; i++) {
				float u = tail + (head - tail) * i / (n - 1);
				// An arc from upper right to lower left (or mirrored), 1.5 blocks out in front.
				double sweep = Math.toRadians((s.flip ? -1 : 1) * (55 - 110 * u));
				double lift = Math.toRadians(12 - 36 * u);
				double yaw = Math.toRadians(-s.yaw) + sweep;
				double pitch = Math.toRadians(-s.pitch) + lift;
				double r = 1.45 + 0.1 * Math.sin(u * Math.PI);
				Vec3 dir = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
				points.add(origin.add(dir.scale(r)).add(0, -0.15, 0));
				float k = (float) i / (n - 1);
				widths[i] = width.f() * (0.15f + 0.85f * (float) Math.sin(k * Math.PI * 0.5));
				colors[i] = Colors.fade(Colors.lerp(base, 0xFFFFFFFF, k * 0.6f), fade * (0.25f + 0.75f * k));
			}
			frame.ribbon(points, widths, colors);
		}
	}
}
