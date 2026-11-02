package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Restyles the vanilla hitboxes (F3 + B): your colours, an optional soft fill, and the eye line and look arrow only if
 * you want them. They still only show what F3 + B shows.
 */
public final class HitboxesFeature extends Feature {
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final NumberSetting fill = number("fill", "Fill", 0.12, 0.0, 0.4, 0.02, NumberSetting.percent());
	private final NumberSetting thickness = number("thickness", "Line width", 2.0, 1.0, 4.0, 0.5, NumberSetting.decimal(" px"));
	private final BoolSetting eyes = bool("eyes", "Eye height line", false);
	private final BoolSetting look = bool("look", "Look direction arrow", true);

	public HitboxesFeature() {
		super("hitboxes", "Hitboxes", "Restyle the F3 + B hitboxes", Category.WORLD, Icons.BOX, false);
	}

	/** Draws one entity's hitbox in our style. Returns false to let vanilla draw it. */
	public static boolean draw(Entity entity, float partialTick) {
		HitboxesFeature f = Features.get(HitboxesFeature.class);
		if (f == null || !f.isEnabled()) {
			return false;
		}
		Vec3 now = entity.getPosition(partialTick);
		Vec3 shift = now.subtract(entity.position());
		AABB box = entity.getBoundingBox().move(shift);
		int c = f.color.argb() | 0xFF000000;
		int a = (int) (f.fill.f() * 255);
		GizmoStyle style = a > 0 ? GizmoStyle.strokeAndFill(c, f.thickness.f(), Colors.withAlpha(c, a)) : GizmoStyle.stroke(c, f.thickness.f());
		Gizmos.cuboid(box, style);
		if (f.eyes.on() && entity instanceof LivingEntity) {
			double eye = box.minY + entity.getEyeHeight();
			Gizmos.cuboid(new AABB(box.minX, eye - 0.01, box.minZ, box.maxX, eye + 0.01, box.maxZ), GizmoStyle.stroke(Colors.withAlpha(0xFFFFFFFF, 0xA0)));
		}
		if (f.look.on()) {
			Vec3 eye = now.add(0, entity.getEyeHeight(), 0);
			Gizmos.arrow(eye, eye.add(entity.getViewVector(partialTick).scale(1.5)), Colors.lerp(c, 0xFFFFFFFF, 0.5f));
		}
		return true;
	}
}
