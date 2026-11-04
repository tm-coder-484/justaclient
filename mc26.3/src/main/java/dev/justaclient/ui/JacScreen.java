package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Easing;
import java.net.URI;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Base for the client's full screens (mods, servers, key binds, screenshots, hubs). It turns the version-specific screen
 * callbacks into plain ones, so the screens themselves are the same code on every Minecraft version: mouse buttons count
 * from 0, keys are {@link InputConstants} codes and typed text arrives as strings.
 */
public abstract class JacScreen extends Screen {
	protected final @Nullable Screen parent;
	protected final Animation in = new Animation(Easing.CUBIC_OUT, 220, 0f);
	/**
	 * How much the screen is drawn scaled down to fit the window: 1 when the window has room for {@link #minWidth()} by
	 * {@link #minHeight()}, less on a small window or a big GUI scale. {@code width} and {@code height} are the screen's own
	 * (scaled up) canvas, so the screens lay themselves out as if the window were big enough.
	 */
	protected float uiScale = 1f;
	private final UiFit.Canvas canvas = new UiFit.Canvas();

	protected JacScreen(String title, @Nullable Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	/** Draws the screen; {@code t} is the opening animation, 0 to 1. */
	protected abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float t);

	/** A click; {@code button} 0 is left, 1 right. Return true if handled. */
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		return false;
	}

	protected void release(double mx, double my, int button) {
	}

	protected void drag(double mx, double my, int button) {
	}

	protected boolean scroll(double mx, double my, double amount) {
		return false;
	}

	/** A key press with {@link InputConstants} codes. Return true if handled; Escape closes the screen otherwise. */
	protected boolean key(int key, boolean ctrl, boolean shift) {
		return false;
	}

	protected boolean typed(String text) {
		return false;
	}

	/** Called when the screen opens, after the size is known. */
	protected void setup() {
	}

	/** The smallest canvas, in GUI units, the layout is made for. A smaller window draws the screen scaled down to fit. */
	protected float minWidth() {
		return 440f;
	}

	protected float minHeight() {
		return 260f;
	}

	@Override
	protected final void init() {
		// Screens that take typing ask for text input on this version.
		minecraft.textInputManager().startTextInput(this);
		uiScale = canvas.fit(this, minWidth(), minHeight());
		setup();
	}

	/** A mouse position from the window's GUI space in the screen's canvas. */
	protected final double canvas(double v) {
		return canvas.in(v);
	}

	@Override
	public void removed() {
		minecraft.textInputManager().stopTextInput(this);
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		boolean pushed = canvas.push(g);
		super.extractBackground(g, canvas.in(mouseX), canvas.in(mouseY), partialTick);
		canvas.pop(g, pushed);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int mx = canvas.in(mouseX);
		int my = canvas.in(mouseY);
		boolean pushed = canvas.push(g);
		super.extractRenderState(g, mx, my, partialTick);
		draw(g, mx, my, in.animateTo(1f).get());
		canvas.pop(g, pushed);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return click(canvas(event.x()), canvas(event.y()), event.button() - 1, doubleClick) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		release(canvas(event.x()), canvas(event.y()), event.button() - 1);
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		drag(canvas(event.x()), canvas(event.y()), event.button() - 1);
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return scroll(canvas(x), canvas(y), scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key(event.key(), event.hasControlDown(), event.hasShiftDown())) {
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return event.isAllowedChatCharacter() && typed(event.codepointAsString()) || super.charTyped(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** Opens another screen. */
	protected void open(@Nullable Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	public static void openPath(Path path) {
		com.mojang.blaze3d.Blaze3D.openPath(path);
	}

	public static void openUri(String uri) {
		try {
			com.mojang.blaze3d.Blaze3D.openUri(URI.create(uri));
		} catch (IllegalArgumentException e) {
			// Not a link.
		}
	}

	/** Splits {@code text} into lines no wider than {@code max}. */
	public static java.util.List<String> wrap(String text, float size, dev.justaclient.render.text.JacText.Weight weight, float max) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String para : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : para.split(" ")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && dev.justaclient.render.text.JacText.width(next, size, weight) > max) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(next);
				}
			}
			out.add(line.toString());
		}
		return out;
	}

	/** Shortens {@code text} with an ellipsis to fit {@code max}. */
	public static String fit(String text, float size, dev.justaclient.render.text.JacText.Weight weight, float max) {
		if (dev.justaclient.render.text.JacText.width(text, size, weight) <= max) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && dev.justaclient.render.text.JacText.width(s + "...", size, weight) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** The game's key for mouse button {@code button} (0 is left), as key binds store it. */
	public static InputConstants.Key mouseKey(int button) {
		// Mouse buttons count from 1 here.
		return InputConstants.Type.MOUSE.getOrCreate(button + 1);
	}

	/** The game's key for key code {@code key}. */
	public static InputConstants.Key keyboardKey(int key) {
		return InputConstants.Type.KEYBOARD.getOrCreate(key);
	}

	/** Draws text in the game's own font, for text that carries its own colours (server MOTDs, chat). */
	protected void vanillaText(GuiGraphicsExtractor g, net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
		g.text(font, text, x, y, color, false);
	}

	public static String clipboard() {
		return net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
	}
}
