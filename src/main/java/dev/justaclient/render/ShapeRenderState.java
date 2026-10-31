package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One SDF rounded rectangle in the GUI render state. The quad is grown by {@code pad} on every side so the shader has room
 * for anti-aliasing and shadow falloff. Colours are per corner (top-left, top-right, bottom-right, bottom-left).
 */
public record ShapeRenderState(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2fc pose,
	float x,
	float y,
	float width,
	float height,
	float radius,
	float feather,
	float lineWidth,
	int colorTopLeft,
	int colorTopRight,
	int colorBottomRight,
	int colorBottomLeft,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	public static ShapeRenderState create(
		RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose,
		float x, float y, float width, float height, float radius, float feather, float lineWidth,
		int tl, int tr, int br, int bl, @Nullable ScreenRectangle scissor
	) {
		float pad = feather + 1f;
		ScreenRectangle area = new ScreenRectangle(
			(int) Math.floor(x - pad), (int) Math.floor(y - pad),
			(int) Math.ceil(width + pad * 2) + 1, (int) Math.ceil(height + pad * 2) + 1
		).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
		return new ShapeRenderState(pipeline, textureSetup, pose, x, y, width, height, radius, feather, lineWidth, tl, tr, br, bl, scissor, bounds);
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		float pad = feather + 1f;
		float hw = width / 2f;
		float hh = height / 2f;
		float cx = x + hw;
		float cy = y + hh;
		int sx = Math.round(hw * JacPipelines.FIXED);
		int sy = Math.round(hh * JacPipelines.FIXED);
		int r = Math.round(Math.min(radius, Math.min(hw, hh)) * JacPipelines.FIXED);
		int f = Math.round(feather * JacPipelines.FIXED);
		float lx = hw + pad;
		float ly = hh + pad;
		vertex(consumer, cx - lx, cy - ly, -lx, -ly, colorTopLeft, sx, sy, r, f);
		vertex(consumer, cx - lx, cy + ly, -lx, ly, colorBottomLeft, sx, sy, r, f);
		vertex(consumer, cx + lx, cy + ly, lx, ly, colorBottomRight, sx, sy, r, f);
		vertex(consumer, cx + lx, cy - ly, lx, -ly, colorTopRight, sx, sy, r, f);
	}

	private void vertex(VertexConsumer consumer, float px, float py, float lx, float ly, int color, int sx, int sy, int r, int f) {
		consumer.addVertexWith2DPose(pose, px, py)
			.setColor(color)
			.setUv(lx, ly)
			.setUv1(sx, sy)
			.setUv2(r, f)
			.setLineWidth(lineWidth);
	}
}
