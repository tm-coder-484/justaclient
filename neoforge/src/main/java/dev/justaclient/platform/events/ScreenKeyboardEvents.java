package dev.justaclient.platform.events;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import dev.justaclient.platform.input.KeyEvent;
public final class ScreenKeyboardEvents {
    public interface Allow { boolean allow(Screen screen, KeyEvent e); }
    public interface After { void run(Screen screen, KeyEvent e); }
    private static final Map<Screen, Event<Allow>> ALLOW = new WeakHashMap<>();
    private static final Map<Screen, Event<After>> AFTER = new WeakHashMap<>();
    public static Event<Allow> allowKeyPress(Screen s) { return ALLOW.computeIfAbsent(s, k -> new Event<>()); }
    public static Event<After> afterKeyPress(Screen s) { return AFTER.computeIfAbsent(s, k -> new Event<>()); }
    static void clear(Screen s) { ALLOW.remove(s); AFTER.remove(s); }
}
