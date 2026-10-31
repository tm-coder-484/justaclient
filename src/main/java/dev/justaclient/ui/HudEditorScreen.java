package dev.justaclient.ui;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.HudRenderer;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Drag HUD widgets into place. Widgets snap to the screen edges, the centre lines and each other's edges (hold Shift to
 * place freely); the scroll wheel resizes the widget under the mouse and a right click opens its settings.
 */
public final class HudEditorScreen extends Screen {
	private static final float SNAP = 4f;
	private static final float BAR_H = 22f;

	private final @Nullable Screen parent;
	private final Animation open = new Animation(Easing.CUBIC_OUT, 220, 0f);
	private final Map<HudFeature, Animation> hover = new HashMap<>();
	private @Nullable HudFeature dragging;
	private float grabX;
	private float grabY;
	private @Nullable Float guideX;
	private @Nullable Float guideY;
	/** Toolbar chips: disabled widgets that can be switched on from here. */
	private final List<float[]> chipBounds = new ArrayList<>();
	private final List<HudFeature> chipFeatures = new ArrayList<>();
	private float[] resetBounds = new float[4];
	private float[] doneBounds = new float[4];

	public HudEditorScreen(@Nullable Screen parent) {
		super(Component.literal("Edit HUD layout"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HudRenderer.editing = true;
	}

	@Override
	public void removed() {
		HudRenderer.editing = false;
		Profiles.save();
	}

	private List<HudFeature> widgets() {
		return Features.hud().stream().filter(HudFeature::isEnabled).toList();
	}

	/** Lays a widget out with live content, or sample content when it has nothing to show right now. */
	private static void layout(HudFeature hud) {
		hud.layout(false);
		if (!hud.hasContent()) {
			hud.layout(true);
		}
	}

	private static boolean live(HudFeature hud) {
		hud.layout(false);
		return hud.hasContent();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float t = open.animateTo(1f).get();
		Draw.rect(g, 0, 0, width, height, 0, Colors.fade(0x50000000, t));
		// Faint centre lines help line things up even before a snap kicks in.
		Draw.rect(g, width / 2f - 0.25f, 0, 0.5f, height, 0, Colors.fade(0x18FFFFFF, t));
		Draw.rect(g, 0, height / 2f - 0.25f, width, 0.5f, 0, Colors.fade(0x18FFFFFF, t));

		int accent = JacConfig.accent();
		HudFeature hovered = dragging != null ? dragging : at(mouseX, mouseY);
		for (HudFeature hud : widgets()) {
			boolean sample = !live(hud);
			layout(hud);
			float x = hud.screenX(width);
			float y = hud.screenY(height);
			float w = hud.scaledWidth();
			float h = hud.scaledHeight();
			float hv = hover.computeIfAbsent(hud, k -> new Animation(Easing.CUBIC_OUT, 120, 0f)).animateTo(hud == hovered ? 1f : 0f).get();
			HudRenderer.draw(g, hud, sample);
			if (sample) {
				// Sample content is dimmed so it reads as a placeholder.
				Draw.rect(g, x, y, w, h, JacConfig.get().style.cornerRadius, Colors.fade(0x40000000, t));
			}
			Draw.outline(g, x - 1.5f, y - 1.5f, w + 3, h + 3, JacConfig.get().style.cornerRadius + 1.5f, 0.75f,
				Colors.fade(Colors.withAlpha(accent, Math.round(0x50 + 0xAF * hv)), t));
			if (hv > 0.01f) {
				String label = hud.name + "  " + Math.round(hud.scale.f() * 100) + "%";
				float lw = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 8;
				float ly = y - 12 < 2 ? y + h + 3 : y - 12;
				Draw.rect(g, x, ly, lw, 10, 3f, Colors.fade(Colors.withAlpha(accent, 0xE0), t * hv));
				JacText.draw(g, label, x + 4, ly + (10 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(0xFFFFFFFF, t * hv), Theme.SMALL,
					JacText.Weight.SEMIBOLD);
			}
		}
		if (dragging != null) {
			if (guideX != null) {
				Draw.rect(g, guideX - 0.5f, 0, 1f, height, 0, Colors.fade(Colors.withAlpha(accent, 0xC0), t));
			}
			if (guideY != null) {
				Draw.rect(g, 0, guideY - 0.5f, width, 1f, 0, Colors.fade(Colors.withAlpha(accent, 0xC0), t));
			}
		}
		drawToolbar(g, mouseX, mouseY, t);
	}

	private void drawToolbar(GuiGraphics g, double mx, double my, float t) {
		List<HudFeature> off = Features.hud().stream().filter(h -> !h.isEnabled()).toList();
		String hint = "Drag to move  ·  Scroll to resize  ·  Right click for settings  ·  Shift to place freely";
		float hintW = JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR);
		float chipsW = 0f;
		for (HudFeature hud : off) {
			chipsW += JacText.width(hud.name, Theme.SMALL, JacText.Weight.SEMIBOLD) + 18 + 4;
		}
		float buttons = 44 + 4 + 40;
		float w = Math.max(hintW, chipsW - 4 + (off.isEmpty() ? 0 : 6) + buttons) + 16;
		float x = (width - w) / 2f;
		float y = 8f;
		if (dragging != null) {
			t *= 0.35f;
		}
		Draw.panel(g, x, y, w, BAR_H * 2 - 4, t);
		JacText.drawCentered(g, hint, width / 2f, y + 6, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		float rowY = y + BAR_H - 4;
		float cx = x + 8;
		chipBounds.clear();
		chipFeatures.clear();
		for (HudFeature hud : off) {
			float cw = JacText.width(hud.name, Theme.SMALL, JacText.Weight.SEMIBOLD) + 18;
			boolean hov = mx >= cx && mx < cx + cw && my >= rowY && my < rowY + 14;
			Draw.rect(g, cx, rowY, cw, 14, 4f, Colors.fade(hov ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
			JacText.icon(g, Icons.PLUS, cx + 4, rowY + 3.5f, 7f, Colors.fade(JacConfig.accent(), t));
			JacText.draw(g, hud.name, cx + 14, rowY + (14 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(Theme.TEXT, t), Theme.SMALL,
				JacText.Weight.SEMIBOLD);
			chipBounds.add(new float[] {cx, rowY, cw, 14});
			chipFeatures.add(hud);
			cx += cw + 4;
		}
		float bx = x + w - 8 - buttons;
		resetBounds = new float[] {bx, rowY, 44, 14};
		doneBounds = new float[] {bx + 48, rowY, 40, 14};
		boolean hr = inside(resetBounds, mx, my);
		Draw.rect(g, bx, rowY, 44, 14, 4f, Colors.fade(hr ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		JacText.drawCentered(g, "Reset all", bx + 22, rowY + (14 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(Theme.TEXT, t), Theme.SMALL,
			JacText.Weight.SEMIBOLD);
		boolean hd = inside(doneBounds, mx, my);
		Draw.horizontalGradient(g, bx + 48, rowY, 40, 14, 4f, Colors.fade(Colors.lerp(JacConfig.accentAt(0f), 0xFFFFFFFF, hd ? 0.12f : 0f), t),
			Colors.fade(Colors.lerp(JacConfig.accentAt(1f), 0xFFFFFFFF, hd ? 0.12f : 0f), t));
		JacText.drawCentered(g, "Done", bx + 68, rowY + (14 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(0xFFFFFFFF, t), Theme.SMALL,
			JacText.Weight.SEMIBOLD);
	}

	private static boolean inside(float[] b, double mx, double my) {
		return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			renderPanorama(g, partialTick);
		}
	}

	private @Nullable HudFeature at(double mx, double my) {
		List<HudFeature> list = widgets();
		// Topmost (last drawn) first.
		for (int i = list.size() - 1; i >= 0; i--) {
			HudFeature hud = list.get(i);
			layout(hud);
			float x = hud.screenX(width);
			float y = hud.screenY(height);
			if (mx >= x && mx < x + hud.scaledWidth() && my >= y && my < y + hud.scaledHeight()) {
				return hud;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0) {
			if (inside(doneBounds, mx, my)) {
				onClose();
				return true;
			}
			if (inside(resetBounds, mx, my)) {
				Features.hud().forEach(HudFeature::resetPosition);
				Profiles.save();
				return true;
			}
			for (int i = 0; i < chipBounds.size(); i++) {
				if (inside(chipBounds.get(i), mx, my)) {
					chipFeatures.get(i).setEnabled(true);
					Profiles.save();
					return true;
				}
			}
		}
		HudFeature hud = at(mx, my);
		if (hud == null) {
			return super.mouseClicked(event, doubleClick);
		}
		if (event.button() == 1) {
			Profiles.save();
			minecraft.setScreen(ClickGuiScreen.showing(parent instanceof ClickGuiScreen gui ? gui.parent() : parent, hud));
			return true;
		}
		if (event.button() == 0) {
			dragging = hud;
			grabX = (float) mx - hud.screenX(width);
			grabY = (float) my - hud.screenY(height);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) {
			return false;
		}
		HudFeature hud = dragging;
		layout(hud);
		float w = hud.scaledWidth();
		float h = hud.scaledHeight();
		float x = (float) event.x() - grabX;
		float y = (float) event.y() - grabY;
		guideX = null;
		guideY = null;
		if (!event.hasShiftDown()) {
			float[] sx = snap(x, w, width, true, hud);
			float[] sy = snap(y, h, height, false, hud);
			x = sx[0];
			y = sy[0];
			guideX = Float.isNaN(sx[1]) ? null : sx[1];
			guideY = Float.isNaN(sy[1]) ? null : sy[1];
		}
		hud.moveTo(x, y, width, height);
		return true;
	}

	/** Returns the snapped position and the guide line to draw (NaN for none) along one axis. */
	private float[] snap(float pos, float size, float screen, boolean horizontal, HudFeature self) {
		float best = Float.MAX_VALUE;
		float result = pos;
		float guide = Float.NaN;
		List<float[]> candidates = new ArrayList<>();
		// {position for the widget's start, guide line}
		candidates.add(new float[] {HudFeature.MARGIN, HudFeature.MARGIN});
		candidates.add(new float[] {screen - HudFeature.MARGIN - size, screen - HudFeature.MARGIN});
		candidates.add(new float[] {(screen - size) / 2f, screen / 2f});
		for (HudFeature other : widgets()) {
			if (other == self) {
				continue;
			}
			layout(other);
			float o = horizontal ? other.screenX(width) : other.screenY(height);
			float os = horizontal ? other.scaledWidth() : other.scaledHeight();
			candidates.add(new float[] {o, o});
			candidates.add(new float[] {o + os - size, o + os});
			candidates.add(new float[] {o + os + 3f, o + os + 1.5f});
			candidates.add(new float[] {o - size - 3f, o - 1.5f});
		}
		for (float[] c : candidates) {
			float d = Math.abs(pos - c[0]);
			if (d < SNAP && d < best) {
				best = d;
				result = c[0];
				guide = c[1];
			}
		}
		return new float[] {result, guide};
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guideX = null;
			guideY = null;
			Profiles.save();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		HudFeature hud = at(x, y);
		if (hud == null || scrollY == 0) {
			return false;
		}
		// Keep the widget's centre still while it grows or shrinks.
		float cx = hud.screenX(width) + hud.scaledWidth() / 2f;
		float cy = hud.screenY(height) + hud.scaledHeight() / 2f;
		hud.scale.set(hud.scale.get() + (scrollY > 0 ? 0.05 : -0.05));
		hud.moveTo(cx - hud.scaledWidth() / 2f, cy - hud.scaledHeight() / 2f, width, height);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Profiles.save();
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
