package dev.justaclient.platform.events;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import dev.justaclient.platform.input.MouseButtonEvent;
public final class ScreenMouseEvents {
    public interface Button { boolean allow(Screen screen, MouseButtonEvent e); }
    public interface Scroll { boolean allow(Screen screen, double x, double y, double sx, double sy); }
    private static final Map<Screen, Event<Button>> CLICK = new WeakHashMap<>(), RELEASE = new WeakHashMap<>();
    private static final Map<Screen, Event<Scroll>> SCROLL = new WeakHashMap<>();
    public static Event<Button> allowMouseClick(Screen s) { return CLICK.computeIfAbsent(s, k -> new Event<>()); }
    public static Event<Button> allowMouseRelease(Screen s) { return RELEASE.computeIfAbsent(s, k -> new Event<>()); }
    public static Event<Scroll> allowMouseScroll(Screen s) { return SCROLL.computeIfAbsent(s, k -> new Event<>()); }
    static void clear(Screen s) { CLICK.remove(s); RELEASE.remove(s); SCROLL.remove(s); }
}
