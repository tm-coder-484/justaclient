package dev.justaclient.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.justaclient.common.spatial.SpatialPanel;
import dev.justaclient.feature.impl.SpatialMenusFeature;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the open menu on a panel in the world (Spatial menus).
 * <p>
 * Just before the screen is extracted, the GUI gets a split: everything the screen draws lands after it. The GUI
 * renderer then draws that second part into an offscreen texture instead of the window, and {@link #composite} lays the
 * texture over the frame through a full-screen shader that traces each pixel's ray onto the (leaned, turned, curved)
 * panel. The HUD stays flat. Mouse positions go through the same panel maths backwards ({@link #mapMouse}), so the menu
 * reacts where it appears.
 */
public final class SpatialMenus {
	private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get();
	/** How far in front of the eye the panel would fill the window at 100% size. */
	private static final double REFERENCE_DISTANCE = 2.0;
	private static final long FLY_MS = 280;

	private static @Nullable TextureTarget target;
	private static @Nullable MappableRingBuffer ubo;
	/** This frame's screen is drawing into the offscreen target. */
	private static boolean frameActive;
	/** The split for this frame is taken; later blur requests are ignored. */
	private static boolean splitTaken;
	private static @Nullable SpatialPanel panel;
	private static @Nullable Screen panelScreen;
	private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();
	private static float opacity = 1f;
	/** Counts GUI frames; {@link #activeFrame} is the last one that floated a menu. */
	private static long frame;
	private static long activeFrame = -10;
	private static long openedAt;
	private static float tiltX;
	private static float tiltY;
	// World anchor: where the panel was when it opened, in world space.
	private static @Nullable Vec3 anchorCenter;
	private static @Nullable Vector3f anchorRight;
	private static @Nullable Vector3f anchorUp;
	private static @Nullable Vector3f anchorNormal;

	private SpatialMenus() {
	}

	/** True while a spatial screen is being extracted, so screen backgrounds (dark fill, blur) are skipped. */
	public static boolean drawing() {
		return frameActive;
	}

	/** Lets vanilla's own blur split through unless this frame's split is already taken. */
	public static boolean blurAllowed() {
		return !splitTaken;
	}

	public static void resetFrame() {
		splitTaken = false;
	}

	/**
	 * Called just before {@code screen} is extracted. Decides whether it floats this frame and, if so, draws the flat dim
	 * behind it and splits the GUI so the screen's own drawing can be redirected.
	 */
	public static void beginScreen(GuiGraphicsExtractor g, GuiRenderState state, int firstStratumAfterBlur, Screen screen) {
		frameActive = false;
		SpatialMenusFeature f = SpatialMenusFeature.get();
		if (f == null || !f.wants(screen) || firstStratumAfterBlur != Integer.MAX_VALUE || !Projection.valid()) {
			panelScreen = null;
			return;
		}
		long now = System.currentTimeMillis();
		// Counted in frames, not time: on a slow machine a frame can take longer than any sensible timeout.
		boolean fresh = activeFrame < frame - 1;
		activeFrame = frame;
		if (fresh) {
			openedAt = now;
			tiltX = 0;
			tiltY = 0;
		}
		place(f, now, fresh);
		panelScreen = screen;
		float dim = f.dim.f() * opacity;
		if (dim > 0.005f) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), Math.round(dim * 255) << 24);
		}
		state.nextStratum();
		state.blurBeforeThisStratum();
		splitTaken = true;
		frameActive = true;
	}

	private static void place(SpatialMenusFeature f, long now, boolean fresh) {
		Matrix4f projection = Projection.projection();
		INVERSE_PROJECTION.set(projection).invert();
		Minecraft mc = Minecraft.getInstance();
		double tanHalf = 1.0 / Math.max(1e-4, projection.m11());
		double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double halfH = REFERENCE_DISTANCE * tanHalf * f.size.get();
		double halfW = halfH * aspect;

		double fly = 1;
		if (f.flyIn.on()) {
			double p = Math.min(1, (now - openedAt) / (double) FLY_MS);
			fly = 1 - Math.pow(1 - p, 3);
		}
		opacity = (float) fly;
		double t = (now % 600_000L) / 1000.0;
		double lift = f.lift.get();
		double lean = f.lean.get();
		double turn = f.turn.get();
		if (f.drift.on()) {
			lift += Math.sin(t * 1.1) * 0.012;
			lean += Math.sin(t * 0.8) * 0.35;
			turn += Math.sin(t * 0.6 + 1.3) * 0.3;
		}
		if (f.followMouse.on()) {
			double mx = mc.mouseHandler.xpos() / Math.max(1, mc.getWindow().getScreenWidth()) * 2 - 1;
			double my = 1 - mc.mouseHandler.ypos() / Math.max(1, mc.getWindow().getScreenHeight()) * 2;
			tiltX += (float) ((Math.max(-1, Math.min(1, mx)) * 3.5 - tiltX) * 0.12);
			tiltY += (float) ((Math.max(-1, Math.min(1, my)) * 2.5 - tiltY) * 0.12);
			turn += tiltX;
			lean += tiltY;
		}
		double distance = f.distance.get() + (1 - fly) * 0.8;
		SpatialPanel local = SpatialPanel.place(distance, lift, halfW, halfH, lean, turn, f.bend.get());
		if (!f.anchor.on()) {
			anchorCenter = null;
			panel = local;
			return;
		}
		Matrix4f view = Projection.modelView();
		Vec3 camera = Projection.camera();
		if (fresh || anchorCenter == null) {
			// Remember where the panel sits in the world, without the fly-in offset.
			SpatialPanel rest = SpatialPanel.place(f.distance.get(), f.lift.get(), halfW, halfH, f.lean.get(), f.turn.get(), f.bend.get());
			Matrix4f toWorld = new Matrix4f(view).invert();
			Vector3f c = toWorld.transformDirection(vec(rest.center));
			anchorCenter = camera.add(c.x, c.y, c.z);
			anchorRight = toWorld.transformDirection(vec(rest.right));
			anchorUp = toWorld.transformDirection(vec(rest.up));
			anchorNormal = toWorld.transformDirection(vec(rest.normal));
		}
		Vector3f c = view.transformDirection(new Vector3f((float) (anchorCenter.x - camera.x), (float) (anchorCenter.y - camera.y),
			(float) (anchorCenter.z - camera.z)));
		Vector3f n = view.transformDirection(new Vector3f(anchorNormal));
		// The fly-in and the gentle movement still play on top of the anchored spot.
		double push = distance - f.distance.get();
		double[] center = {c.x - n.x * push, c.y - n.y * push + (lift - f.lift.get()), c.z - n.z * push};
		panel = new SpatialPanel(center, arr(view.transformDirection(new Vector3f(anchorRight))), arr(view.transformDirection(new Vector3f(anchorUp))),
			arr(n), halfW, halfH, local.radius);
	}

	private static Vector3f vec(double[] v) {
		return new Vector3f((float) v[0], (float) v[1], (float) v[2]);
	}

	private static double[] arr(Vector3f v) {
		return new double[] {v.x, v.y, v.z};
	}

	/** The target for the screen's part of the GUI: the offscreen one while floating, the window otherwise. */
	public static RenderTarget target(RenderTarget main) {
		if (!frameActive) {
			return main;
		}
		if (target == null || target.width != main.width || target.height != main.height) {
			if (target != null) {
				target.destroyBuffers();
			}
			target = new TextureTarget("Justaclient spatial menu", main.width, main.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		}
		return target;
	}

	/** Called as the GUI renderer starts drawing: empties the offscreen target for this frame's screen. */
	public static void beginDraw() {
		frame++;
		if (!frameActive) {
			return;
		}
		RenderTarget t = target(Minecraft.getInstance().gameRenderer.mainRenderTarget());
		// Transparent: JOML's no-argument Vector4f is (0, 0, 0, 1), which would leave the whole panel opaque black.
		RenderSystem.getDevice().createCommandEncoder()
			.clearColorAndDepthTextures(t.getColorTexture(), new Vector4f(0, 0, 0, 0), t.getDepthTexture(), RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE);
	}

	/** Should vanilla's blur of the world run before the screen draws? Only when asked for in the settings. */
	public static boolean vanillaBlur() {
		if (!frameActive) {
			return true;
		}
		SpatialMenusFeature f = SpatialMenusFeature.get();
		return f != null && f.blur.on();
	}

	/** Called once the GUI renderer is done: lays the menu texture over the frame on the panel. */
	public static void composite() {
		if (!frameActive) {
			return;
		}
		frameActive = false;
		SpatialPanel p = panel;
		TextureTarget t = target;
		if (p == null || t == null) {
			return;
		}
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> "Justaclient spatial", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putMat4f(INVERSE_PROJECTION)
				.putVec4(new Vector4f((float) p.center[0], (float) p.center[1], (float) p.center[2], (float) p.radius))
				.putVec4(new Vector4f((float) p.right[0], (float) p.right[1], (float) p.right[2], (float) p.halfWidth))
				.putVec4(new Vector4f((float) p.up[0], (float) p.up[1], (float) p.up[2], (float) p.halfHeight))
				.putVec4(new Vector4f((float) p.normal[0], (float) p.normal[1], (float) p.normal[2], opacity));
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		try (RenderPass pass = encoder.createRenderPass(() -> "Justaclient spatial menu", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(JacPipelines.SPATIAL));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("GuiSampler", t.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("JacSpatial", ubo.currentBuffer());
			pass.draw(3, 1, 0, 0);
		}
		ubo.rotate();
	}

	/**
	 * The GUI position the mouse points at on the floating menu, or null when no menu floats (then the plain position
	 * stands). {@code windowX}/{@code windowY} are the mouse in window pixels.
	 */
	public static double @Nullable [] mapMouse(double windowX, double windowY) {
		Minecraft mc = Minecraft.getInstance();
		SpatialPanel p = panel;
		if (p == null || panelScreen == null || mc.gui.screen() != panelScreen || frame - activeFrame > 2) {
			return null;
		}
		int w = Math.max(1, mc.getWindow().getScreenWidth());
		int h = Math.max(1, mc.getWindow().getScreenHeight());
		Vector4f ray = new Vector4f((float) (windowX / w * 2 - 1), (float) (1 - windowY / h * 2), 0.5f, 1f);
		INVERSE_PROJECTION.transform(ray);
		if (Math.abs(ray.w) < 1e-9) {
			return new double[] {-10_000, -10_000};
		}
		double[] uv = p.hit(ray.x / ray.w, ray.y / ray.w, ray.z / ray.w, true);
		if (uv == null) {
			return new double[] {-10_000, -10_000};
		}
		return SpatialPanel.panelToGui(uv[0], uv[1], mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
	}
}
