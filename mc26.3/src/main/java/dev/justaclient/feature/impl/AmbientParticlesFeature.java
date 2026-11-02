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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Drifting glowing motes in the air around you: fireflies, slow snow-like dots, or dots joined by faint lines. They live
 * only on your screen and only in open air, so they never hint at anything hidden.
 */
public final class AmbientParticlesFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		FIREFLIES("Fireflies"), DRIFT("Drift"), LINKED("Linked dots");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.FIREFLIES);
	private final NumberSetting count = number("count", "Amount", 60, 10, 200, 5, NumberSetting.unit(""));
	private final NumberSetting radius = number("radius", "Spread", 10, 4, 24, 1, NumberSetting.unit(" blocks"));
	private final NumberSetting size = number("size", "Size", 0.12, 0.04, 0.4, 0.01, v -> String.format("%.2f", v));
	private final ColorSetting color = color("color", "Colour", "#F59E0B");
	private final BoolSetting nightOnly = bool("night_only", "Only at night", false);
	private final List<Mote> motes = new ArrayList<>();
	private final RandomSource random = RandomSource.create();
	private float visibility;

	private static final class Mote {
		double x, y, z, vx, vy, vz;
		float phase, speed;
	}

	public AmbientParticlesFeature() {
		super("ambient_particles", "Ambient particles", "Fireflies and drifting motes around you", Category.WORLD, Icons.SPARKLES, false);
	}

	public static void init() {
		AmbientParticlesFeature feature = Features.get(AmbientParticlesFeature.class);
		WorldFx.layer(feature, feature::render);
	}

	@Override
	protected void onDisable() {
		motes.clear();
	}

	private void render(WorldFx.Frame f) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			motes.clear();
			return;
		}
		boolean night = mc.level.getSkyDarken() > 4 || !mc.level.dimensionType().hasSkyLight();
		visibility += ((!nightOnly.on() || night ? 1f : 0f) - visibility) * Math.min(1f, f.dt * 1.5f);
		if (visibility < 0.01f) {
			return;
		}
		Vec3 center = mc.player.getPosition(f.partialTick).add(0, 1.2, 0);
		double r = radius.get();
		int want = count.i();
		while (motes.size() < want) {
			motes.add(spawn(center, r, true));
		}
		while (motes.size() > want) {
			motes.removeLast();
		}
		float t = System.nanoTime() / 1e9f;
		int base = ColorSetting.ACCENT.equals(color.get()) ? JacConfig.accent() : color.argb();
		for (int i = 0; i < motes.size(); i++) {
			Mote m = motes.get(i);
			step(m, f.dt, t);
			double dx = m.x - center.x, dy = m.y - center.y, dz = m.z - center.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r * r * 1.2 || !open(mc, m)) {
				motes.set(i, spawn(center, r, false));
				continue;
			}
			// Fade out near the edge of the spread so motes never pop in or out.
			float edge = (float) Math.max(0, 1 - Math.sqrt(d2) / r);
			float glow = style.get() == Style.FIREFLIES ? 0.35f + 0.65f * (float) Math.pow(0.5 + 0.5 * Math.sin(t * m.speed * 2.2 + m.phase), 3) : 0.8f;
			float a = Math.min(1f, edge * 2.5f) * glow * visibility;
			int c = ColorSetting.ACCENT.equals(color.get()) ? JacConfig.accentAt(i / (float) motes.size()) : base;
			f.sprite(m.x, m.y, m.z, size.f() * (style.get() == Style.FIREFLIES ? 1.6f : 1f), Colors.fade(c, a));
		}
		if (style.get() == Style.LINKED) {
			link(f, base, r, center);
		}
	}

	private void link(WorldFx.Frame f, int color, double r, Vec3 center) {
		double max = 2.6;
		List<Vec3> pair = new ArrayList<>(2);
		float[] widths = {0.015f, 0.015f};
		int[] colors = new int[2];
		for (int i = 0; i < motes.size(); i++) {
			Mote a = motes.get(i);
			for (int j = i + 1; j < motes.size(); j++) {
				Mote b = motes.get(j);
				double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d < max) {
					float strength = (float) (1 - d / max) * 0.5f * visibility;
					double ex = a.x - center.x, ey = a.y - center.y, ez = a.z - center.z;
					float edge = (float) Math.max(0, 1 - Math.sqrt(ex * ex + ey * ey + ez * ez) / r);
					colors[0] = colors[1] = Colors.fade(color, strength * Math.min(1f, edge * 2.5f));
					pair.clear();
					pair.add(new Vec3(a.x, a.y, a.z));
					pair.add(new Vec3(b.x, b.y, b.z));
					f.ribbon(pair, widths, colors);
				}
			}
		}
	}

	private void step(Mote m, float dt, float t) {
		// Smooth wandering: velocity follows a slowly turning direction per mote.
		double ang = Math.sin(t * 0.3 * m.speed + m.phase) * Math.PI;
		double tx = Math.cos(ang + m.phase) * 0.35;
		double tz = Math.sin(ang * 0.7 + m.phase) * 0.35;
		double ty = style.get() == Style.DRIFT ? -0.25 : Math.sin(t * 0.5 * m.speed + m.phase * 2) * 0.15;
		double k = Math.min(1, dt * 0.8);
		m.vx += (tx - m.vx) * k;
		m.vy += (ty - m.vy) * k;
		m.vz += (tz - m.vz) * k;
		m.x += m.vx * dt;
		m.y += m.vy * dt;
		m.z += m.vz * dt;
	}

	private Mote spawn(Vec3 center, double r, boolean anywhere) {
		Mote m = new Mote();
		double u = anywhere ? Math.cbrt(random.nextDouble()) : 0.85 + random.nextDouble() * 0.15;
		double theta = random.nextDouble() * Math.PI * 2;
		double phi = Math.acos(2 * random.nextDouble() - 1);
		m.x = center.x + r * u * Math.sin(phi) * Math.cos(theta);
		m.y = center.y + r * u * Math.cos(phi) * 0.5;
		m.z = center.z + r * u * Math.sin(phi) * Math.sin(theta);
		m.phase = random.nextFloat() * 6.28f;
		m.speed = 0.6f + random.nextFloat() * 0.8f;
		return m;
	}

	private static boolean open(Minecraft mc, Mote m) {
		BlockPos pos = BlockPos.containing(m.x, m.y, m.z);
		return mc.level.getBlockState(pos).isAir();
	}
}
