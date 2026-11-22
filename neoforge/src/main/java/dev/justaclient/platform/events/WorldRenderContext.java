package dev.justaclient.platform.events;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
public record WorldRenderContext(RenderLevelStageEvent event) {
    public PoseStack matrices() { return event.getPoseStack(); }
    public MultiBufferSource.BufferSource consumers() { return Minecraft.getInstance().renderBuffers().bufferSource(); }
    public WorldState worldState() { return new WorldState(event); }
    public static final class WorldState {
        public final CameraState cameraRenderState;
        public final BlockOutlineRenderState blockOutlineRenderState;
        WorldState(RenderLevelStageEvent event) {
            cameraRenderState=new CameraState(event.getCamera().getPosition(),new Quaternionf(event.getCamera().rotation()));
            var mc=Minecraft.getInstance();
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK && mc.level!=null) {
                var pos=hit.getBlockPos();
                blockOutlineRenderState=new BlockOutlineRenderState(pos,mc.level.getBlockState(pos).getShape(mc.level,pos,CollisionContext.of(event.getCamera().getEntity())));
            } else blockOutlineRenderState=null;
        }
    }
    public static final class CameraState {
        public final Vec3 pos;
        public final Quaternionf orientation;
        CameraState(Vec3 pos,Quaternionf orientation) { this.pos=pos;this.orientation=orientation; }
    }
}
