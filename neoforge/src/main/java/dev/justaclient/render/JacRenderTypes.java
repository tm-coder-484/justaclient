package dev.justaclient.render;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import java.util.function.Supplier;
/** Render states for the custom world effects, using the native 1.21.1 buffer renderer. */
public abstract class JacRenderTypes extends RenderType {
    private JacRenderTypes() { super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{}); }
    public static RenderType effect(String name,Supplier<ShaderInstance> shader,boolean throughWalls,boolean additive) {
        return create(name,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,1536,false,true,
            CompositeState.builder().setShaderState(new ShaderStateShard(shader))
                .setTransparencyState(additive?ADDITIVE_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(throughWalls?NO_DEPTH_TEST:LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
    private static final java.util.Map<Float,RenderType> LINES=new java.util.HashMap<>();
    public static RenderType lines(float width) {
        return LINES.computeIfAbsent(width,w->create("justaclient_lines_"+w,DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(java.util.OptionalDouble.of(w)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL).createCompositeState(false)));
    }
}
