package dev.justaclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Feature;
import dev.justaclient.hud.FrameCost;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Glowing world effects: short-lived particles and rings, plus per-frame layers that features draw themselves (trails,
 * target markers). Everything is drawn after the main pass with depth testing, so effects sit in the world and are
 * hidden by blocks in front of them like anything else.
 */
public final class WorldFx {
	public static final RenderType FX = RenderType.create("justaclient_fx", RenderSetup.builder(JacPipelines.WORLD_FX).sortOnUpload().createRenderSetup());
	private static final int MAX_PARTICLES = 1500;
	private static final int MAX_RINGS = 64;
	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static final List<Ring> RINGS = new ArrayList<>();
	private static final List<Layer> LAYERS = new ArrayList<>();
	private static final FrameCost.Section COST = FrameCost.section("Effect particles");
	private static long lastNanos = -1;

	private WorldFx() {
	}

	private record Layer(Feature owner, Consumer<Frame> draw) {
	}

	private static final class Particle {
		double x, y, z, vx, vy, vz;
		float size, life, age, gravity, drag;
		int color;
	}

	private static final class Ring {
		double x, y, z;
		float from, to, width, life, age;
		int color;
	}

	public static void init() {
		WorldRenderEvents.END_MAIN.register(WorldFx::render);
	}

	/** Draws {@code draw} every frame while {@code owner} is on, booking the time to the owner. */
	public static void layer(Feature owner, Consumer<Frame> draw) {
		LAYERS.add(new Layer(owner, draw));
	}

	/** One glowing dot that flies, falls and fades. Velocity is in blocks per second. */
	public static void particle(double x, double y, double z, double vx, double vy, double vz, float size, int color, float life, float gravity, float drag) {
		if (PARTICLES.size() >= MAX_PARTICLES) {
			PARTICLES.removeFirst();
		}
		Particle p = new Particle();
		p.x = x;
		p.y = y;
		p.z = z;
		p.vx = vx;
		p.vy = vy;
		p.vz = vz;
		p.size = size;
		p.color = color;
		p.life = life;
		p.gravity = gravity;
		p.drag = drag;
		PARTICLES.add(p);
	}

	/** A flat ring on the ground that grows from {@code from} to {@code to} blocks and fades out. */
	public static void ring(Vec3 at, float from, float to, float width, int color, float life) {
		if (RINGS.size() >= MAX_RINGS) {
			RINGS.removeFirst();
		}
		Ring r = new Ring();
		r.x = at.x;
		r.y = at.y;
		r.z = at.z;
		r.from = from;
		r.to = to;
		r.width = width;
		r.color = color;
		r.life = life;
		RINGS.add(r);
	}

	public static void clear() {
		PARTICLES.clear();
		RINGS.clear();
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (mc.isPaused()) {
			dt = 0f;
		}
		if (mc.level == null) {
			clear();
			return;
		}
		boolean anyLayer = false;
		for (Layer layer : LAYERS) {
			anyLayer |= layer.owner.isEnabled();
		}
		if (PARTICLES.isEmpty() && RINGS.isEmpty() && !anyLayer) {
			return;
		}
		Frame frame = new Frame(context, dt);
		if (!PARTICLES.isEmpty() || !RINGS.isEmpty()) {
			COST.begin();
			drawParticles(frame, dt);
			drawRings(frame, dt);
			COST.end();
		}
		for (Layer layer : LAYERS) {
			if (layer.owner.isEnabled()) {
				layer.owner.cost.begin();
				layer.draw.accept(frame);
				layer.owner.cost.end();
			}
		}
	}

