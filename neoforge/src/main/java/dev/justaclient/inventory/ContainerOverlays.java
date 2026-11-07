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
import dev.justaclient.platform.events.ScreenEvents;
import dev.justaclient.platform.events.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;
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

		void draw(Box box, GuiGraphics g, float x, float y, float w, float h, double mouseX, double mouseY);

		/** {@code button} is 0 for the left button on every game version (26.3's 1-based numbers are converted). */
		default boolean click(Box box, float x, float y, float w, float h, double mouseX, double mouseY, int button) {
			return false;
		}

		default boolean scroll(Box box, float x, float y, float w, float h, double mouseX, double mouseY, double amount) {
			return false;
		}

		/** Drawn on the container itself, under the panels: slot outlines and such. */
		default void decorate(Box box, GuiGraphics g, double mouseX, double mouseY) {
		}
	}

	/** The container screen and where its box is. */
	public record Box(AbstractContainerScreen<?> screen, int left, int top, int width, int height) {
		public @Nullable Slot hovered() {
			return ((AbstractContainerScreenAccessor) screen).jac$hoveredSlot();
		}

		/** Rings a slot, e.g. the one that holds the next brewing ingredient. */
		public void outline(GuiGraphics g, Slot slot, int color) {
			float x = left + slot.x - 1;
			float y = top + slot.y - 1;
			Draw.rect(g, x, y, 18, 18, 2f, Colors.withAlpha(color, 0x38));
			Draw.outline(g, x, y, 18, 18, 2f, 1f, color);
		}
	}

	/**
	 * A panel placed beside the container: {@code w} by {@code h} is its own size, drawn at {@code scale} (below 1 when the
	 * window is too small for it beside the container, e.g. at GUI scale 4) with its top-left kept at {@code x}, {@code y}.
	 */
	private record Placed(Overlay overlay, float x, float y, float w, float h, float scale) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w * scale && my >= y && my < y + h * scale;
		}

		/** A mouse position on screen, in the panel's own (unscaled) coordinates. */
		double localX(double mx) {
			return x + (mx - x) / scale;
		}

		double localY(double my) {
			return y + (my - y) / scale;
		}
	}

	/** Panels shrink to fit beside the container down to this, then get narrower instead. */
	private static final float MIN_SCALE = 0.6f;

	private static final List<Overlay> OVERLAYS = new ArrayList<>();
	private static final List<Placed> placed = new ArrayList<>();

	private ContainerOverlays() {
	}

	/** The recipe book's width plus the gap the game leaves between it and the container. */
	private static final float BOOK_WIDTH = 149;

	public static void register(Overlay overlay) {
		OVERLAYS.add(overlay);
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			InvCompat.afterDraw(screen, (s, g, mx, my, delta) -> draw(container, g, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y(), event.button(), container));
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
		// The recipe book sits left of the container when open; panels go beside it, never on it.
		float bookWidth = screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener recipeScreen
			&& recipeScreen.getRecipeBookComponent().isVisible() ? BOOK_WIDTH : 0;
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
			float roomLeft = box.left - GAP - 2 - bookWidth;
			boolean fitsRight = w <= roomRight && (rightY + h <= screen.height - 2 || rightY == box.top);
			boolean fitsLeft = w <= roomLeft && (leftY + h <= screen.height - 2 || leftY == box.top);
			// On a narrow window neither side may have room: use the wider side and shrink the panel to fit.
			boolean right = fitsRight || !fitsLeft && roomRight >= roomLeft;
			if (bookWidth > 0 && !fitsRight && !fitsLeft && Math.max(roomRight, roomLeft) < 80 * MIN_SCALE) {
				// The open recipe book takes the room: leave the panel out rather than cover the book.
				continue;
			}
			float room = right ? roomRight : roomLeft;
			float top = right ? rightY : leftY;
			// The first panel in a column is also kept on screen top to bottom; later ones stack below as before.
			float scale = dev.justaclient.common.ui.Fit.sidePanelScale(w, h, room, top == box.top ? screen.height - 2 - top : Float.MAX_VALUE,
				MIN_SCALE);
			// Still too wide at the smallest scale: narrow it as before.
			float pw = w * scale <= room ? w : Math.max(80, room / scale);
			if (right) {
				placed.add(new Placed(overlay, rightX, rightY, pw, h, scale));
				rightY += h * scale + GAP;
			} else {
				placed.add(new Placed(overlay, Math.max(2, box.left - GAP - bookWidth - pw * scale), leftY, pw, h, scale));
				leftY += h * scale + GAP;
			}
		}
	}

	private static void draw(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my) {
		Box box = box(screen);
		layout(box);
		for (Placed p : placed) {
			p.overlay.decorate(box, g, mx, my);
		}
		for (Placed p : placed) {
			boolean scaled = p.scale < 1f;
			if (scaled) {
				g.pose().pushPose();
				g.pose().translate(p.x, p.y, 0);
				g.pose().scale(p.scale, p.scale, 1);
				g.pose().translate(-p.x, -p.y, 0);
			}
			Draw.hudPanel(g, p.x, p.y, p.w, p.h);
			p.overlay.draw(box, g, p.x, p.y, p.w, p.h, p.localX(mx), p.localY(my));
			if (scaled) {
				g.pose().popPose();
			}
		}
		drawOffer(box, g, mx, my);
	}

	/** Where the storage offer chip sits this frame, or null when there is none. */
	private static float @Nullable [] offerChip;

	/** The latest "this chest has it" offer, as a chip under the container: click it or press the GPS key. */
	private static void drawOffer(Box box, GuiGraphics g, int mx, int my) {
		String line = StorageFinder.offerLine();
		if (line == null) {
			offerChip = null;
			return;
		}
		String key = dev.justaclient.feature.impl.StorageMemoryFeature.get().gpsKeyName() + " for GPS";
		float size = Theme.SMALL + 0.5f;
		float keyW = JacText.width(key, size, JacText.Weight.SEMIBOLD);
		float maxText = Math.max(60, box.screen.width - 40 - keyW);
		String text = dev.justaclient.ui.JacScreen.fit(line, size, JacText.Weight.REGULAR, maxText);
		float w = 6 + 9 + JacText.width(text, size, JacText.Weight.REGULAR) + 8 + keyW + 6;
		float h = 14;
		float x = box.left + box.width / 2f - w / 2f;
		x = Math.max(2, Math.min(x, box.screen.width - 2 - w));
		float y = Math.min(box.top + box.height + 4, box.screen.height - h - 2);
		boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
		Draw.hudPanel(g, x, y, w, h);
		if (hot) {
			Draw.rect(g, x, y, w, h, 4f, Theme.CONTROL_HOVER);
		}
		JacText.icon(g, Icons.PACKAGE_SEARCH, x + 6, y + 3.5f, 6.5f, JacConfig.accent());
		JacText.draw(g, text, x + 15, y + 4, Theme.TEXT, size, JacText.Weight.REGULAR);
		JacText.draw(g, key, x + w - 6 - keyW, y + 4, JacConfig.accent(), size, JacText.Weight.SEMIBOLD);
		offerChip = new float[] {x, y, w, h};
	}

	private static boolean click(double mx, double my, int button, AbstractContainerScreen<?> screen) {
		Box box = box(screen);
		float[] chip = offerChip;
		if (chip != null && mx >= chip[0] && mx < chip[0] + chip[2] && my >= chip[1] && my < chip[1] + chip[3]) {
			if (button == 0) {
				StorageFinder.accept();
			}
			return true;
		}
		for (Placed p : placed) {
			if (p.contains(mx, my)) {
				p.overlay.click(box, p.x, p.y, p.w, p.h, p.localX(mx), p.localY(my), button);
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
				return p.overlay.scroll(box, p.x, p.y, p.w, p.h, p.localX(mx), p.localY(my), amount) || true;
			}
		}
		return false;
	}

	// Shared drawing for the panels, so they all look alike.

	public static float header(GuiGraphics g, Icons icon, String title, float x, float y, float w) {
		JacText.icon(g, icon, x + 6, y + 6, 7f, JacConfig.accent());
		JacText.draw(g, title, x + 17, y + 5.5f, Theme.TEXT, Theme.LABEL, JacText.Weight.SEMIBOLD);
		return y + 18;
	}

	/** A label on the left and a value on the right. Returns the next line's y. */
	public static float row(GuiGraphics g, String label, String value, int valueColor, float x, float y, float w) {
		JacText.draw(g, label, x + 6, y, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
		float vw = JacText.width(value, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, value, x + w - 6 - vw, y, valueColor, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		return y + 10;
	}

	/** A thin progress bar. Returns the next line's y. */
	public static float bar(GuiGraphics g, float fraction, int color, float x, float y, float w) {
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
