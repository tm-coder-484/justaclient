package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;

/**
 * The shape kit. All coordinates are GUI pixels and accept fractions; everything is anti-aliased and follows the current
 * pose, so shapes can be scaled and moved by animations.
 */
public final class Draw {
	private Draw() {
	}

	public static void rect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
		gradient(g, x, y, w, h, radius, color, color, color, color);
	}

	/** Four-corner gradient: top-left, top-right, bottom-right, bottom-left. */
	public static void gradient(GuiGraphics g, float x, float y, float w, float h, float radius, int tl, int tr, int br, int bl) {
		submit(g, JacPipelines.SHAPE, TextureSetup.noTexture(), x, y, w, h, radius, 0f, 0f, tl, tr, br, bl);
	}

	public static void horizontalGradient(GuiGraphics g, float x, float y, float w, float h, float radius, int left, int right) {
		gradient(g, x, y, w, h, radius, left, right, right, left);
	}

	public static void verticalGradient(GuiGraphics g, float x, float y, float w, float h, float radius, int top, int bottom) {
		gradient(g, x, y, w, h, radius, top, top, bottom, bottom);
	}

	/** An inside stroke of {@code thickness} pixels along the rounded edge. */
	public static void outline(GuiGraphics g, float x, float y, float w, float h, float radius, float thickness, int color) {
		submit(g, JacPipelines.SHAPE, TextureSetup.noTexture(), x, y, w, h, radius, 0f, Math.max(0.01f, thickness), color, color, color, color);
	}

	/** Soft drop shadow or glow that fades out over {@code softness} pixels around the rectangle. */
	public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, float softness, int color) {
		submit(g, JacPipelines.SHAPE, TextureSetup.noTexture(), x, y, w, h, radius, Math.max(0.5f, softness), 0f, color, color, color, color);
	}

	public static void circle(GuiGraphics g, float cx, float cy, float r, int color) {
		rect(g, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	/**
	 * Frosted glass: the blurred frame behind the rectangle, tinted with {@code tint} (its alpha is how much tint covers the
	 * blur), drawn at {@code opacity}. With blur turned off this is a plain translucent rectangle.
	 */
	public static void glass(GuiGraphics g, float x, float y, float w, float h, float radius, int tint, float opacity) {
		if (opacity <= 0f) {
			return;
		}
		if (!JacConfig.get().style.blur) {
			rect(g, x, y, w, h, radius, Colors.fade(tint, opacity));
			return;
		}
		// The glass shader reads overall opacity from the line width slot, which it has no other use for.
		int opaque = tint | 0x01000000;
		submit(g, JacPipelines.GLASS, BlurEngine.request(), x, y, w, h, radius, 0f, Math.min(1f, opacity), opaque, opaque, opaque, opaque);
	}

	/**
	 * A complete panel in the current style: shadow, glass or solid surface, and the outline token. {@code alpha} fades the
	 * whole panel for open and close animations.
	 */
	public static void panel(GuiGraphics g, float x, float y, float w, float h, float alpha) {
		StyleTokens style = JacConfig.get().style;
		float radius = style.cornerRadius;
		int accent = JacConfig.accent();
		// Submission order is layer order for overlapping shapes, so everything behind the glass goes first.
		shadow(g, x, y + 2, w, h, radius, 14f, Colors.fade(0x66000000, alpha));
		if (style.outline == StyleTokens.Outline.GLOW) {
			shadow(g, x, y, w, h, radius, 8f, Colors.fade(Colors.withAlpha(accent, 0x70), alpha));
		}
		glass(g, x, y, w, h, radius, JacConfig.surface(), alpha);
		if (style.outline == StyleTokens.Outline.OFF) {
			// Hairline highlight keeps the edge readable over bright backgrounds.
			outline(g, x, y, w, h, radius, 1f, Colors.fade(0x14FFFFFF, alpha));
		} else {
			int edge = style.outline == StyleTokens.Outline.GLOW ? 0xD0 : 0xB0;
			outline(g, x, y, w, h, radius, 1f, Colors.fade(Colors.withAlpha(accent, edge), alpha));
		}
	}

	private static void submit(
		GuiGraphics g, RenderPipeline pipeline, TextureSetup textures,
		float x, float y, float w, float h, float radius, float feather, float lineWidth, int tl, int tr, int br, int bl
	) {
		if (w <= 0 || h <= 0 || ((tl | tr | br | bl) >>> 24) == 0) {
			return;
		}
		g.guiRenderState.submitGuiElement(ShapeRenderState.create(
			pipeline, textures, new Matrix3x2f(g.pose()), x, y, w, h, radius, feather, lineWidth, tl, tr, br, bl, g.scissorStack.peek()
		));
	}
}
