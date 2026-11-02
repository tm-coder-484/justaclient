package dev.justaclient.feature.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import dev.justaclient.render.JacPipelines;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Adds to the overworld night sky: denser twinkling stars, an aurora over the northern horizon and the odd shooting
 * star, plus an optional tint for the daytime sky. The meshes are built and uploaded before vanilla's sky pass and drawn
 * inside it, behind everything else, and only where vanilla would show stars.
 */
public final class SkyFeature extends Feature {
	private static final float DISTANCE = 100f;
	private static final int MAX_STARS = 2400;
	/** Quads per mesh: every star plus the shooting star's segments, and the aurora's two curtains. */
	private static final int STAR_QUADS = MAX_STARS + 16;
	private static final int AURORA_QUADS = 2 * 72;
	private static final int QUAD_BYTES = 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize();

	private final BoolSetting stars = bool("stars", "Extra stars", true);
	private final NumberSetting starCount = number("star_count", "Star count", 1200, 200, MAX_STARS, 100, NumberSetting.unit("")).visibleWhen(stars::on);
	private final BoolSetting twinkle = bool("twinkle", "Twinkle", true).visibleWhen(stars::on);
	private final BoolSetting aurora = bool("aurora", "Aurora", true);
	private final ColorSetting auroraColor = color("aurora_color", "Aurora colour", ColorSetting.ACCENT).visibleWhen(aurora::on);
	private final ColorSetting auroraTop = color("aurora_top", "Aurora top colour", "#8B5CF6").visibleWhen(aurora::on);
	private final NumberSetting auroraBrightness = number("aurora_brightness", "Aurora brightness", 0.8, 0.1, 1.0, 0.05, NumberSetting.percent())
		.visibleWhen(aurora::on);
	private final BoolSetting shootingStars = bool("shooting_stars", "Shooting stars", true);
	private final BoolSetting tint = bool("tint", "Tint the day sky", false);
	private final ColorSetting tintColor = color("tint_color", "Sky tint", ColorSetting.ACCENT).visibleWhen(tint::on);
	private final NumberSetting tintStrength = number("tint_strength", "Tint strength", 0.25, 0.05, 0.8, 0.05, NumberSetting.percent()).visibleWhen(tint::on);

	/** Star corners in sky space, four per star, and a phase and size per star. Built once. */
	private final float[] starCorners = new float[MAX_STARS * 12];
	private final float[] starPhase = new float[MAX_STARS];
	private final float[] starTint = new float[MAX_STARS];
	private final RandomSource random = RandomSource.create();
	private float meteorAge = -1f;
	private float meteorWait = 3f;
	private final Vector3f meteorStart = new Vector3f();
	private final Vector3f meteorDir = new Vector3f();
	private long lastNanos = -1;
	private final ByteBufferBuilder bytes = new ByteBufferBuilder(STAR_QUADS * QUAD_BYTES);
	private @Nullable MappableRingBuffer starBuffer;
	private @Nullable MappableRingBuffer auroraBuffer;
	private int starQuads;
	private int auroraQuads;

	public SkyFeature() {
		super("sky", "Custom sky", "Twinkling stars, aurora and shooting stars at night", Category.WORLD, Icons.MOON, false);
		buildStars();
	}

	private void buildStars() {
		// Fixed seed so the star field is the same every session, like vanilla's.
		Random r = new Random(84_217L);
		for (int i = 0; i < MAX_STARS; i++) {
			Vector3f d;
			do {
				d = new Vector3f(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1);
			} while (d.lengthSquared() > 1f || d.lengthSquared() < 0.01f);
			d.normalize();
			// Mostly small, a few bright ones.
			float size = 0.25f + (float) Math.pow(r.nextFloat(), 6) * 1.1f;
			Vector3f side = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize(size);
			Vector3f up = new Vector3f(side).cross(d).normalize(size);
			Vector3f c = new Vector3f(d).mul(DISTANCE);
			int o = i * 12;
			put(o, c.x - side.x - up.x, c.y - side.y - up.y, c.z - side.z - up.z);
			put(o + 3, c.x + side.x - up.x, c.y + side.y - up.y, c.z + side.z - up.z);
			put(o + 6, c.x + side.x + up.x, c.y + side.y + up.y, c.z + side.z + up.z);
			put(o + 9, c.x - side.x + up.x, c.y - side.y + up.y, c.z - side.z + up.z);
			starPhase[i] = r.nextFloat() * 100f;
			starTint[i] = r.nextFloat();
		}
	}

	private void put(int o, float x, float y, float z) {
		starCorners[o] = x;
		starCorners[o + 1] = y;
		starCorners[o + 2] = z;
	}

