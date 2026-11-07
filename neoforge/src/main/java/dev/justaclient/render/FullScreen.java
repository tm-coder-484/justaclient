package dev.justaclient.render;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL30;
/** Shared fullscreen pass and framebuffer copy for Minecraft 1.21.1. */
public final class FullScreen {
    public static void copy(RenderTarget from, RenderTarget to) {
        int read=GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw=GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to.frameBufferId);
        GL30.glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
    }
    public static void draw(ShaderInstance shader, RenderTarget target, boolean blend) {
        target.bindWrite(true);
        if (blend) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); } else RenderSystem.disableBlend();
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);
        var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-1,-1,0).setUv(0,0); b.addVertex(1,-1,0).setUv(1,0);
        b.addVertex(1,1,0).setUv(1,1); b.addVertex(-1,1,0).setUv(0,1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true); RenderSystem.enableDepthTest(); RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
