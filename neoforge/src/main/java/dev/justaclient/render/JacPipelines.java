package dev.justaclient.render;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import dev.justaclient.Justaclient;
import java.io.IOException;
/** Native OpenGL shader programs for the 1.21.1 renderer. Reloaded with resource packs. */
public final class JacPipelines {
    public static ShaderInstance SHAPE, ARC, GLASS, MSDF_TEXT, WORLD_FX, AURORA, BLUR_DOWN, BLUR_UP, POST_GRADE, HAND_COMPOSITE, SPATIAL;
    public static void init() { }
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("shape"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> SHAPE = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("arc"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> ARC = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("glass"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> GLASS = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("msdf_text"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> MSDF_TEXT = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("world_fx"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> WORLD_FX = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("aurora"), DefaultVertexFormat.POSITION_TEX_COLOR), shader -> AURORA = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("kawase_down"), DefaultVertexFormat.POSITION_TEX), shader -> BLUR_DOWN = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("kawase_up"), DefaultVertexFormat.POSITION_TEX), shader -> BLUR_UP = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("grade"), DefaultVertexFormat.POSITION_TEX), shader -> POST_GRADE = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("hand"), DefaultVertexFormat.POSITION_TEX), shader -> HAND_COMPOSITE = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Justaclient.id("spatial"), DefaultVertexFormat.POSITION_TEX), shader -> SPATIAL = shader);
    }
}
