package dev.justaclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * A ring segment drawn by the arc shader. It shares the shape vertex format: UV1 holds the outer radius, LineWidth the
 * ring thickness, and UV2 the start and sweep as fractions of a turn times {@link #ANGLE_FIXED}.
 */
public record ArcRenderState(
	Matrix3x2fc pose,
	float cx,
	float cy,
	float r,
	float thickness,
	float start,
	float sweep,
	int color,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	/** Must match ANGLE_FIXED in arc.fsh. */
	public static final int ANGLE_FIXED = 8000;

	public static ArcRenderState create(Matrix3x2fc pose, float cx, float cy, float r, float thickness, float start, float sweep, int color,
		@Nullable ScreenRectangle scissor) {
		ScreenRectangle area = new ScreenRectangle(
			(int) Math.floor(cx - r - 1), (int) Math.floor(cy - r - 1), (int) Math.ceil(r * 2 + 2) + 1, (int) Math.ceil(r * 2 + 2) + 1
		).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
		return new ArcRenderState(pose, cx, cy, r, thickness, start, Math.min(1f, sweep), color, scissor, bounds);
	}

	@Override
	public RenderPipeline pipeline() {
		return JacPipelines.ARC;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		float l = r + 1f;
		int size = Math.round(r * JacPipelines.FIXED);
		int a = Math.round((start - (float) Math.floor(start)) * ANGLE_FIXED);
		int b = Math.round(sweep * ANGLE_FIXED);
		vertex(consumer, -l, -l, size, a, b);
		vertex(consumer, -l, l, size, a, b);
		vertex(consumer, l, l, size, a, b);
		vertex(consumer, l, -l, size, a, b);
	}

	private void vertex(VertexConsumer consumer, float lx, float ly, int size, int a, int b) {
		consumer.addVertexWith2DPose(pose, cx + lx, cy + ly)
			.setColor(color)
			.setUv(lx, ly)
			.setUv1(size, size)
			.setUv2(a, b)
			.setLineWidth(thickness);
	}
}
