package dev.justaclient.render.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.justaclient.render.JacPipelines;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** One run of MSDF text: a quad per glyph, all from the same atlas so the run batches into a single draw. */
public record MsdfTextRenderState(
	MsdfFont font,
	String text,
	TextureSetup textureSetup,
	Matrix3x2fc pose,
	float x,
	float y,
	float scale,
	int color,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public RenderPipeline pipeline() {
		return JacPipelines.MSDF_TEXT;
	}

	@Override
	public void buildVertices(VertexConsumer consumer) {
		float penX = x;
		int previous = -1;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (previous >= 0) {
				penX += font.kerning(previous, cp) * scale;
			}
			MsdfFont.Glyph g = font.glyph(cp);
			if (g.width() > 0 && g.height() > 0) {
				float x0 = penX + g.xOffset() * scale;
				float y0 = y + g.yOffset() * scale;
				float x1 = x0 + g.width() * scale;
				float y1 = y0 + g.height() * scale;
				float u0 = g.x() / font.atlasWidth;
				float v0 = g.y() / font.atlasHeight;
				float u1 = (g.x() + g.width()) / font.atlasWidth;
				float v1 = (g.y() + g.height()) / font.atlasHeight;
				consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
				consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
				consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
			}
			penX += g.advance() * scale;
			previous = cp;
		}
	}
}
