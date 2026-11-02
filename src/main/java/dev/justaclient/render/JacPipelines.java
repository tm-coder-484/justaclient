package dev.justaclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.justaclient.Justaclient;
import net.minecraft.client.renderer.RenderPipelines;

/** Every render pipeline Justaclient draws with. Registered with vanilla so they compile on resource reload. */
public final class JacPipelines {
	/**
	 * Vertex layout for SDF shapes, built only from vanilla elements so it never collides with other mods.
	 * <ul>
	 *     <li>UV0: position relative to the shape's centre, in GUI pixels</li>
	 *     <li>UV1: half width and half height, times {@link #FIXED}</li>
	 *     <li>UV2: corner radius and feather (shadow softness), times {@link #FIXED}</li>
	 *     <li>LineWidth: outline thickness, 0 for a filled shape</li>
	 * </ul>
	 */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
		.add("Position", VertexFormatElement.POSITION)
		.add("Color", VertexFormatElement.COLOR)
		.add("UV0", VertexFormatElement.UV0)
		.add("UV1", VertexFormatElement.UV1)
		.add("UV2", VertexFormatElement.UV2)
		.add("LineWidth", VertexFormatElement.LINE_WIDTH)
		.build();

	/** Fixed-point scale for the short attributes: quarter-pixel precision, up to 8191 px. */
	public static final int FIXED = 4;

	private static final RenderPipeline.Snippet SHAPE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withVertexShader(Justaclient.id("core/shape"))
		.withBlend(BlendFunction.TRANSLUCENT)
		.withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withCull(false)
		.buildSnippet();

	/** Filled, outlined, gradient and shadow rounded rectangles. */
	public static final RenderPipeline SHAPE = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET)
			.withLocation(Justaclient.id("pipeline/shape"))
			.withFragmentShader(Justaclient.id("core/shape"))
			.build()
	);

	/** Ring segments for timers, dials and gauges. */
	public static final RenderPipeline ARC = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET)
			.withLocation(Justaclient.id("pipeline/arc"))
			.withFragmentShader(Justaclient.id("core/arc"))
			.build()
	);

	/** A rounded rectangle that shows the blurred frame behind it, tinted by the vertex colour. */
	public static final RenderPipeline GLASS = RenderPipelines.register(
		RenderPipeline.builder(SHAPE_SNIPPET)
			.withLocation(Justaclient.id("pipeline/glass"))
			.withFragmentShader(Justaclient.id("core/glass"))
			.withSampler("Sampler0")
			.withUniform("Globals", UniformType.UNIFORM_BUFFER)
			.build()
	);

	/** Multi-channel signed distance field text. */
	public static final RenderPipeline MSDF_TEXT = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Justaclient.id("pipeline/msdf_text"))
			.withVertexShader(Justaclient.id("core/msdf_text"))
			.withFragmentShader(Justaclient.id("core/msdf_text"))
			.withSampler("Sampler0")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withShaderDefine("PX_RANGE", 6.0f)
			.build()
	);

	private static final RenderPipeline.Snippet WORLD_FX_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withVertexShader(Justaclient.id("core/world_fx"))
		.withDepthWrite(false)
		.withCull(false)
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
		.buildSnippet();

	/** Soft glowing sprites, rings and ribbons in the world. Depth tested, so they never show through blocks. */
	public static final RenderPipeline WORLD_FX = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/world_fx"))
			.withFragmentShader(Justaclient.id("core/world_fx"))
			.withBlend(BlendFunction.TRANSLUCENT)
			.build()
	);

	/** The same sprites added onto the sky, for stars and shooting stars. */
	public static final RenderPipeline SKY_FX = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/sky_fx"))
			.withFragmentShader(Justaclient.id("core/world_fx"))
			.withBlend(BlendFunction.LIGHTNING)
			.build()
	);

	/** Aurora curtains, added onto the sky. */
	public static final RenderPipeline AURORA = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/aurora"))
			.withFragmentShader(Justaclient.id("core/aurora"))
			.withBlend(BlendFunction.LIGHTNING)
			.build()
	);

	private static final RenderPipeline.Snippet BLUR_SNIPPET = RenderPipeline.builder()
		.withVertexShader("core/screenquad")
		.withSampler("InSampler")
		.withoutBlend()
		.withDepthWrite(false)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
		.buildSnippet();

	/** Dual Kawase downsample: halves resolution while blurring. */
	public static final RenderPipeline BLUR_DOWN = RenderPipelines.register(
		RenderPipeline.builder(BLUR_SNIPPET)
			.withLocation(Justaclient.id("pipeline/blur_down"))
			.withFragmentShader(Justaclient.id("post/kawase_down"))
			.build()
	);

	/** Dual Kawase upsample: doubles resolution while blurring. */
	public static final RenderPipeline BLUR_UP = RenderPipelines.register(
		RenderPipeline.builder(BLUR_SNIPPET)
			.withLocation(Justaclient.id("pipeline/blur_up"))
			.withFragmentShader(Justaclient.id("post/kawase_up"))
			.build()
	);

	private JacPipelines() {
	}

	/** Forces class init so the pipelines are registered before the first resource reload. */
	public static void init() {
	}
}
