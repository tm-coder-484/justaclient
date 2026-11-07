package dev.justaclient.render;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
public final class SmoothTexture extends DynamicTexture {
    public SmoothTexture(Supplier<String> label,int width,int height) { super(width,height,true);setFilter(true,false); }
}
