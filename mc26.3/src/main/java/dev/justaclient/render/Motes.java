package dev.justaclient.render;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.AmbientParticlesFeature.Style;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Glowing motes drifting in the open air around you, used by Ambient particles and Biome effects. Changing the look
 * fades the old motes out before the new ones fade in, so walking into another biome never pops.
 */
public final class Motes {
	/** How the motes look. {@code accent} uses the theme accent instead of {@code color}. */
	public record Look(Style style, int count, double radius, float size, int color, boolean accent) {
	}

	private static final class Mote {
		double x, y, z, vx, vy, vz;
		float phase, speed;
	}

	private final List<Mote> motes = new ArrayList<>();
	private final RandomSource random = RandomSource.create();
	private @Nullable Look current;
	private float visibility;

	public void clear() {
		motes.clear();
		current = null;
		visibility = 0f;
	}

	/** Fades towards {@code look} (null fades out) and draws this frame. */
	public void draw(WorldFx.Frame f, @Nullable Look look) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}
		boolean swap = look != null && current != null && (look.style() != current.style() || look.color() != current.color()
			|| look.accent() != current.accent());
		if (current == null && look != null) {
			current = look;
		} else if (current != null && look != null && !swap) {
			// Amount, spread and size follow at once; only a new style or colour needs the fade.
			current = look;
		}
		float target = look == null || swap ? 0f : 1f;
		visibility += (target - visibility) * Math.min(1f, f.dt * 1.5f);
		if (visibility < 0.01f) {
			visibility = 0f;
			if (swap || look == null) {
				motes.clear();
				current = look;
			}
			if (look == null) {
				return;
			}
		}
		Look l = Objects.requireNonNull(current);
		Vec3 center = mc.player.getPosition(f.partialTick).add(0, 1.2, 0);
		double r = l.radius();
		while (motes.size() < l.count()) {
			motes.add(spawn(center, r, true));
		}
		while (motes.size() > l.count()) {
			motes.removeLast();
		}
		float t = System.nanoTime() / 1e9f;
		for (int i = 0; i < motes.size(); i++) {
			Mote m = motes.get(i);
			step(m, l.style(), f.dt, t);
			double dx = m.x - center.x, dy = m.y - center.y, dz = m.z - center.z;
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r * r * 1.2 || !open(mc, m)) {
				motes.set(i, spawn(center, r, false));
				continue;
			}
			// Fade out near the edge of the spread so motes never pop in or out.
			float edge = (float) Math.max(0, 1 - Math.sqrt(d2) / r);
			float glow = l.style() == Style.FIREFLIES ? 0.35f + 0.65f * (float) Math.pow(0.5 + 0.5 * Math.sin(t * m.speed * 2.2 + m.phase), 3) : 0.8f;
			float a = Math.min(1f, edge * 2.5f) * glow * visibility;
			int c = l.accent() ? JacConfig.accentAt(i / (float) motes.size()) : l.color();
			f.sprite(m.x, m.y, m.z, l.size() * (l.style() == Style.FIREFLIES ? 1.6f : 1f), Colors.fade(c, a));
		}
		if (l.style() == Style.LINKED) {
			link(f, l.accent() ? JacConfig.accent() : l.color(), r, center);
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

	private static void step(Mote m, Style style, float dt, float t) {
		// Smooth wandering: velocity follows a slowly turning direction per mote.
		double ang = Math.sin(t * 0.3 * m.speed + m.phase) * Math.PI;
		double tx = Math.cos(ang + m.phase) * 0.35;
		double tz = Math.sin(ang * 0.7 + m.phase) * 0.35;
		double ty = style == Style.DRIFT ? -0.25 : Math.sin(t * 0.5 * m.speed + m.phase * 2) * 0.15;
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
