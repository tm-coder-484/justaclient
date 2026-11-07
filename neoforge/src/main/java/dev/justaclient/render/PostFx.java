package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ColorGradingFeature;
import dev.justaclient.feature.impl.MotionBlurFeature;
import dev.justaclient.feature.impl.PerformanceModeFeature;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Full-screen passes over the finished world frame: color grading, vignette and motion blur, in one shader.
 * <p>
 * Runs once per frame after the world and hand are drawn and before the GUI, so menus and the HUD stay untouched and
 * the blur behind panels sees the graded picture. Costs nothing while every pass is off.
 */
public final class PostFx {
	private static @Nullable TextureTarget scratch;
	private static @Nullable TextureTarget history;
	private static boolean historyValid;
	private static long lastNanos = -1;

	private PostFx() {
	}

	/** Called from the game renderer once the world frame is complete. */
	public static void apply() {
		ColorGradingFeature grading = Features.get(ColorGradingFeature.class);
		MotionBlurFeature motion = Features.get(MotionBlurFeature.class);
		boolean paused = PerformanceModeFeature.pauseShaders();
		boolean grade = grading.isEnabled() && !paused;
		boolean blur = motion.isEnabled() && !paused;
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 1f / 60f : Math.min(0.25f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (!grade && !blur) {
			historyValid = false;
			return;
		}
		(grade ? grading : motion).cost.begin();
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		scratch = ensure(scratch, "Justaclient post scratch", main);
		if (blur) {
			if (history == null || history.width != main.width || history.height != main.height) {
				historyValid = false;
			}
			history = ensure(history, "Justaclient motion history", main);
		}
        FullScreen.copy(main,scratch);
        int vignette=grading.vignetteColor();
        var shader=JacPipelines.POST_GRADE;
        shader.safeGetUniform("Grade").set(grading.exposure(),grading.contrast(),grading.saturation(),grading.vibrance());
        shader.safeGetUniform("Balance").set(grading.temperature(),grading.tint(),grading.vignetteStrength(),grading.vignetteSize());
        shader.safeGetUniform("Motion").set(blur?motion.blend(dt):0f,blur&&historyValid?1f:0f,grade?1f:0f,grade?1f:0f);
        shader.safeGetUniform("VignetteColor").set(((vignette>>16)&255)/255f,((vignette>>8)&255)/255f,(vignette&255)/255f,Colors.alpha(vignette)/255f);
        shader.setSampler("InSampler",scratch.getColorTextureId());
        shader.setSampler("HistorySampler",(blur?history:scratch).getColorTextureId());
        FullScreen.draw(shader,main,false);
		if (blur) {
			FullScreen.copy(main,history);
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
		return new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
	}
}
