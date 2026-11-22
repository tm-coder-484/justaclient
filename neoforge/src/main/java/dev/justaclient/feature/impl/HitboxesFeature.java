package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;


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
    public static boolean draw(com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vanilla,Entity entity,float partialTick) {
        var f=Features.get(HitboxesFeature.class);if(f==null || !f.isEnabled())return false;
        var buffers=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        AABB box=entity.getBoundingBox().move(-entity.getX(),-entity.getY(),-entity.getZ());
        int color=f.color.argb()|0xff000000;
        if(f.fill.f()>0) BlockHighlightFeature.filledBox(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugFilledBox()),box,Colors.withAlpha(color,(int)(255*f.fill.f())));
        var lines=buffers.getBuffer(dev.justaclient.render.JacRenderTypes.lines(f.thickness.f()));
        BlockHighlightFeature.lineBox(pose,lines,box,color,f.thickness.f());
        if(f.eyes.on() && entity instanceof LivingEntity) {
            double eye=entity.getEyeHeight();
            BlockHighlightFeature.lineBox(pose,lines,new AABB(box.minX,eye-.01,box.minZ,box.maxX,eye+.01,box.maxZ),0xa0ffffff,f.thickness.f());
        }
        if(f.look.on()) {
            Vec3 direction=entity.getViewVector(partialTick).scale(1.5);
            var p=pose.last();float eye=entity.getEyeHeight();int c=Colors.lerp(color,0xffffffff,.5f);
            var normal=new org.joml.Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize();
            lines.addVertex(p,0,eye,0).setColor(c).setNormal(p,normal.x,normal.y,normal.z);
            lines.addVertex(p,(float)direction.x,eye+(float)direction.y,(float)direction.z).setColor(c).setNormal(p,normal.x,normal.y,normal.z);
        }
        return true;
    }
}
