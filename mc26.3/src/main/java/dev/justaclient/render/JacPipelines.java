package dev.justaclient.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.justaclient.Justaclient;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/** Every render pipeline Justaclient draws with. Registered with vanilla so they compile on resource reload. */
public final class JacPipelines {
	/**
	 * Vertex layout for SDF shapes, using vanilla's attribute names and formats so it never collides with other mods.
	 * <ul>
	 *     <li>UV0: position relative to the shape's centre, in GUI pixels</li>
	 *     <li>UV1: half width and half height, times {@link #FIXED}</li>
	 *     <li>UV2: corner radius and feather (shadow softness), times {@link #FIXED}</li>
	 *     <li>LineWidth: outline thickness, 0 for a filled shape</li>
	 * </ul>
	 */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("UV2", GpuFormat.RG16_SINT)
		.addAttribute("LineWidth", GpuFormat.R32_FLOAT)
		.build();

	/** Fixed-point scale for the short attributes: quarter-pixel precision, up to 8191 px. */
	public static final int FIXED = 4;

	private static final BindGroupLayout POST_UNIFORMS = BindGroupLayout.builder()
		.withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("JacPost", UniformType.UNIFORM_BUFFER)
		.build();
	private static final BindGroupLayout HAND_UNIFORMS = BindGroupLayout.builder()
		.withUniform("HandSampler", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("HandDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("JacHand", UniformType.UNIFORM_BUFFER)
		.build();

	/** GUI drawing: the same matrices and blend as vanilla's GUI pipelines, no depth. */
	private static final RenderPipeline.Snippet GUI_BASE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.buildSnippet();

	private static final RenderPipeline.Snippet SHAPE_SNIPPET = RenderPipeline.builder(GUI_BASE)
		.withVertexShader(Justaclient.id("core/shape"))
		.withVertexBinding(0, SHAPE_FORMAT)
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
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.build()
	);

	/** Multi-channel signed distance field text. */
	public static final RenderPipeline MSDF_TEXT = RenderPipelines.register(
		RenderPipeline.builder(GUI_BASE)
			.withLocation(Justaclient.id("pipeline/msdf_text"))
			.withVertexShader(Justaclient.id("core/msdf_text"))
			.withFragmentShader(Justaclient.id("core/msdf_text"))
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withShaderDefine("PX_RANGE", 6.0f)
			.build()
	);

	/** World effects: camera matrices, depth tested against the world but never written. */
	private static final RenderPipeline.Snippet WORLD_FX_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withVertexShader(Justaclient.id("core/world_fx"))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.withCull(false)
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.buildSnippet();

	/** Soft glowing sprites, rings and ribbons in the world. Depth tested, so they never show through blocks. */
	public static final RenderPipeline WORLD_FX = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/world_fx"))
			.withFragmentShader(Justaclient.id("core/world_fx"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build()
	);

	/** The same sprites added onto the sky, for stars and shooting stars. */
	public static final RenderPipeline SKY_FX = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/sky_fx"))
			.withFragmentShader(Justaclient.id("core/world_fx"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.build()
	);

	/** Aurora curtains, added onto the sky. */
	public static final RenderPipeline AURORA = RenderPipelines.register(
		RenderPipeline.builder(WORLD_FX_SNIPPET)
			.withLocation(Justaclient.id("pipeline/aurora"))
			.withFragmentShader(Justaclient.id("core/aurora"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.build()
	);

	/** Full-screen passes: vanilla's screen quad, one sampler, no blending or depth. */
	private static final RenderPipeline.Snippet BLUR_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withVertexShader("core/screenquad")
		.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL))
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.buildSnippet();

	/** Dual Kawase downsample: halves resolution while blurring. */
	public static final RenderPipeline BLUR_DOWN = RenderPipelines.register(
		RenderPipeline.builder(BLUR_SNIPPET)
			.withLocation(Justaclient.id("pipeline/blur_down"))
			.withFragmentShader(Justaclient.id("post/kawase_down"))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.build()
	);

	/** Dual Kawase upsample: doubles resolution while blurring. */
	public static final RenderPipeline BLUR_UP = RenderPipelines.register(
		RenderPipeline.builder(BLUR_SNIPPET)
			.withLocation(Justaclient.id("pipeline/blur_up"))
			.withFragmentShader(Justaclient.id("post/kawase_up"))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.build()
	);

	/** Color grading, vignette and motion blur over the world frame (see {@link PostFx}). */
	public static final RenderPipeline POST_GRADE = RenderPipelines.register(
		RenderPipeline.builder(BLUR_SNIPPET)
			.withLocation(Justaclient.id("pipeline/post_grade"))
			.withFragmentShader(Justaclient.id("post/grade"))
			.withBindGroupLayout(POST_UNIFORMS)
			.build()
	);

	/** Lays the separately drawn first-person hand back over the frame with its glow (see {@link HandFx}). */
	public static final RenderPipeline HAND_COMPOSITE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(Justaclient.id("pipeline/hand_composite"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(Justaclient.id("post/hand"))
			.withBindGroupLayout(HAND_UNIFORMS)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build()
	);

	private JacPipelines() {
	}

	/** Forces class init so the pipelines are registered before the first resource reload. */
	public static void init() {
	}
}
