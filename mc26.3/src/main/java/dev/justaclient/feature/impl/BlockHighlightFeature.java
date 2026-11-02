package dev.justaclient.feature.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.FrameCost;
import dev.justaclient.mixin.MultiPlayerGameModeAccessor;
import dev.justaclient.render.Icons;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the block outline with a box that glides between blocks, with an optional fill and mining progress. It is
 * depth tested like vanilla's outline, so it never shows through other blocks.
 */
public final class BlockHighlightFeature extends Feature {
	private final ColorSetting outlineColor = color("outline_color", "Outline colour", ColorSetting.ACCENT);
	private final NumberSetting outlineWidth = number("outline_width", "Outline width", 2.5, 1, 6, 0.5, NumberSetting.decimal(" px"));
	private final BoolSetting fill = bool("fill", "Fill", true);
	private final ColorSetting fillColor = color("fill_color", "Fill colour", ColorSetting.ACCENT).visibleWhen(fill::on);
	private final NumberSetting fillOpacity = number("fill_opacity", "Fill opacity", 0.15, 0.02, 0.6, 0.01, NumberSetting.percent())
		.visibleWhen(fill::on);
	private final BoolSetting smooth = bool("smooth", "Glide between blocks", true);
	private final BoolSetting progress = bool("progress", "Show mining progress", true);
	private @Nullable AABB shown;
	private float visibility;
	private long lastNanos = -1;

	public BlockHighlightFeature() {
		super("block_highlight", "Block highlight", "Smooth outline and fill on the block you look at", Category.WORLD, Icons.SQUARE_DASHED, true);
	}

	public static void init() {
		BlockHighlightFeature feature = Features.get(BlockHighlightFeature.class);
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> !feature.isEnabled());
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (feature.isEnabled()) {
				FrameCost.time(feature.cost, () -> feature.render(context));
			}
		});
	}

	private void render(LevelRenderContext context) {
		long now = System.nanoTime();
		float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		BlockOutlineRenderState state = context.levelState().blockOutlineRenderState;
		AABB target = state == null || state.shape().isEmpty() ? null : state.shape().bounds().move(state.pos());
		if (target != null) {
			if (shown == null || !smooth.on() || shown.getCenter().distanceToSqr(target.getCenter()) > 64) {
				shown = target;
			} else {
				shown = lerp(shown, target, 1f - (float) Math.exp(-dt * 22f));
			}
		}
		visibility = target != null ? Math.min(1f, visibility + dt * 10f) : Math.max(0f, visibility - dt * 8f);
		if (shown == null || visibility <= 0f) {
			if (visibility <= 0f) {
				shown = null;
			}
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		AABB box = shown.move(-cam.x, -cam.y, -cam.z).inflate(0.002);
		PoseStack pose = context.poseStack();
		SubmitNodeCollector out = context.submitNodeCollector();
		if (fill.on()) {
			int color = Colors.fade(Colors.withAlpha(fillColor.argb(), Math.round(fillOpacity.f() * 255)), visibility);
			out.submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, v) -> filledBox(p, v, box, color));
		}
		float mined = miningProgress();
		if (mined > 0f && target != null) {
			// The inner box grows from the centre as the block breaks.
			AABB inner = box.deflate(box.getXsize() * 0.5 * (1 - mined), box.getYsize() * 0.5 * (1 - mined), box.getZsize() * 0.5 * (1 - mined));
			int color = Colors.withAlpha(outlineColor.argb(), 0x60);
			out.submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, v) -> filledBox(p, v, inner, color));
		}
		int line = Colors.fade(outlineColor.argb(), visibility);
		float width = outlineWidth.f() * Minecraft.getInstance().getWindow().getAppropriateLineWidth() / 2.5f;
		out.submitCustomGeometry(pose, RenderTypes.lines(), (p, v) -> lineBox(p, v, box, line, width));
	}

	private float miningProgress() {
		Minecraft mc = Minecraft.getInstance();
		if (!progress.on() || mc.gameMode == null || !mc.gameMode.isDestroying()) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, ((MultiPlayerGameModeAccessor) mc.gameMode).justaclient$destroyProgress()));
	}

	private static AABB lerp(AABB a, AABB b, float t) {
		return new AABB(
			a.minX + (b.minX - a.minX) * t, a.minY + (b.minY - a.minY) * t, a.minZ + (b.minZ - a.minZ) * t,
			a.maxX + (b.maxX - a.maxX) * t, a.maxY + (b.maxY - a.maxY) * t, a.maxZ + (b.maxZ - a.maxZ) * t
		);
	}

	private static void filledBox(PoseStack.Pose p, VertexConsumer v, AABB b, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		quad(v, p, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(v, p, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(v, p, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(v, p, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(v, p, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(v, p, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(VertexConsumer v, PoseStack.Pose p, int color, float... c) {
		for (int i = 0; i < 12; i += 3) {
			v.addVertex(p, c[i], c[i + 1], c[i + 2]).setColor(color);
		}
	}

	private static void lineBox(PoseStack.Pose p, VertexConsumer v, AABB b, int color, float width) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		float[][] edges = {
			{x0, y0, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z0, x1, y1, z0}, {x0, y1, z1, x1, y1, z1},
			{x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
			{x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}
		};
		for (float[] e : edges) {
			float nx = e[3] - e[0], ny = e[4] - e[1], nz = e[5] - e[2];
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-6f) {
				continue;
			}
			nx /= len;
			ny /= len;
			nz /= len;
			v.addVertex(p, e[0], e[1], e[2]).setColor(color).setNormal(p, nx, ny, nz).setLineWidth(width);
			v.addVertex(p, e[3], e[4], e[5]).setColor(color).setNormal(p, nx, ny, nz).setLineWidth(width);
		}
	}
}
