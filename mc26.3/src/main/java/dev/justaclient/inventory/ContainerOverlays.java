package dev.justaclient.inventory;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.mixin.AbstractContainerScreenAccessor;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * The kit the container helpers share: small panels docked beside a container screen (right side first, left when the
 * right is full), slot outlines, and clicks and scrolls routed to the panel under the mouse. Panels only show what the
 * game already shows or knows; they never move items.
 */
public final class ContainerOverlays {
	private static final float GAP = 5f;

	/** One helper panel. Each feature that adds one registers it once. */
	public interface Overlay {
		boolean applies(AbstractContainerScreen<?> screen);

		float width(Box box);

		/** The panel height for the current state; 0 hides it this frame. */
		float height(Box box);

		void draw(Box box, GuiGraphicsExtractor g, float x, float y, float w, float h, double mouseX, double mouseY);

		default boolean click(Box box, float x, float y, float w, float h, double mouseX, double mouseY, int button) {
			return false;
		}

		default boolean scroll(Box box, float x, float y, float w, float h, double mouseX, double mouseY, double amount) {
			return false;
		}

		/** Drawn on the container itself, under the panels: slot outlines and such. */
		default void decorate(Box box, GuiGraphicsExtractor g, double mouseX, double mouseY) {
		}
	}

	/** The container screen and where its box is. */
	public record Box(AbstractContainerScreen<?> screen, int left, int top, int width, int height) {
		public @Nullable Slot hovered() {
			return ((AbstractContainerScreenAccessor) screen).jac$hoveredSlot();
		}

		/** Rings a slot, e.g. the one that holds the next brewing ingredient. */
		public void outline(GuiGraphicsExtractor g, Slot slot, int color) {
			float x = left + slot.x - 1;
			float y = top + slot.y - 1;
			Draw.rect(g, x, y, 18, 18, 2f, Colors.withAlpha(color, 0x38));
			Draw.outline(g, x, y, 18, 18, 2f, 1f, color);
		}
	}

	private record Placed(Overlay overlay, float x, float y, float w, float h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final List<Overlay> OVERLAYS = new ArrayList<>();
	private static final List<Placed> placed = new ArrayList<>();

	private ContainerOverlays() {
	}

	public static void register(Overlay overlay) {
		OVERLAYS.add(overlay);
	}

	public static void init() {
		// Mouse buttons count from 1 here (left is 1); the overlays use 0 for left like the older versions.
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			InvCompat.afterDraw(screen, (s, g, mx, my, delta) -> draw(container, g, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y(), event.button() - 1, container));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h2, v) -> !scroll(container, mx, my, v));
		});
	}

	private static Box box(AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
		return new Box(screen, a.jac$left(), a.jac$top(), a.jac$imageWidth(), a.jac$imageHeight());
	}

	private static void layout(Box box) {
		placed.clear();
		Screen screen = box.screen;
		float rightX = box.left + box.width + GAP;
		float rightY = box.top;
		float leftY = box.top;
		for (Overlay overlay : OVERLAYS) {
			if (!overlay.applies(box.screen)) {
				continue;
			}
			float w = overlay.width(box);
			float h = overlay.height(box);
			if (h <= 0) {
				continue;
			}
			float roomRight = screen.width - 2 - rightX;
			float roomLeft = box.left - GAP - 2;
			boolean fitsRight = w <= roomRight && (rightY + h <= screen.height - 2 || rightY == box.top);
			boolean fitsLeft = w <= roomLeft && (leftY + h <= screen.height - 2 || leftY == box.top);
			// On a narrow window neither side may have room: use the wider side and narrow the panel to fit.
			boolean right = fitsRight || !fitsLeft && roomRight >= roomLeft;
			float pw = Math.max(80, Math.min(w, right ? roomRight : roomLeft));
			if (right) {
				placed.add(new Placed(overlay, rightX, rightY, pw, h));
				rightY += h + GAP;
			} else {
				placed.add(new Placed(overlay, Math.max(2, box.left - GAP - pw), leftY, pw, h));
				leftY += h + GAP;
			}
		}
	}

	private static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mx, int my) {
		Box box = box(screen);
		layout(box);
		for (Placed p : placed) {
			p.overlay.decorate(box, g, mx, my);
		}
		for (Placed p : placed) {
			Draw.hudPanel(g, p.x, p.y, p.w, p.h);
			p.overlay.draw(box, g, p.x, p.y, p.w, p.h, mx, my);
		}
	}

	private static boolean click(double mx, double my, int button, AbstractContainerScreen<?> screen) {
		Box box = box(screen);
		for (Placed p : placed) {
			if (p.contains(mx, my)) {
				p.overlay.click(box, p.x, p.y, p.w, p.h, mx, my, button);
				// The click lands on the panel, never on the slots or the world behind it.
				return true;
			}
		}
		return false;
	}

	private static boolean scroll(AbstractContainerScreen<?> screen, double mx, double my, double amount) {
		Box box = box(screen);
		for (Placed p : placed) {
			if (p.contains(mx, my)) {
				return p.overlay.scroll(box, p.x, p.y, p.w, p.h, mx, my, amount) || true;
			}
		}
		return false;
	}

	// Shared drawing for the panels, so they all look alike.

	public static float header(GuiGraphicsExtractor g, Icons icon, String title, float x, float y, float w) {
		JacText.icon(g, icon, x + 6, y + 6, 7f, JacConfig.accent());
		JacText.draw(g, title, x + 17, y + 5.5f, Theme.TEXT, Theme.LABEL, JacText.Weight.SEMIBOLD);
		return y + 18;
	}

	/** A label on the left and a value on the right. Returns the next line's y. */
	public static float row(GuiGraphicsExtractor g, String label, String value, int valueColor, float x, float y, float w) {
		JacText.draw(g, label, x + 6, y, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
		float vw = JacText.width(value, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, value, x + w - 6 - vw, y, valueColor, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		return y + 10;
	}

	/** A thin progress bar. Returns the next line's y. */
	public static float bar(GuiGraphicsExtractor g, float fraction, int color, float x, float y, float w) {
		Draw.rect(g, x + 6, y, w - 12, 3, 1.5f, 0x30FFFFFF);
		Draw.rect(g, x + 6, y, Math.max(3, (w - 12) * Math.max(0, Math.min(1, fraction))), 3, 1.5f, color);
		return y + 7;
	}

	/** "1:05", "12s". */
	public static String time(int ticks) {
		int s = Math.max(0, (ticks + 19) / 20);
		return s >= 60 ? s / 60 + ":" + String.format("%02d", s % 60) : s + "s";
	}
}
