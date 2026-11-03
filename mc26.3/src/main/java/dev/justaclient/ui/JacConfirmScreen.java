package dev.justaclient.ui;

import dev.justaclient.render.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** A {@link ConfirmDialog} on its own screen, for asking before something a vanilla screen does. */
public final class JacConfirmScreen extends Screen {
	private final @Nullable Screen parent;
	private final ConfirmDialog dialog;

	public JacConfirmScreen(@Nullable Screen parent, Icons icon, String title, String message, String confirmLabel, Runnable onConfirm) {
		super(Component.literal(title));
		this.parent = parent;
		this.dialog = new ConfirmDialog(icon, title, message, confirmLabel, onConfirm, this::onClose);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		dialog.render(g, width, height, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// SDL numbers buttons from 1 (left); the widgets use 0 for left.
		return dialog.mouseClicked(event.x(), event.y(), event.button() - 1);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return dialog.keyPressed(event.key());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return parent != null && parent.isPauseScreen();
	}
}
