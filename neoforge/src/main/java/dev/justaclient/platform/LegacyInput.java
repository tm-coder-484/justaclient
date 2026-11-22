package dev.justaclient.platform;
import net.minecraft.client.MouseHandler;
import com.mojang.blaze3d.platform.Window;
public final class LegacyInput {
    public static double x(MouseHandler mouse,Window window) { return mouse.xpos()*window.getGuiScaledWidth()/window.getScreenWidth(); }
    public static double y(MouseHandler mouse,Window window) { return mouse.ypos()*window.getGuiScaledHeight()/window.getScreenHeight(); }
}
