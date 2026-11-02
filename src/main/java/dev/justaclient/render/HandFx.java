package dev.justaclient.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandGlowFeature;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Draws the first-person hand into its own target instead of the main one, then lays it back over the frame through a
 * shader that adds the glow and colour style. Only the hand goes through here, so the world is never affected.
 */
public final class HandFx {
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();
	private static @Nullable TextureTarget target;
	private static @Nullable MappableRingBuffer ubo;
	private static boolean capturing;
	private static final long START = System.nanoTime();

	private HandFx() {
	}

	/** Redirects the hand's draws. Called just before vanilla draws the hand. */
	public static void begin() {
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		if (!glow.isEnabled() || Shaders.packInUse()) {
			return;
		}
		glow.cost.begin();
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		if (target == null || target.width != main.width || target.height != main.height) {
			if (target != null) {
				target.destroyBuffers();
			}
			target = new TextureTarget("Justaclient hand", main.width, main.height, true);
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
		RenderSystem.outputColorTextureOverride = target.getColorTextureView();
		RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
		capturing = true;
		glow.cost.end();
	}

	/** Restores normal output and composites the captured hand. Called right after vanilla has drawn it. */
	public static void end() {
		if (!capturing) {
			return;
		}
		capturing = false;
		RenderSystem.outputColorTextureOverride = null;
		RenderSystem.outputDepthTextureOverride = null;
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		glow.cost.begin();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> "Justaclient hand", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
		}
		int c0 = glow.colorStart();
		int c1 = glow.colorEnd();
		float scale = Minecraft.getInstance().getWindow().getGuiScale() > 0 ? (float) Minecraft.getInstance().getWindow().getGuiScale() : 2f;
		try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo.currentBuffer(), false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putVec4(r(c0), g(c0), b(c0), glow.glowStrength())
				.putVec4(r(c1), g(c1), b(c1), glow.styleAmount())
				.putVec4(glow.styleIndex(), glow.glowRadius() * scale, (System.nanoTime() - START) / 1e9f, Math.max(1f, scale * 0.75f));
		}
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		try (RenderPass pass = encoder.createRenderPass(() -> "Justaclient hand", main.getColorTextureView(), OptionalInt.empty())) {
			pass.setPipeline(JacPipelines.HAND_COMPOSITE);
			pass.bindTexture("HandSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("JacHand", ubo.currentBuffer());
			pass.draw(0, 3);
		}
		ubo.rotate();
		glow.cost.end();
	}

	private static float r(int c) {
		return ((c >> 16) & 0xFF) / 255f;
	}

	private static float g(int c) {
		return ((c >> 8) & 0xFF) / 255f;
	}

	private static float b(int c) {
		return (c & 0xFF) / 255f;
	}
}
