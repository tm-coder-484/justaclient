package dev.justaclient.platform.events;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
public final class ScreenEvents {
    public interface Init { void init(Minecraft mc, Screen screen, int width, int height); }
    public interface Render { void render(Screen screen, GuiGraphics g, int mx, int my, float partial); }
    public interface Action { void run(Screen screen); }
    public static final Event<Init> AFTER_INIT = new Event<>();
    private static final Map<Screen, Event<Render>> RENDER = new WeakHashMap<>();
    private static final Map<Screen, Event<Action>> TICK = new WeakHashMap<>(), REMOVE = new WeakHashMap<>();
    public static Event<Render> afterRender(Screen s) { return RENDER.computeIfAbsent(s, k -> new Event<>()); }
    public static Event<Action> afterTick(Screen s) { return TICK.computeIfAbsent(s, k -> new Event<>()); }
    public static Event<Action> remove(Screen s) { return REMOVE.computeIfAbsent(s, k -> new Event<>()); }
    public static void init(Screen s) { RENDER.remove(s); TICK.remove(s); REMOVE.remove(s); ScreenMouseEvents.clear(s); ScreenKeyboardEvents.clear(s); }
}
