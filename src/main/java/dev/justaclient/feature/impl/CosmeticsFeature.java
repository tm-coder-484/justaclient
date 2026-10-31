package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Glowing cosmetics drawn on your own player (and optionally friends), visible only on your screen: a halo, and orbs that
 * circle you and trail behind on springs when you move.
 */
public final class CosmeticsFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		HALO("Halo"), ORBS("Orbs"), BOTH("Both");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final int ORBS = 3;
	private final ChoiceSetting<Style> style = choice("style", "Style", Style.BOTH);
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting chroma = bool("chroma", "Chroma", false);
	private final BoolSetting friends = bool("friends", "Show on friends", false);
	private final Map<UUID, Orb[]> orbs = new HashMap<>();

	private static final class Orb {
		double x, y, z, vx, vy, vz;
		boolean placed;
	}

	public CosmeticsFeature() {
		super("cosmetics", "Cosmetics", "A halo and orbiting orbs, visible only to you", Category.WORLD, Icons.ORBIT, false);
	}

	public static void init() {
		CosmeticsFeature feature = Features.get(CosmeticsFeature.class);
		WorldFx.layer(feature, feature::render);
	}

	@Override
	protected void onDisable() {
		orbs.clear();
	}

	private int color(float offset) {
		if (chroma.on()) {
			return Colors.rainbow(offset + System.nanoTime() / 4e9f, 0.6f, 1f);
		}
		return color.get().equals(ColorSetting.ACCENT) ? JacConfig.accentAt(offset % 1f) : color.argb();
	}

	private void render(WorldFx.Frame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self ? !mc.gameRenderer.getMainCamera().isDetached() : !friends.on() || !FriendsFeature.get().isFriend(player.getGameProfile().name())) {
				continue;
			}
			if (player.isInvisible()) {
				continue;
			}
			Vec3 pos = player.getPosition(f.partialTick);
			float time = System.nanoTime() / 1e9f;
			if (style.get() != Style.ORBS) {
				double y = pos.y + player.getBbHeight() + 0.28 + Math.sin(time * 2f) * 0.03;
				f.flatRing(pos.x, y, pos.z, 0.3f, 0.07f, Colors.fade(color(0f), 0.9f), 40, 1f, 0f);
				f.flatRing(pos.x, y, pos.z, 0.3f, 0.22f, Colors.fade(color(0f), 0.25f), 40, 1f, 0f);
			}
			if (style.get() != Style.HALO) {
				drawOrbs(f, player, pos, time);
			}
		}
		orbs.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}

	private void drawOrbs(WorldFx.Frame f, Player player, Vec3 pos, float time) {
		Orb[] list = orbs.computeIfAbsent(player.getUUID(), id -> {
			Orb[] o = new Orb[ORBS];
			for (int i = 0; i < ORBS; i++) {
				o[i] = new Orb();
			}
			return o;
		});
		float dt = Math.min(f.dt, 0.05f);
		for (int i = 0; i < ORBS; i++) {
			Orb o = list[i];
			double a = time * 1.6 + i * Math.PI * 2 / ORBS;
			double tx = pos.x + Math.cos(a) * 0.75;
			double ty = pos.y + player.getBbHeight() * 0.6 + Math.sin(time * 2.3 + i) * 0.15;
			double tz = pos.z + Math.sin(a) * 0.75;
			if (!o.placed || Math.abs(o.x - tx) + Math.abs(o.z - tz) > 16) {
				o.x = tx;
				o.y = ty;
				o.z = tz;
				o.placed = true;
			}
			// A damped spring towards the orbit point, so the orbs lag and swing when you move.
			double k = 60, damping = 9;
			o.vx += ((tx - o.x) * k - o.vx * damping) * dt;
			o.vy += ((ty - o.y) * k - o.vy * damping) * dt;
			o.vz += ((tz - o.z) * k - o.vz * damping) * dt;
			o.x += o.vx * dt;
			o.y += o.vy * dt;
			o.z += o.vz * dt;
			int c = color(i / (float) ORBS);
			f.sprite(o.x, o.y, o.z, 0.5f, Colors.fade(c, 0.35f));
			f.sprite(o.x, o.y, o.z, 0.2f, c);
		}
	}
}
