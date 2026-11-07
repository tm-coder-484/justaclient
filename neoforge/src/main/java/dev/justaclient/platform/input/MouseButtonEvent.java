package dev.justaclient.platform.input;
public record MouseButtonEvent(double x, double y, int buttonInfo) {
    public int button() { return buttonInfo; }
    public boolean hasShiftDown() { return net.minecraft.client.gui.screens.Screen.hasShiftDown(); }
}
