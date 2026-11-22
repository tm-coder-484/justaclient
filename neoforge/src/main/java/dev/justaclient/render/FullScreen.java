package dev.justaclient.render;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
/** Shared fullscreen pass and framebuffer copy for Minecraft 1.21.1. */
public final class FullScreen {
    public static void copy(RenderTarget from, RenderTarget to) {
        boolean clipped=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int read=GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw=GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._disableScissorTest();
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to.frameBufferId);
            GL30.glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,GL30.GL_COLOR_BUFFER_BIT,GL30.GL_NEAREST);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            if(clipped) GlStateManager._enableScissorTest();
        }
    }
    /** Binds the destination; restores the caller's GUI clip, shader and raster state. */
    public static void draw(ShaderInstance shader, RenderTarget target, boolean blend) {
        try (var state = new DrawState()) {
            GlStateManager._disableScissorTest();
            target.bindWrite(true);
            if (blend) { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); } else RenderSystem.disableBlend();
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
            RenderSystem.colorMask(true,true,true,true);
            RenderSystem.setShader(() -> shader);
            var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            b.addVertex(-1,-1,0).setUv(0,0); b.addVertex(1,-1,0).setUv(1,0);
            b.addVertex(1,1,0).setUv(1,1); b.addVertex(-1,1,0).setUv(0,1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
    }
    /** A reused offscreen target must be cleared completely, including outside GUI clips. */
    public static void clear(RenderTarget target) {
        boolean clipped=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        boolean depthMask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var colors=stack.malloc(4);
            org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK,colors);
            GlStateManager._disableScissorTest();
            RenderSystem.depthMask(true);RenderSystem.colorMask(true,true,true,true);
            try { target.clear(Minecraft.ON_OSX); }
            finally {
                RenderSystem.depthMask(depthMask);
                RenderSystem.colorMask(colors.get(0)!=0,colors.get(1)!=0,colors.get(2)!=0,colors.get(3)!=0);
                if(clipped) GlStateManager._enableScissorTest();
            }
        }
    }

    private static final class DrawState implements AutoCloseable {
        private final boolean clip=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final ShaderInstance shader=RenderSystem.getShader();
        private final boolean[] colors=new boolean[4];
        private DrawState() {
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
                var values=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,values);
                for(int i=0;i<4;i++) colors[i]=values.get(i)!=0;
            }
        }
        @Override public void close() {
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb,equationAlpha);
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);RenderSystem.colorMask(colors[0],colors[1],colors[2],colors[3]);
            if(clip) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
            RenderSystem.setShader(() -> shader);
        }
    }
}
