package dev.justaclient.render;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public final class LegacyImages {
    public static int abgr(int argb) { return argb & 0xff00ff00 | (argb>>16 & 255) | (argb&255)<<16; }
    public static void pixel(NativeImage image,int x,int y,int argb) { image.setPixelRGBA(x,y,abgr(argb)); }
    public static void blit(GuiGraphics g,ResourceLocation id,int x,int y,float u,float v,int w,int h,int tw,int th) { blit(g,id,x,y,u,v,w,h,w,h,tw,th,-1); }
    public static void blit(GuiGraphics g,ResourceLocation id,int x,int y,float u,float v,int w,int h,int tw,int th,int color) { blit(g,id,x,y,u,v,w,h,w,h,tw,th,color); }
    public static void blit(GuiGraphics g,ResourceLocation id,int x,int y,float u,float v,int w,int h,int sw,int sh,int tw,int th,int color) {
        g.flush(); tint(color);g.blit(id,x,y,w,h,u,v,sw,sh,tw,th);g.flush();RenderSystem.setShaderColor(1,1,1,1);
    }
    public static void sprite(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h,int color) {
        g.flush();tint(color);g.blitSprite(id,x,y,w,h);g.flush();RenderSystem.setShaderColor(1,1,1,1);
    }
    public static void tint(int color) { RenderSystem.setShaderColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f); }
    public static org.joml.Matrix3x2f pose(GuiGraphics graphics) {
        var m=graphics.pose().last().pose();return new org.joml.Matrix3x2f(m.m00(),m.m01(),m.m10(),m.m11(),m.m30(),m.m31());
    }
    public static void face(GuiGraphics g,net.minecraft.client.resources.PlayerSkin skin,int x,int y,int size,int color) {
        g.flush();tint(color);net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin,x,y,size);g.flush();RenderSystem.setShaderColor(1,1,1,1);
    }
    public static void sprite(GuiGraphics g,ResourceLocation id,float x,float y,int w,int h,int color) { sprite(g,id,Math.round(x),Math.round(y),w,h,color); }
    public static void sprite(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h,float alpha) { sprite(g,id,x,y,w,h,(Math.round(alpha*255)<<24)|0xffffff); }
    public static void enableScissor(GuiGraphics g,int x0,int y0,int x1,int y1) {
        var pose=pose(g);
        var a=pose.transformPosition(x0,y0,new org.joml.Vector2f());
        var b=pose.transformPosition(x1,y1,new org.joml.Vector2f());
        g.flush();g.enableScissor((int)Math.floor(Math.min(a.x,b.x)),(int)Math.floor(Math.min(a.y,b.y)),(int)Math.ceil(Math.max(a.x,b.x)),(int)Math.ceil(Math.max(a.y,b.y)));
    }
}
