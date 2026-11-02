package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.PerformanceModeFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.jspecify.annotations.Nullable;

/**
 * Dual Kawase blur of the frame behind the GUI.
 * <p>
 * Glass panels are submitted during GUI extraction and point at {@link #output()}. Just before the GUI draws, the mixin on
 * {@code GuiRenderer} calls {@link #capture()}, which blurs the main target (world or title panorama) into that texture.
 * The work only happens on frames where something asked for glass.
 */
public final class BlurEngine {
	public static final int MAX_PASSES = 5;

	private static final List<TextureTarget> down = new ArrayList<>();
	private static final List<TextureTarget> up = new ArrayList<>();
	private static int width = -1;
	private static int height = -1;
	private static boolean requested;
	private static int idleFrames;

	private BlurEngine() {
	}

	/** Called by glass panels while the GUI is being extracted. Sizes the targets so the texture view stays valid this frame. */
	public static TextureSetup request() {
		requested = true;
		ensureTargets();
		return TextureSetup.singleTexture(output(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}

	/** Blurs the main render target if a glass panel was submitted this frame. Runs on the render thread. */
	public static void capture() {
		if (!requested) {
			// Performance mode: give the blur targets back after ten seconds without glass.
			if (++idleFrames > 600 && !down.isEmpty() && PerformanceModeFeature.freeMemory()) {
				close();
				width = -1;
				height = -1;
			}
			return;
		}
		idleFrames = 0;
		requested = false;
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (main.width != width || main.height != height) {
			// Window resized mid-frame; panels already point at the old view, so skip one frame rather than draw garbage.
			return;
		}
		int passes = passes();
		GpuTextureView source = main.getColorTextureView();
		for (int i = 0; i < passes; i++) {
			TextureTarget target = down.get(i);
			blit(JacPipelines.BLUR_DOWN, source, target);
			source = target.getColorTextureView();
		}
		// Walk back up to half resolution; the last up pass always lands in up[0].
		for (int i = passes - 2; i >= 0; i--) {
			TextureTarget target = up.get(i);
			blit(JacPipelines.BLUR_UP, source, target);
			source = target.getColorTextureView();
		}
		if (passes == 1) {
			// One pass: copy the half-res result into the shared output so the view never changes.
			blit(JacPipelines.BLUR_UP, source, up.get(0));
		}
	}

	/** The blurred frame at half resolution. Stable for the lifetime of the current window size. */
	public static @Nullable GpuTextureView output() {
		return up.isEmpty() ? null : up.get(0).getColorTextureView();
	}

	private static int passes() {
		return Math.max(1, Math.min(Math.min(MAX_PASSES, PerformanceModeFeature.maxBlurPasses()), JacConfig.get().style.blurStrength));
	}

	private static void ensureTargets() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (main.width == width && main.height == height && !down.isEmpty()) {
			return;
		}
		close();
		width = main.width;
		height = main.height;
		for (int i = 0; i < MAX_PASSES; i++) {
			int w = Math.max(1, width >> (i + 1));
			int h = Math.max(1, height >> (i + 1));
			down.add(new TextureTarget("Justaclient blur down " + i, w, h, GpuFormat.RGBA8_UNORM, null));
			up.add(new TextureTarget("Justaclient blur up " + i, w, h, GpuFormat.RGBA8_UNORM, null));
		}
	}

	private static void close() {
		down.forEach(RenderTarget::destroyBuffers);
		up.forEach(RenderTarget::destroyBuffers);
		down.clear();
		up.clear();
	}

	private static void blit(RenderPipeline pipeline, GpuTextureView source, RenderTarget target) {
		try (RenderPass pass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(() -> "Justaclient blur", target.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.draw(3, 1, 0, 0);
		}
	}
}
