package dev.justaclient.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.justaclient.feature.impl.LightColorFeature;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

/** Feeds the Light colour tint to the lightmap pass. */
public final class LightTint {
	private static final int SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
	private static @Nullable MappableRingBuffer ubo;
	private static boolean active;

	private LightTint() {
	}

	/** Called before the lightmap pass opens: writes this frame's tint, or notes that none is needed. */
	public static void prepare() {
		LightColorFeature f = LightColorFeature.get();
		float[] block = f.blockTint();
		float[] sky = f.skyTint();
		active = block[3] > 0f || sky[3] > 0f;
		if (!active) {
			return;
		}
		if (ubo == null) {
			ubo = new MappableRingBuffer(() -> "Justaclient light tint", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, SIZE);
		}
		ubo.rotate();
		try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(ubo.currentBuffer(), false, true)) {
			Std140Builder.intoBuffer(view.data()).putVec4(block[0], block[1], block[2], block[3]).putVec4(sky[0], sky[1], sky[2], sky[3]);
		}
	}

	/** Swaps vanilla's lightmap pipeline for the tinted one inside the open pass. */
	public static void apply(RenderPass pass) {
		if (active && ubo != null) {
			pass.setPipeline(JacPipelines.LIGHTMAP);
			pass.setUniform("JacLight", ubo.currentBuffer());
		}
	}
}
