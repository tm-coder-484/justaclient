package dev.justaclient.ui;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.common.hud.HudSnap;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.HudEditorFeature;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.HudLayouts;
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
 * Drag HUD widgets into place. Widgets snap to the screen edges, the centre lines and each other's edges with a small gap
 * (hold Shift to place freely). Drag a corner or scroll to scale, an edge bar to resize, press A over a widget to change which point is
 * anchored, right click for its settings. The Layout menu switches between saved layouts.
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
	private float[] layoutBounds = new float[4];
	private float[] hideBounds = new float[4];
	private float[] showBounds = new float[4];
	private boolean layoutMenu;
	private final List<float[]> layoutRows = new ArrayList<>();
	private final List<Runnable> layoutActions = new ArrayList<>();
	/** Corner being dragged to resize: 0 top left, 1 top right, 2 bottom right, 3 bottom left; -1 when moving. */
	private int corner = -1;
	/** Edge being dragged to resize a freely sized widget: 0 top, 1 right, 2 bottom, 3 left; -1 when not. */
	private int edge = -1;
	private float fixedX;
	private float fixedY;
	private static final float HANDLE = 4f;
	private static final String[] ANCHOR_NAMES = {"top left", "top", "top right", "left", "centre", "right", "bottom left", "bottom", "bottom right"};

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

	private static HudEditorFeature editor() {
		return Features.get(HudEditorFeature.class);
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
		}
		// Labels go on top of every widget, so a neighbour never hides one.
		g.nextStratum();
		for (HudFeature hud : widgets()) {
			Animation anim = hover.get(hud);
			float hv = anim == null ? 0f : anim.get();
			if (hv <= 0.01f) {
				continue;
			}
			layout(hud);
			float x = hud.screenX(width);
			float y = hud.screenY(height);
			float w = hud.scaledWidth();
			float h = hud.scaledHeight();
			if (hv > 0.01f && editor().resizeHandles()) {
				float[][] corners = {{x, y}, {x + w, y}, {x + w, y + h}, {x, y + h}};
				for (float[] c : corners) {
					Draw.rect(g, c[0] - HANDLE / 2f, c[1] - HANDLE / 2f, HANDLE, HANDLE, 1f, Colors.fade(0xFFFFFFFF, t * hv));
					Draw.outline(g, c[0] - HANDLE / 2f, c[1] - HANDLE / 2f, HANDLE, HANDLE, 1f, 0.5f, Colors.fade(accent, t * hv));
				}
			}
			if (hv > 0.01f && editor().resizeHandles()) {
				for (int e = 0; e < 4; e++) {
					float[] r = edgeHandle(hud, e, x, y, w, h);
					if (r != null) {
						Draw.rect(g, r[0], r[1], r[2], r[3], 1f, Colors.fade(accent, t * hv));
					}
				}
			}
			if (hv > 0.01f && !Float.isNaN(hud.anchorX)) {
				Draw.circle(g, x + hud.anchorX * w, y + hud.anchorY * h, 2.5f, Colors.fade(accent, t * hv));
				Draw.circle(g, x + hud.anchorX * w, y + hud.anchorY * h, 1.2f, Colors.fade(0xFFFFFFFF, t * hv));
			}
			String label = hud.name + "  " + Math.round(hud.scale.f() * 100) + "%" + (Float.isNaN(hud.anchorX) ? "" : "  ·  " + anchorName(hud));
			float lw = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 8;
			float ly = y - 12 < 2 ? y + h + 3 : y - 12;
			float lx = Math.min(x, width - lw - 2);
			Draw.rect(g, lx, ly, lw, 10, 3f, Colors.fade(Colors.withAlpha(accent, 0xE0), t * hv));
			JacText.draw(g, label, lx + 4, ly + (10 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(0xFFFFFFFF, t * hv), Theme.SMALL,
				JacText.Weight.SEMIBOLD);
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
		drawLayoutMenu(g, mouseX, mouseY, t);
	}

	private static String anchorName(HudFeature hud) {
		int col = Math.round(hud.anchorX * 2);
		int row = Math.round(hud.anchorY * 2);
		return ANCHOR_NAMES[row * 3 + col];
	}

	private void drawLayoutMenu(GuiGraphics g, double mx, double my, float t) {
		layoutRows.clear();
		layoutActions.clear();
		if (!layoutMenu) {
			return;
		}
		List<HudLayouts.Layout> layouts = HudLayouts.all();
		HudLayouts.Layout active = layouts.stream().filter(l -> l.name.equals(HudLayouts.active())).findFirst().orElse(null);
		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		List<HudLayouts.Layout> rowLayouts = new ArrayList<>();
		for (HudLayouts.Layout l : layouts) {
			labels.add(l.name);
			actions.add(() -> HudLayouts.apply(l));
			rowLayouts.add(l);
		}
		labels.add("+ Save current as a new layout");
		actions.add(HudLayouts::saveCurrent);
		rowLayouts.add(null);
		if (active != null && !HudLayouts.isBuiltIn(active)) {
			labels.add("Update " + active.name);
			actions.add(() -> HudLayouts.overwrite(active));
			rowLayouts.add(null);
		}
		float x = layoutBounds[0];
		float y = layoutBounds[1] + layoutBounds[3] + 3;
		float w = 150;
		float h = labels.size() * 13f + 6;
		Draw.panel(g, x, y, w, h, t);
		for (int i = 0; i < labels.size(); i++) {
			float iy = y + 3 + i * 13f;
			boolean hov = mx >= x + 3 && mx < x + w - 3 && my >= iy && my < iy + 13;
			if (hov) {
				Draw.rect(g, x + 3, iy, w - 6, 13, 4f, Colors.fade(Theme.CONTROL_HOVER, t));
			}
			HudLayouts.Layout l = rowLayouts.get(i);
			boolean isActive = l != null && l.name.equals(HudLayouts.active());
			if (isActive) {
				JacText.icon(g, Icons.CHECK, x + 7, iy + 3.5f, 6f, Colors.fade(JacConfig.accent(), t));
			}
			JacText.draw(g, labels.get(i), x + 17, iy + (13 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(l == null ? Theme.TEXT_DIM : Theme.TEXT, t),
				Theme.SMALL, isActive ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
			if (l != null && !HudLayouts.isBuiltIn(l)) {
				boolean hx = hov && mx >= x + w - 16;
				JacText.icon(g, Icons.X, x + w - 12, iy + 3.5f, 6f, Colors.fade(hx ? 0xFFF87171 : Theme.TEXT_DIM, t));
				layoutRows.add(new float[] {x + w - 16, iy, 13, 13});
				layoutActions.add(() -> HudLayouts.delete(l));
			}
			layoutRows.add(new float[] {x + 3, iy, w - 6, 13});
			layoutActions.add(actions.get(i));
		}
	}

	private static boolean barHidden() {
		return JacConfig.get().hudEditorBarHidden;
	}

	private static void setBarHidden(boolean hidden) {
		JacConfig.get().hudEditorBarHidden = hidden;
		JacConfig.save();
	}

	/** The folded bar: a small tab at the top that brings it back. Its buttons get no bounds, so nothing hidden can be clicked. */
	private void drawFoldedBar(GuiGraphics g, double mx, double my, float t) {
		layoutBounds = new float[4];
		resetBounds = new float[4];
		doneBounds = new float[4];
		hideBounds = new float[4];
		chipBounds.clear();
		chipFeatures.clear();
		String label = "Show toolbar (H)";
		float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 22;
		float x = (width - w) / 2f;
		showBounds = new float[] {x, 0, w, 14};
		boolean hot = inside(showBounds, mx, my);
		if (dragging != null) {
			t *= 0.35f;
		}
		Draw.rect(g, x, -6, w, 20, 6f, Colors.fade(hot ? Colors.withAlpha(JacConfig.accent(), 0xD0) : 0xB0101218, t));
		JacText.icon(g, Icons.CHEVRON_DOWN, x + 7, 4f, 6f, Colors.fade(hot ? 0xFFFFFFFF : Theme.TEXT_DIM, t));
		JacText.draw(g, label, x + 16, 7 - JacText.lineHeight(Theme.SMALL) / 2f + 0.5f, Colors.fade(hot ? 0xFFFFFFFF : Theme.TEXT, t), Theme.SMALL,
			JacText.Weight.SEMIBOLD);
	}

	private void drawToolbar(GuiGraphics g, double mx, double my, float t) {
		if (barHidden()) {
			drawFoldedBar(g, mx, my, t);
			return;
		}
		showBounds = new float[4];
		List<HudFeature> off = Features.hud().stream().filter(h -> !h.isEnabled()).toList();
		String hint = "Drag to move  ·  Corner or scroll: scale  ·  Edge bar: resize  ·  A: anchor  ·  Right click: settings  ·  Shift: no snap  ·  H: hide bar";
		float hintW = JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR);
		float chipsW = 0f;
		for (HudFeature hud : off) {
			chipsW += JacText.width(hud.name, Theme.SMALL, JacText.Weight.SEMIBOLD) + 18 + 4;
		}
		float buttons = 44 + 4 + 40;
		String layoutLabel = "Layout: " + HudLayouts.active();
		float layoutW = JacText.width(layoutLabel, Theme.SMALL, JacText.Weight.SEMIBOLD) + 20;
		float w = Math.min(width - 8, Math.max(hintW + 24, layoutW + 6 + chipsW - 4 + (off.isEmpty() ? 0 : 6) + buttons) + 16);
		float x = (width - w) / 2f;
		float y = 8f;
		if (dragging != null) {
			t *= 0.35f;
		}
		Draw.panel(g, x, y, w, BAR_H * 2 - 4, t);
		JacText.drawCentered(g, fitHint(hint, w - 30), width / 2f, y + 6, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		// Fold the bar away to see what is under it; H or the tab brings it back.
		hideBounds = new float[] {x + w - 16, y + 3, 12, 12};
		boolean hh = inside(hideBounds, mx, my);
		if (hh) {
			Draw.rect(g, hideBounds[0], hideBounds[1], 12, 12, 3f, Colors.fade(Theme.CONTROL_HOVER, t));
		}
		JacText.icon(g, Icons.X, x + w - 13.5f, y + 5.5f, 7f, Colors.fade(hh ? Theme.TEXT : Theme.TEXT_DIM, t));
		float rowY = y + BAR_H - 4;
		float cx = x + 8;
		layoutBounds = new float[] {cx, rowY, layoutW, 14};
		boolean hl = inside(layoutBounds, mx, my) || layoutMenu;
		Draw.rect(g, cx, rowY, layoutW, 14, 4f, Colors.fade(hl ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		JacText.draw(g, layoutLabel, cx + 6, rowY + (14 - JacText.lineHeight(Theme.SMALL)) / 2f, Colors.fade(Theme.TEXT, t), Theme.SMALL,
			JacText.Weight.SEMIBOLD);
		JacText.icon(g, Icons.CHEVRON_DOWN, cx + layoutW - 11, rowY + 4, 6f, Colors.fade(Theme.TEXT_DIM, t));
		cx += layoutW + 6;
		chipBounds.clear();
		chipFeatures.clear();
		float chipsEnd = x + w - 8 - buttons - 6;
		for (HudFeature hud : off) {
			float cw = JacText.width(hud.name, Theme.SMALL, JacText.Weight.SEMIBOLD) + 18;
			if (cx + cw > chipsEnd) {
				break;
			}
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

	private static String fitHint(String hint, float max) {
		return JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR) <= max ? hint : JacScreen.fit(hint, Theme.SMALL, JacText.Weight.REGULAR, max);
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
		if (layoutMenu) {
			layoutMenu = false;
			for (int i = 0; i < layoutRows.size(); i++) {
				if (inside(layoutRows.get(i), mx, my)) {
					layoutActions.get(i).run();
					return true;
				}
			}
			if (inside(layoutBounds, mx, my)) {
				return true;
			}
		}
		if (event.button() == 0 && inside(showBounds, mx, my)) {
			setBarHidden(false);
			return true;
		}
		if (event.button() == 0 && inside(hideBounds, mx, my)) {
			layoutMenu = false;
			setBarHidden(true);
			return true;
		}
		if (event.button() == 0 && inside(layoutBounds, mx, my)) {
			layoutMenu = true;
			return true;
		}
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
					chipFeatures.get(i).toggle();
					Profiles.save();
					return true;
				}
			}
		}
		if (event.button() == 0 && editor().resizeHandles()) {
			for (HudFeature w : widgets()) {
				layout(w);
				float x = w.screenX(width);
				float y = w.screenY(height);
				for (int e = 0; e < 4; e++) {
					float[] r = edgeHandle(w, e, x, y, w.scaledWidth(), w.scaledHeight());
					if (r != null && mx >= r[0] - 2 && mx < r[0] + r[2] + 2 && my >= r[1] - 2 && my < r[1] + r[3] + 2) {
						dragging = w;
						corner = -1;
						edge = e;
						fixedX = e == 3 ? x + w.scaledWidth() : x;
						fixedY = e == 0 ? y + w.scaledHeight() : y;
						return true;
					}
				}
				float[][] corners = {{x, y}, {x + w.scaledWidth(), y}, {x + w.scaledWidth(), y + w.scaledHeight()}, {x, y + w.scaledHeight()}};
				for (int c = 0; c < 4; c++) {
					if (Math.abs(mx - corners[c][0]) <= HANDLE && Math.abs(my - corners[c][1]) <= HANDLE) {
						dragging = w;
						corner = c;
						float[] opposite = corners[(c + 2) % 4];
						fixedX = opposite[0];
						fixedY = opposite[1];
						return true;
					}
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
			corner = -1;
			edge = -1;
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
		if (corner >= 0) {
			resize(hud, (float) event.x(), (float) event.y());
			return true;
		}
		if (edge >= 0) {
			resizeEdge(hud, (float) event.x(), (float) event.y());
			return true;
		}
		float w = hud.scaledWidth();
		float h = hud.scaledHeight();
		float x = (float) event.x() - grabX;
		float y = (float) event.y() - grabY;
		guideX = null;
		guideY = null;
		if (!event.hasShiftDown() && editor().snaps()) {
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

	/** Scales the widget so the dragged corner follows the mouse while the opposite corner stays put. */
	private void resize(HudFeature hud, float mx, float my) {
		float baseW = hud.width();
		float baseH = hud.height();
		if (baseW <= 0 || baseH <= 0) {
			return;
		}
		float want = (Math.abs(mx - fixedX) / baseW + Math.abs(my - fixedY) / baseH) / 2f;
		// The corner follows the mouse in drawn size; the Scale setting leaves out the small-screen fit.
		want /= dev.justaclient.hud.HudFit.factor();
		double snapped = Math.round(want / 0.05) * 0.05;
		hud.scale.set(snapped);
		float w = hud.scaledWidth();
		float h = hud.scaledHeight();
		boolean left = corner == 0 || corner == 3;
		boolean top = corner == 0 || corner == 1;
		hud.moveTo(left ? fixedX - w : fixedX, top ? fixedY - h : fixedY, width, height);
	}

	/** The bar drawn in the middle of an edge that can be dragged, as {x, y, w, h}, or null if that edge is fixed. */
	private static float @org.jspecify.annotations.Nullable [] edgeHandle(HudFeature hud, int e, float x, float y, float w, float h) {
		boolean horizontal = e == 1 || e == 3;
		if ((horizontal ? hud.freeWidth() : hud.freeHeight()) == null) {
			return null;
		}
		return switch (e) {
			case 0 -> new float[] {x + w / 2f - 5, y - 1.25f, 10, 2.5f};
			case 1 -> new float[] {x + w - 1.25f, y + h / 2f - 5, 2.5f, 10};
			case 2 -> new float[] {x + w / 2f - 5, y + h - 1.25f, 10, 2.5f};
			default -> new float[] {x - 1.25f, y + h / 2f - 5, 2.5f, 10};
		};
	}

	/** Changes a freely sized widget's width or height so the dragged edge follows the mouse and the opposite edge stays. */
	private void resizeEdge(HudFeature hud, float mx, float my) {
		boolean horizontal = edge == 1 || edge == 3;
		var setting = horizontal ? hud.freeWidth() : hud.freeHeight();
		if (setting == null) {
			return;
		}
		float scale = hud.drawScale();
		float now = horizontal ? hud.scaledWidth() : hud.scaledHeight();
		float want = horizontal ? Math.abs(mx - fixedX) : Math.abs(my - fixedY);
		setting.set(setting.get() + (want - now) / scale);
		layout(hud);
		// fixedX and fixedY hold the edges that stay put: the left or right one, and the top or bottom one.
		float x = edge == 3 ? fixedX - hud.scaledWidth() : fixedX;
		float y = edge == 0 ? fixedY - hud.scaledHeight() : fixedY;
		hud.moveTo(x, y, width, height);
	}

	/** Returns the snapped position and the guide line to draw (NaN for none) along one axis. */
	private float[] snap(float pos, float size, float screen, boolean horizontal, HudFeature self) {
		List<HudSnap.Span> others = new ArrayList<>();
		for (HudFeature other : widgets()) {
			if (other == self) {
				continue;
			}
			layout(other);
			others.add(new HudSnap.Span(horizontal ? other.screenX(width) : other.screenY(height),
				horizontal ? other.scaledWidth() : other.scaledHeight()));
		}
		return HudSnap.snap(pos, size, screen, HudFeature.MARGIN, editor().gap(), editor().snapsToCentres(), others, SNAP);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			if (corner < 0 && editor().autoAnchors()) {
				layout(dragging);
				dragging.anchorToNearest(width, height);
			}
			dragging = null;
			corner = -1;
			edge = -1;
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
			if (layoutMenu) {
				layoutMenu = false;
				return true;
			}
			onClose();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_H) {
			layoutMenu = false;
			setBarHidden(!barHidden());
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_A) {
			double mx = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
			double my = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
			HudFeature hud = at(mx, my);
			if (hud != null) {
				layout(hud);
				int index = Float.isNaN(hud.anchorX) ? -1 : Math.round(hud.anchorY * 2) * 3 + Math.round(hud.anchorX * 2);
				int next = (index + 1) % 9;
				hud.setAnchor((next % 3) / 2f, (next / 3) / 2f, width, height);
				Profiles.save();
				return true;
			}
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
