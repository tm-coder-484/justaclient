package dev.justaclient.render;
import com.mojang.blaze3d.pipeline.*;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.PerformanceModeFeature;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
/** Dual Kawase blur, captured once per GUI frame and shared by all glass panels. */
public final class BlurEngine {
    public static final int MAX_PASSES=5;
    private static final List<TextureTarget> down=new ArrayList<>(),up=new ArrayList<>();
    private static int width=-1,height=-1,idleFrames;
    private static boolean captured;
    public static void beginFrame() {
        captured=false;
        if (++idleFrames>600 && PerformanceModeFeature.freeMemory()) close();
    }
    public static int request() { capture(); return output(); }
    public static int output() { return up.isEmpty()?0:up.getFirst().getColorTextureId(); }
    public static void capture() {
        if (captured) return;
        captured=true;idleFrames=0;
        var main=Minecraft.getInstance().getMainRenderTarget();
        if (main.width!=width || main.height!=height || down.isEmpty()) {
            close();width=main.width;height=main.height;
            for (int i=0;i<MAX_PASSES;i++) {
                int w=Math.max(1,width>>(i+1)),h=Math.max(1,height>>(i+1));
                down.add(new TextureTarget(w,h,false,Minecraft.ON_OSX));
                up.add(new TextureTarget(w,h,false,Minecraft.ON_OSX));
            }
        }
        int passes=Math.max(1,Math.min(Math.min(MAX_PASSES,PerformanceModeFeature.maxBlurPasses()),JacConfig.get().style.blurStrength));
        int source=main.getColorTextureId();
        for(int i=0;i<passes;i++) { blit(JacPipelines.BLUR_DOWN,source,down.get(i));source=down.get(i).getColorTextureId(); }
        for(int i=passes-2;i>=0;i--) { blit(JacPipelines.BLUR_UP,source,up.get(i));source=up.get(i).getColorTextureId(); }
        if(passes==1) blit(JacPipelines.BLUR_UP,source,up.getFirst());
        SpatialMenus.target(main).bindWrite(true);
    }
    private static void close() { down.forEach(RenderTarget::destroyBuffers);up.forEach(RenderTarget::destroyBuffers);down.clear();up.clear(); }
    private static void blit(ShaderInstance shader,int texture,RenderTarget target) { shader.setSampler("InSampler",texture);FullScreen.draw(shader,target,false); }
}
