package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.WorldFx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A glowing ribbon or dots behind you, and optionally other players you can see. */
public final class TrailsFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		RIBBON("Ribbon"), DOTS("Dots");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Anchor implements ChoiceSetting.Named {
		FEET("Feet"), WAIST("Waist");

		private final String label;

		Anchor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.RIBBON);
	private final ChoiceSetting<Anchor> anchor = choice("anchor", "Height", Anchor.FEET);
	private final NumberSetting length = number("length", "Length", 0.8, 0.2, 2.5, 0.1, NumberSetting.decimal(" s"));
	private final NumberSetting width = number("width", "Width", 0.25, 0.05, 0.8, 0.01, v -> String.format("%.2f", v));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting gradient = bool("gradient", "Follow accent gradient", true);
	private final BoolSetting others = bool("others", "Other players too", false);
	private final BoolSetting firstPerson = bool("first_person", "Your trail in first person", true);
	private final Map<Integer, ArrayDeque<Point>> trails = new HashMap<>();

	private record Point(Vec3 pos, long nanos) {
	}

	public TrailsFeature() {
		super("trails", "Trails", "A glowing ribbon behind you as you move", Category.WORLD, Icons.FOOTPRINTS, false);
	}

	public static void init() {
		TrailsFeature feature = Features.get(TrailsFeature.class);
		WorldFx.layer(feature, feature::render);
	}

	@Override
	protected void onDisable() {
		trails.clear();
	}

	private void render(WorldFx.Frame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			trails.clear();
			return;
		}
		long now = System.nanoTime();
		long keep = (long) (length.get() * 1e9);
		List<Integer> seen = new ArrayList<>();
		for (Player p : mc.level.players()) {
			boolean self = p == mc.player;
			if (!self && (!others.on() || p.isInvisibleTo(mc.player) || p.distanceToSqr(mc.player) > 48 * 48 || p.isSpectator())) {
				continue;
			}
			seen.add(p.getId());
			ArrayDeque<Point> points = trails.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
			Vec3 pos = p.getPosition(f.partialTick).add(0, anchor.get() == Anchor.WAIST ? p.getBbHeight() * 0.45 : 0.08, 0);
			Point last = points.peekLast();
			if (!mc.isPaused() && (last == null || last.pos.distanceToSqr(pos) > 0.004)) {
				points.addLast(new Point(pos, now));
			}
			while (!points.isEmpty() && now - points.peekFirst().nanos > keep) {
				points.removeFirst();
			}
			boolean hidden = self && mc.options.getCameraType().isFirstPerson() && !firstPerson.on();
			if (!hidden && points.size() >= 2) {
				draw(f, points, pos, now, keep);
			}
		}
		trails.keySet().retainAll(seen);
	}

	private void draw(WorldFx.Frame f, ArrayDeque<Point> points, Vec3 head, long now, long keep) {
		int n = points.size() + 1;
		List<Vec3> pos = new ArrayList<>(n);
		float[] widths = new float[n];
		int[] colors = new int[n];
		int i = 0;
		Iterator<Point> it = points.iterator();
		while (it.hasNext()) {
			Point p = it.next();
			float age = Math.min(1f, (now - p.nanos) / (float) keep);
			put(pos, widths, colors, i++, p.pos, 1f - age);
		}
		// The newest end follows the player exactly, so the ribbon never lags behind them.
		put(pos, widths, colors, i, head, 1f);
		if (style.get() == Style.RIBBON) {
			f.ribbon(pos, widths, colors);
		} else {
			for (int k = 0; k < n; k += 2) {
				Vec3 p = pos.get(k);
				f.sprite(p.x, p.y, p.z, widths[k] * 1.2f, colors[k]);
			}
		}
	}

	private void put(List<Vec3> pos, float[] widths, int[] colors, int i, Vec3 p, float life) {
		pos.add(p);
		widths[i] = width.f() * (0.25f + 0.75f * life);
		int c = gradient.on() && ColorSetting.ACCENT.equals(color.get()) ? JacConfig.accentAt(1f - life) : color.argb();
		colors[i] = Colors.fade(c, life * life * 0.9f);
	}
}