	private static void drawParticles(Frame f, float dt) {
		Iterator<Particle> it = PARTICLES.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			float keep = (float) Math.exp(-p.drag * dt);
			p.vx *= keep;
			p.vz *= keep;
			p.vy = p.vy * keep - p.gravity * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			float t = p.age / p.life;
			// Pop in quickly, then shrink and fade.
			float scale = Math.min(1f, t * 8f) * (1f - t * t);
			f.sprite(p.x, p.y, p.z, p.size * scale, Colors.fade(p.color, 1f - t * t));
		}
	}

	private static void drawRings(Frame f, float dt) {
		Iterator<Ring> it = RINGS.iterator();
		while (it.hasNext()) {
			Ring r = it.next();
			r.age += dt;
			if (r.age >= r.life) {
				it.remove();
				continue;
			}
			float t = r.age / r.life;
			float eased = 1f - (1f - t) * (1f - t) * (1f - t);
			float radius = r.from + (r.to - r.from) * eased;
			f.flatRing(r.x, r.y, r.z, radius, r.width * (1f - t * 0.5f), Colors.fade(r.color, 1f - t), 48, 1f, 0f);
		}
	}

	/** Drawing helpers for one frame. World positions are absolute; the frame subtracts the camera. */
	public static final class Frame {
		public final float dt;
		public final float partialTick;
		public final Vec3 camera;
		private final PoseStack.Pose pose;
		private final VertexConsumer out;
		private final Vector3f right;
		private final Vector3f up;

		Frame(WorldRenderContext context, float dt) {
			this.dt = dt;
			this.partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			this.camera = context.worldState().cameraRenderState.pos;
			this.pose = context.matrices().last();
			this.out = context.consumers().getBuffer(FX);
			Quaternionf rotation = context.worldState().cameraRenderState.orientation;
			this.right = rotation.transform(new Vector3f(1f, 0f, 0f));
			this.up = rotation.transform(new Vector3f(0f, 1f, 0f));
		}

		/** A camera-facing glowing disc {@code size} blocks across. */
		public void sprite(double x, double y, double z, float size, int color) {
			if (size <= 0.001f || (color >>> 24) == 0) {
				return;
			}
			float cx = (float) (x - camera.x);
			float cy = (float) (y - camera.y);
			float cz = (float) (z - camera.z);
			float h = size / 2f;
			float rx = right.x * h, ry = right.y * h, rz = right.z * h;
			float ux = up.x * h, uy = up.y * h, uz = up.z * h;
			vertex(cx - rx - ux, cy - ry - uy, cz - rz - uz, -1f, -1f, color);
			vertex(cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, -1f, color);
			vertex(cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, 1f, color);
			vertex(cx - rx + ux, cy - ry + uy, cz - rz + uz, -1f, 1f, color);
		}

		/**
		 * A ring lying flat at height {@code y}. {@code fill} below 1 splits it into that many dashes' worth of arc (e.g. 0.6
		 * draws 60% of each dash slot), and {@code rotation} (turns) spins the dashes.
		 */
		public void flatRing(double x, double y, double z, float radius, float width, int color, int segments, float fill, float rotation) {
			if (radius <= 0f || (color >>> 24) == 0) {
				return;
			}
			float cx = (float) (x - camera.x);
			float cy = (float) (y - camera.y);
			float cz = (float) (z - camera.z);
			float inner = Math.max(0f, radius - width / 2f);
			float outer = radius + width / 2f;
			int dashes = fill >= 1f ? 1 : 12;
			int perDash = Math.max(2, segments / dashes);
			for (int d = 0; d < dashes; d++) {
				float start = (d + rotation) / dashes;
				float span = (fill >= 1f ? 1f : fill) / dashes;
				for (int i = 0; i < perDash; i++) {
					double a0 = (start + span * i / perDash) * Math.PI * 2;
					double a1 = (start + span * (i + 1) / perDash) * Math.PI * 2;
					float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0);
					float s1 = (float) Math.sin(a1), c1 = (float) Math.cos(a1);
					vertex(cx + s0 * inner, cy, cz + c0 * inner, 0f, -1f, color);
					vertex(cx + s0 * outer, cy, cz + c0 * outer, 0f, 1f, color);
					vertex(cx + s1 * outer, cy, cz + c1 * outer, 0f, 1f, color);
					vertex(cx + s1 * inner, cy, cz + c1 * inner, 0f, -1f, color);
				}
			}
		}

		/**
		 * A camera-facing ribbon through {@code points} (absolute positions), with a width and colour per point. Used for
		 * trails and streaks.
		 */
		public void ribbon(List<Vec3> points, float[] widths, int[] colors) {
			int n = points.size();
			if (n < 2) {
				return;
			}
			float[] sx = new float[n], sy = new float[n], sz = new float[n];
			float[] px = new float[n], py = new float[n], pz = new float[n];
			for (int i = 0; i < n; i++) {
				Vec3 p = points.get(i);
				px[i] = (float) (p.x - camera.x);
				py[i] = (float) (p.y - camera.y);
				pz[i] = (float) (p.z - camera.z);
			}
			for (int i = 0; i < n; i++) {
				int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
				float tx = px[b] - px[a], ty = py[b] - py[a], tz = pz[b] - pz[a];
				// Side vector: across the tangent and the view direction, so the ribbon faces the camera.
				float ox = ty * pz[i] - tz * py[i];
				float oy = tz * px[i] - tx * pz[i];
				float oz = tx * py[i] - ty * px[i];
				float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
				if (len < 1e-6f) {
					ox = right.x;
					oy = right.y;
					oz = right.z;
					len = 1f;
				}
				float w = widths[i] / 2f / len;
				sx[i] = ox * w;
				sy[i] = oy * w;
				sz[i] = oz * w;
			}
			for (int i = 0; i < n - 1; i++) {
				int j = i + 1;
				vertex(px[i] - sx[i], py[i] - sy[i], pz[i] - sz[i], 0f, -1f, colors[i]);
				vertex(px[i] + sx[i], py[i] + sy[i], pz[i] + sz[i], 0f, 1f, colors[i]);
				vertex(px[j] + sx[j], py[j] + sy[j], pz[j] + sz[j], 0f, 1f, colors[j]);
				vertex(px[j] - sx[j], py[j] - sy[j], pz[j] - sz[j], 0f, -1f, colors[j]);
			}
		}

		private void vertex(float x, float y, float z, float u, float v, int color) {
			out.addVertex(pose, x, y, z).setUv(u, v).setColor(color);
		}
	}
}
