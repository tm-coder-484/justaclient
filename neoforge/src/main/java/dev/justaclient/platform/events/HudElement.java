package dev.justaclient.platform.events;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
public interface HudElement { void render(GuiGraphics graphics,DeltaTracker delta); }
