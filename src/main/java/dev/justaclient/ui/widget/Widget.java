package dev.justaclient.ui.widget;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Easing;
import net.minecraft.client.gui.GuiGraphics;

/** Base for Justaclient's drawn controls. Coordinates are local to the panel that owns the widget. */
public abstract class Widget {
	protected float x;
	protected float y;
	protected final float width;
	protected final float height;
	/** 0..1 hover amount, eased so highlights fade rather than snap. */
	protected final Animation hover = new Animation(Easing.CUBIC_OUT, 120, 0f);

	protected Widget(float x, float y, float width, float height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void setPosition(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public float y() {
		return y;
	}

	public float height() {
		return height;
	}

	public final void render(GuiGraphics g, double mouseX, double mouseY, float alpha) {
		hover.animateTo(isMouseOver(mouseX, mouseY) ? 1f : 0f);
		draw(g, mouseX, mouseY, alpha);
	}

	protected abstract void draw(GuiGraphics g, double mouseX, double mouseY, float alpha);

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public void mouseDragged(double mouseX, double mouseY) {
	}

	public void mouseReleased(double mouseX, double mouseY) {
	}

	/** True while the widget wants every key press, e.g. a key binder waiting for a key. */
	public boolean capturesKeys() {
		return false;
	}

	/** Returns true if the key was used. */
	public boolean keyPressed(int key) {
		return false;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
