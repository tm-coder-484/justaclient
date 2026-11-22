package dev.justaclient.platform.input;
public record KeyEvent(int key, int scancode, int modifiers) {
    public boolean hasControlDown() { return net.minecraft.client.gui.screens.Screen.hasControlDown(); }
    public boolean hasShiftDown() { return (modifiers & 1) != 0; }
    public boolean hasAltDown() { return (modifiers & 4) != 0; }
}