	/** Blends the tint into vanilla's sky disc colour. */
	public Vector3fc tintSky(Vector3fc color) {
		if (!isEnabled() || !tint.on()) {
			return color;
		}
		int rgb = 0xFF000000 | Math.round(color.x() * 255) << 16 | Math.round(color.y() * 255) << 8 | Math.round(color.z() * 255);
		int argb = Colors.lerp(rgb, tintColor.argb(), tintStrength.f());
		return new Vector3f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f);
	}

	/** Builds and uploads this frame's meshes. Called just before vanilla opens the sky pass. Brightness values are vanilla's, 0..1. */
	public void prepareNight(SkyRenderState state) {
		starQuads = 0;
		auroraQuads = 0;
		if (!isEnabled()) {
			return;
		}
		cost.begin();
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		float night = Math.max(0f, Math.min(1f, state.starBrightness * 2f)) * state.rainBrightness;
		if (night > 0.01f) {
			float time = (now / 1_000_000L % 3_600_000L) / 1000f;
			if (aurora.on()) {
				drawAurora(time, night);
			}
			if (stars.on() || shootingStars.on()) {
				drawStars(time, state.starAngle, night, dt);
			}
		}
		cost.end();
	}

	/** Draws the meshes uploaded by {@link #prepareNight}. Called at the end of vanilla's sun, moon and stars, inside the sky pass. */
	public void renderNight(RenderPass pass) {
		if (auroraBuffer != null) {
			draw(pass, JacPipelines.AURORA, auroraBuffer, auroraQuads);
		}
		if (starBuffer != null) {
			draw(pass, JacPipelines.SKY_FX, starBuffer, starQuads);
		}
	}

	private static void draw(RenderPass pass, RenderPipeline pipeline, MappableRingBuffer buffer, int quads) {
		if (quads == 0) {
			return;
		}
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer index = indices.getBuffer(quads * 6);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1f, 1f, 1f, 1f));
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", transforms);
		pass.setVertexBuffer(0, buffer.currentBuffer().slice());
		pass.setIndexBuffer(index, indices.type());
		pass.drawIndexed(quads * 6, 1, 0, 0, 0);
	}

	/** Copies a built mesh into the next buffer of a ring and returns its quad count. */
	private static int upload(@Nullable MeshData mesh, MappableRingBuffer ring) {
		if (mesh == null) {
			return 0;
		}
		try (mesh) {
			ring.rotate();
			try (GpuBufferSlice.MappedView view = ring.currentBuffer().map(false, true)) {
				view.data().put(mesh.vertexBuffer());
			}
			return mesh.drawState().vertexCount() / 4;
		}
	}

	private static MappableRingBuffer ring(String name, int quads) {
		return new MappableRingBuffer(() -> name, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, quads * QUAD_BYTES);
	}

	private void drawStars(float time, float starAngle, float night, float dt) {
		BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		boolean any = false;
		if (stars.on()) {
			PoseStack pose = new PoseStack();
			pose.rotateDegrees(Axis.YP, -90f);
			pose.rotate(Axis.XP, starAngle);
			Matrix4f m = pose.last().pose();
			int count = Math.min(MAX_STARS, starCount.i());
			for (int i = 0; i < count; i++) {
				float tw = twinkle.on() ? 0.55f + 0.45f * (float) Math.sin(time * (1.3f + starPhase[i] % 2.2f) + starPhase[i]) : 1f;
				int base = starTint[i] < 0.15f ? 0xFFFFE2B8 : starTint[i] > 0.85f ? 0xFFBFD4FF : 0xFFFFFFFF;
				int c = Colors.fade(base, night * tw * 0.9f);
				int o = i * 12;
				b.addVertex(m, starCorners[o], starCorners[o + 1], starCorners[o + 2]).setUv(-1, -1).setColor(c);
				b.addVertex(m, starCorners[o + 3], starCorners[o + 4], starCorners[o + 5]).setUv(1, -1).setColor(c);
				b.addVertex(m, starCorners[o + 6], starCorners[o + 7], starCorners[o + 8]).setUv(1, 1).setColor(c);
				b.addVertex(m, starCorners[o + 9], starCorners[o + 10], starCorners[o + 11]).setUv(-1, 1).setColor(c);
			}
			any = count > 0;
		}
		if (shootingStars.on()) {
			any |= meteor(b, night, dt);
		}
		MeshData mesh = b.build();
		if (!any && mesh != null) {
			mesh.close();
			return;
		}
		if (starBuffer == null) {
			starBuffer = ring("Justaclient stars", STAR_QUADS);
		}
		starQuads = upload(mesh, starBuffer);
	}

	/** Advances and draws the current shooting star, starting a new one every few seconds. */
	private boolean meteor(BufferBuilder b, float night, float dt) {
		if (Minecraft.getInstance().isPaused()) {
			dt = 0f;
		}
		if (meteorAge < 0f) {
			meteorWait -= dt;
			if (meteorWait > 0f) {
				return false;
			}
			float az = random.nextFloat() * (float) Math.PI * 2;
			float el = 0.5f + random.nextFloat() * 0.6f;
			meteorStart.set((float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.cos(az)));
			// Fall sideways and downwards across the sky.
			Vector3f side = new Vector3f(meteorStart).cross(0, 1, 0).normalize();
			meteorDir.set(side).mul(random.nextBoolean() ? 1 : -1).add(0, -0.45f, 0).normalize();
			meteorAge = 0f;
			meteorWait = 4f + random.nextFloat() * 9f;
		}
		meteorAge += dt;
		float life = 0.9f;
		if (meteorAge > life) {
			meteorAge = -1f;
			return false;
		}
		float t = meteorAge / life;
		float fade = (float) Math.sin(t * Math.PI) * night;
		Matrix4f m = new Matrix4f();
		int segments = 10;
		float head = 0.55f * t;
		float tail = 0.22f;
		Vector3f prev = null;
		for (int i = 0; i <= segments; i++) {
			float s = head - tail * (1f - i / (float) segments);
			Vector3f p = new Vector3f(meteorDir).mul(s).add(meteorStart).normalize(DISTANCE);
			if (prev != null) {
				Vector3f side = new Vector3f(p).sub(prev).cross(p).normalize(0.35f + 0.25f * i / segments);
				int c0 = Colors.fade(0xFFFFFFFF, fade * (i - 1) / segments);
				int c1 = Colors.fade(0xFFFFFFFF, fade * i / segments);
				b.addVertex(m, prev.x - side.x, prev.y - side.y, prev.z - side.z).setUv(0, -1).setColor(c0);
				b.addVertex(m, prev.x + side.x, prev.y + side.y, prev.z + side.z).setUv(0, 1).setColor(c0);
				b.addVertex(m, p.x + side.x, p.y + side.y, p.z + side.z).setUv(0, 1).setColor(c1);
				b.addVertex(m, p.x - side.x, p.y - side.y, p.z - side.z).setUv(0, -1).setColor(c1);
			}
			prev = p;
		}
		return true;
	}

	private void drawAurora(float time, float night) {
		BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		Matrix4f m = new Matrix4f();
		float brightness = auroraBrightness.f() * night;
		int bottom = auroraColor.argb();
		int top = auroraTop.argb();
		// Two curtains at different heights and speeds give it some depth.
		curtain(b, m, time, 0f, 10f, 30f, Colors.fade(bottom, brightness), Colors.fade(top, brightness * 0.8f));
		curtain(b, m, time * 0.7f + 40f, 0.6f, 16f, 22f, Colors.fade(Colors.lerp(bottom, top, 0.4f), brightness * 0.6f), Colors.fade(top, brightness * 0.4f));
		if (auroraBuffer == null) {
			auroraBuffer = ring("Justaclient aurora", AURORA_QUADS);
		}
		auroraQuads = upload(b.build(), auroraBuffer);
	}

	/** One aurora band across the northern sky (negative Z). Angles are in degrees above the horizon. */
	private static void curtain(BufferBuilder b, Matrix4f m, float time, float offset, float baseElevation, float height, int bottom, int top) {
		int segments = 72;
		float span = 150f;
		float prevBx = 0, prevBy = 0, prevBz = 0, prevTx = 0, prevTy = 0, prevTz = 0, prevU = 0;
		for (int i = 0; i <= segments; i++) {
			float f = i / (float) segments;
			float az = (float) Math.toRadians((f - 0.5f) * span + 6f * Math.sin(time * 0.05f + offset));
			float wave = (float) (Math.sin(f * 7f + time * 0.23f + offset) * 3.5f + Math.sin(f * 17f - time * 0.4f) * 1.2f);
			float el0 = (float) Math.toRadians(baseElevation + wave);
			float el1 = (float) Math.toRadians(baseElevation + wave + height * (0.75f + 0.25f * (float) Math.sin(f * 5f + time * 0.17f)));
			float bx = (float) (Math.sin(az) * Math.cos(el0)) * DISTANCE, by = (float) Math.sin(el0) * DISTANCE, bz = (float) (-Math.cos(az) * Math.cos(el0)) * DISTANCE;
			float tx = (float) (Math.sin(az) * Math.cos(el1)) * DISTANCE, ty = (float) Math.sin(el1) * DISTANCE, tz = (float) (-Math.cos(az) * Math.cos(el1)) * DISTANCE;
			float u = f * 30f + time * 0.15f + offset * 10f;
			// Fade the ends of the band.
			float edge = Math.min(1f, Math.min(f, 1f - f) * 6f);
			if (i > 0) {
				float pf = (i - 1) / (float) segments;
				float pedge = Math.min(1f, Math.min(pf, 1f - pf) * 6f);
				b.addVertex(m, prevBx, prevBy, prevBz).setUv(prevU, 0).setColor(Colors.fade(bottom, pedge));
				b.addVertex(m, bx, by, bz).setUv(u, 0).setColor(Colors.fade(bottom, edge));
				b.addVertex(m, tx, ty, tz).setUv(u, 1).setColor(Colors.fade(top, edge));
				b.addVertex(m, prevTx, prevTy, prevTz).setUv(prevU, 1).setColor(Colors.fade(top, pedge));
			}
			prevBx = bx;
			prevBy = by;
			prevBz = bz;
			prevTx = tx;
			prevTy = ty;
			prevTz = tz;
			prevU = u;
		}
	}
}
