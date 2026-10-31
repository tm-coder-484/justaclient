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
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ColorGradingFeature;
import dev.justaclient.feature.impl.MotionBlurFeature;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Full-screen passes over the finished world frame: color grading, vignette and motion blur, in one shader.
 * <p>
 * Runs once per frame after the world and hand are drawn and before the GUI, so menus and the HUD stay untouched and
 * the blur behind panels sees the graded picture. Costs nothing while every pass is off.
 */
public final class PostFx {
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
	private static @Nullable TextureTarget scratch;
	private static @Nullable TextureTarget history;
	private static @Nullable MappableRingBuffer ubo;
	private static boolean historyValid;
	private static long lastNanos = -1;

	private PostFx() {
	}

	/** Called from the game renderer once the world frame is complete. */
	public static void apply() {
		ColorGradingFeature grading = Features.get(ColorGradingFeature.class);
		MotionBlurFeature motion = Features.get(MotionBlurFeature.class);
		boolean grade = grading.isEnabled();
		boolean blur = motion.isEnabled();
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 1f / 60f : Math.min(0.25f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (!grade && !blur) {
			historyValid = false;
			return;
		}
		(grade ? grading : motion).cost.begin();
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		scratch = ensure(scratch, "Justaclient post scratch", main);
		if (blur) {
			if (history == null || history.width != main.width || history.height != main.height) {
				historyValid = false;
			}
			history = ensure(history, "Justaclient motion history", main);
		}
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> "Justaclient post", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(main.getColorTexture(), scratch.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);

		int vignette = grading.vignetteColor();
		try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putVec4(grading.exposure(), grading.contrast(), grading.saturation(), grading.vibrance())
				.putVec4(grading.temperature(), grading.tint(), grading.vignetteStrength(), grading.vignetteSize())
				.putVec4(blur ? motion.blend(dt) : 0f, blur && historyValid ? 1f : 0f, grade ? 1f : 0f, grade ? 1f : 0f)
				.putVec4(((vignette >> 16) & 0xFF) / 255f, ((vignette >> 8) & 0xFF) / 255f, (vignette & 0xFF) / 255f, Colors.alpha(vignette) / 255f);
		}
		try (RenderPass pass = encoder.createRenderPass(() -> "Justaclient post", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(JacPipelines.POST_GRADE));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("InSampler", scratch.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			TextureTarget past = blur ? history : scratch;
			pass.setUniform("HistorySampler", past.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setUniform("JacPost", ubo.currentBuffer());
			pass.draw(3, 1, 0, 0);
		}
		ubo.rotate();
		if (blur) {
			encoder.copyTextureToTexture(main.getColorTexture(), history.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			historyValid = true;
		}
		(grade ? grading : motion).cost.end();
	}

	/** Forgets the last frame, e.g. after a teleport or world change, so motion blur does not smear across it. */
	public static void resetHistory() {
		historyValid = false;
	}

	private static TextureTarget ensure(@Nullable TextureTarget target, String name, RenderTarget main) {
		if (target != null && target.width == main.width && target.height == main.height) {
			return target;
		}
		if (target != null) {
			target.destroyBuffers();
		}
		return new TextureTarget(name, main.width, main.height, GpuFormat.RGBA8_UNORM, null);
	}
}
