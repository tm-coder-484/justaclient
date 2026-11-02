package dev.justaclient.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HandGlowFeature;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the first-person hand into its own target instead of the main one (by swapping the targets of vanilla's
 * "Item in hand" render pass), then lays it back over the frame through a
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

	/** Starts capturing the hand if the glow is on. Called as vanilla opens the hand's render pass; returns whether to redirect it. */
	public static boolean begin() {
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		if (!glow.isEnabled() || Shaders.packInUse()) {
			return false;
		}
		glow.cost.begin();
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (target == null || target.width != main.width || target.height != main.height) {
			if (target != null) {
				target.destroyBuffers();
			}
			target = new TextureTarget("Justaclient hand", main.width, main.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
		}
		RenderSystem.getDevice().createCommandEncoder()
			.clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(), target.getDepthTexture(), RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE);
		capturing = true;
		glow.cost.end();
		return true;
	}

	/** The hand's colour target while capturing. */
	public static GpuTextureView color() {
		return target.getColorTextureView();
	}

	/** The hand's depth target while capturing. */
	public static GpuTextureView depth() {
		return target.getDepthTextureView();
	}

	/** Restores normal output and composites the captured hand. Called right after vanilla has drawn it. */
	public static void end() {
		if (!capturing) {
			return;
		}
		capturing = false;
		HandGlowFeature glow = Features.get(HandGlowFeature.class);
		glow.cost.begin();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> "Justaclient hand", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
		}
		int c0 = glow.colorStart();
		int c1 = glow.colorEnd();
		float scale = Minecraft.getInstance().getWindow().getGuiScale() > 0 ? (float) Minecraft.getInstance().getWindow().getGuiScale() : 2f;
		try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putVec4(r(c0), g(c0), b(c0), glow.glowStrength())
				.putVec4(r(c1), g(c1), b(c1), glow.styleAmount())
				.putVec4(glow.styleIndex(), glow.glowRadius() * scale, (System.nanoTime() - START) / 1e9f, Math.max(1f, scale * 0.75f));
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		try (RenderPass pass = encoder.createRenderPass(() -> "Justaclient hand", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(JacPipelines.HAND_COMPOSITE));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("HandSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("HandDepthSampler", target.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setUniform("JacHand", ubo.currentBuffer());
			pass.draw(3, 1, 0, 0);
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
