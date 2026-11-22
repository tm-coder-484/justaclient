package dev.justaclient.platform;

import dev.justaclient.platform.input.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Converts GLFW callbacks into the input values used by Justaclient's screens. */
public abstract class PlatformScreen extends Screen {
    protected PlatformScreen(Component title) { super(title); }
    @Override public final boolean mouseClicked(double x, double y, int button) {
        return mouseClicked(new MouseButtonEvent(x, y, button), false);
    }
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }
    @Override public final boolean mouseReleased(double x, double y, int button) {
        return mouseReleased(new MouseButtonEvent(x, y, button));
    }
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return mouseDragged(new MouseButtonEvent(x, y, button), dx, dy);
    }
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }
    @Override public final boolean keyPressed(int key, int scan, int mods) {
        return keyPressed(new KeyEvent(key, scan, mods));
    }
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }
    @Override public final boolean keyReleased(int key, int scan, int mods) {
        return keyReleased(new KeyEvent(key, scan, mods));
    }
    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }
    @Override public final boolean charTyped(char character, int mods) {
        return charTyped(new CharacterEvent(character, mods));
    }
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped((char) event.codepoint(), event.modifiers());
    }
}
