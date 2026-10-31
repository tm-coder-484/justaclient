package dev.justaclient.ui;

import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.render.Draw;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.Segmented;
import dev.justaclient.ui.widget.Slider;
import dev.justaclient.ui.widget.Swatches;
import dev.justaclient.ui.widget.Toggle;
import dev.justaclient.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The Theme page: live controls for every style token over a dark glass panel. This is the M0 proving ground for the
 * render kit (glass, shapes, MSDF text, animation); the full click GUI in M1 grows around it.
 */
public final class StyleScreen extends Screen {
	private static final float W = 380f;
	private static final float H = 214f;
	private static final List<String> ACCENTS = List.of("#4C8DFF", "#8B5CF6", "#14B8A6", "#22C55E", "#F59E0B", "#F43F5E", "#E5E7EB");

	private final @Nullable Screen parent;
	private final List<Widget> widgets = new ArrayList<>();
	private final Animation open = new Animation(Easing.CUBIC_OUT, 260, 0f);
	private boolean closing;
	private float originX;
	private float originY;
	private float scale = 1f;

	public StyleScreen(@Nullable Screen parent) {
		super(Component.literal("Justaclient"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		widgets.clear();
		StyleTokens s = JacConfig.get().style;
		float left = 16f;
		float right = 200f;
		float col = 164f;

		widgets.add(new Swatches(left, 46, col, "Accent", ACCENTS, () -> s.accent, v -> {
			s.accent = v;
			s.rainbowAccent = false;
		}));
		widgets.add(new Slider(left, 78, col, "Panel opacity", 0, 1, 0.01,
			() -> s.surfaceOpacity, v -> s.surfaceOpacity = (float) v, v -> Math.round(v * 100) + "%"));
		widgets.add(new Slider(left, 106, col, "Corner radius", 0, 16, 1,
			() -> s.cornerRadius, v -> s.cornerRadius = (float) v, v -> Math.round(v) + " px"));
		widgets.add(new Toggle(left, 134, col, "Background blur", () -> s.blur, v -> s.blur = v));
		widgets.add(new Slider(left, 154, col, "Blur strength", 1, 5, 1,
			() -> s.blurStrength, v -> s.blurStrength = (int) Math.round(v), v -> String.valueOf(Math.round(v))));

		widgets.add(new Segmented<>(right, 44, col, "Outline", List.of(StyleTokens.Outline.values()), List.of("Off", "Thin", "Glow"),
			() -> s.outline, v -> s.outline = v));
		widgets.add(new Segmented<>(right, 76, col, "Font", List.of(StyleTokens.FontChoice.values()), List.of("Inter", "Vanilla"),
			() -> s.font, v -> s.font = v));
		widgets.add(new Segmented<>(right, 108, col, "Motion", List.of(StyleTokens.Motion.values()), List.of("Off", "Normal", "Fast"),
			() -> s.motion, v -> s.motion = v));
		widgets.add(new Toggle(right, 142, col, "Rainbow accent", () -> s.rainbowAccent, v -> s.rainbowAccent = v));

		widgets.add(new Button(left, H - 30, 110, 18, "Reset to dark glass", false, () -> {
			JacConfig.resetStyle();
			JacConfig.save();
			rebuildWidgets();
		}));
		widgets.add(new Button(W - 16 - 72, H - 30, 72, 18, "Done", true, this::onClose));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float t = open.animateTo(closing ? 0f : 1f).get();
		if (closing && open.isDone()) {
			minecraft.setScreen(parent);
			return;
		}
		// Fit the panel on small windows, then add the open animation's slight zoom.
		float fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		scale = fit * (0.94f + 0.06f * t);
		originX = (width - W * scale) / 2f;
		originY = (height - H * scale) / 2f + (1f - t) * 6f;
		double lx = toLocalX(mouseX);
		double ly = toLocalY(mouseY);

		g.pose().pushMatrix();
		g.pose().translate(originX, originY);
		g.pose().scale(scale, scale);

		Draw.panel(g, 0, 0, W, H, t);
		JacText.draw(g, "justaclient", 16, 13, Colors.fade(Theme.TEXT, t), Theme.TITLE, JacText.Weight.SEMIBOLD);
		float titleW = JacText.width("justaclient", Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Theme", 16 + titleW + 8, 14.5f, Colors.fade(Theme.TEXT_DIM, t), Theme.LABEL + 1, JacText.Weight.REGULAR);
		String hint = Justaclient.OPEN_MENU.getTranslatedKeyMessage().getString() + " to close";
		JacText.draw(g, hint, W - 16 - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), 16,
			Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		Draw.rect(g, 16, 34, W - 32, 1, 0, Colors.fade(Theme.DIVIDER, t));
		Draw.rect(g, 191.5f, 44, 1, 136, 0, Colors.fade(Theme.DIVIDER, t));

		for (Widget widget : widgets) {
			widget.render(g, lx, ly, t);
		}
		drawPreview(g, t);
		g.pose().popMatrix();
	}

	/** Accent strip and two text sizes, so changes to accent and font are visible at a glance. */
	private void drawPreview(GuiGraphics g, float alpha) {
		float x = 200f;
		float y = 162f;
		Draw.horizontalGradient(g, x, y, 3f, 18f, 1.5f, Colors.fade(JacConfig.accentAt(0f), alpha), Colors.fade(JacConfig.accentAt(1f), alpha));
		JacText.draw(g, "Crisp at any scale", x + 9, y, Colors.fade(Theme.TEXT, alpha), Theme.LABEL + 1, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "The quick brown fox jumps over the lazy dog", x + 9, y + 11, Colors.fade(Theme.TEXT_DIM, alpha), 5.5f, JacText.Weight.REGULAR);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			renderPanorama(g, partialTick);
		}
	}

	private double toLocalX(double screenX) {
		return (screenX - originX) / scale;
	}

	private double toLocalY(double screenY) {
		return (screenY - originY) / scale;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return true;
		}
		double lx = toLocalX(event.x());
		double ly = toLocalY(event.y());
		for (Widget widget : widgets) {
			if (widget.mouseClicked(lx, ly, event.button())) {
				JacConfig.save();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double lx = toLocalX(event.x());
		double ly = toLocalY(event.y());
		widgets.forEach(w -> w.mouseDragged(lx, ly));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		double lx = toLocalX(event.x());
		double ly = toLocalY(event.y());
		widgets.forEach(w -> w.mouseReleased(lx, ly));
		JacConfig.save();
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Justaclient.OPEN_MENU.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			JacConfig.save();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
