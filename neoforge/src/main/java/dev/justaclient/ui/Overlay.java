package dev.justaclient.ui;

import net.minecraft.client.gui.GuiGraphics;

/** A modal card drawn over a screen, in screen coordinates. It takes every click and key while it is open. */
public interface Overlay {
	void render(GuiGraphics g, int screenW, int screenH, double mx, double my);

	boolean mouseClicked(double mx, double my, int button);

	boolean keyPressed(int key);

	default boolean charTyped(String text) {
		return true;
	}

	default boolean mouseScrolled(double amount) {
		return true;
	}
}
