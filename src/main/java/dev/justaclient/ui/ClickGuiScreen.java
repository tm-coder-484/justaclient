package dev.justaclient.ui;

import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.hud.FrameCost;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.Notifications;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.SettingWidgets;
import dev.justaclient.ui.widget.Widget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Right Shift menu: categories and search on the left, the features of the chosen category in the middle, and the
 * selected feature's settings on the right. The Theme page swaps the two right columns for the style token controls.
 */
public final class ClickGuiScreen extends Screen {
	static final float W = 500f;
	static final float H = 290f;
	private static final float SIDEBAR = 108f;
	private static final float LIST_W = 182f;
	private static final float FOOTER = 30f;
	private static final float BODY_H = H - FOOTER;
	private static final float LIST_X = SIDEBAR;
	private static final float SET_X = SIDEBAR + LIST_W;
	private static final float SET_W = W - SET_X;
	private static final float CARD_H = 27f;
	private static final float CARD_GAP = 4f;
	private static final float LIST_TOP = 34f;
	private static final float SET_TOP = 72f;
	private static final float SET_PAD = 14f;
	private static final float ROW_GAP = 7f;

	/** Remembered between openings so the menu comes back where the player left it. */
	private static @Nullable Category lastCategory = Category.HUD;
	private static @Nullable Feature lastFeature;

	private @Nullable Screen parent;
	private final Animation open = new Animation(Easing.CUBIC_OUT, 260, 0f);
	private final Animation pageIn = new Animation(Easing.CUBIC_OUT, 200, 1f);
	private final Animation settingsIn = new Animation(Easing.CUBIC_OUT, 200, 1f);
	private boolean closing;
	private @Nullable Screen closeTo;
	private float originX;
	private float originY;
	private float scale = 1f;

	private @Nullable Category category;
	private @Nullable Feature selected;
	private String query = "";
	private List<Feature> shown = List.of();
	/** The search also matches the Theme page, which is listed after the features. */
	private boolean themeHit;
	/** Showing the Performance page (category is null too, like the Theme page). */
	private boolean perfPage;
	private static boolean lastPerfPage;

	private final Spring listScroll = Spring.ui(0f);
	private float listScrollTarget;
	private final Spring settingsScroll = Spring.ui(0f);
	private float settingsScrollTarget;
	private float settingsContentH;

	private final Map<Feature, Spring> knobs = new HashMap<>();
	private final Map<Feature, Animation> cardHover = new HashMap<>();
	private final Map<Object, Animation> navHover = new HashMap<>();
	private final Spring navMarker = Spring.ui(0f);
	private boolean navMarkerPlaced;

	/** Setting controls, kept per setting so their animations survive re-layout. */
	private final Map<Setting<?>, Widget> settingWidgets = new IdentityHashMap<>();
	private final Map<Widget, Spring> rowY = new IdentityHashMap<>();
	private final List<Widget> visibleSettings = new ArrayList<>();
	private final List<Widget> chrome = new ArrayList<>();
	private final List<Widget> themeWidgets = new ArrayList<>();
	private @Nullable Button resetButton;

	private boolean profileMenu;
	/** Rows after the profile names in the profile menu: new, copy, paste, delete, open folder. */
	private static final int ACTIONS = 5;
	private final Animation profileMenuAnim = new Animation(Easing.CUBIC_OUT, 160, 0f);

	public ClickGuiScreen(@Nullable Screen parent) {
		super(Component.literal("Justaclient"));
		this.parent = parent;
		this.category = lastCategory;
		this.perfPage = lastPerfPage && lastCategory == null;
	}

	/** Opens straight onto one feature's settings, e.g. from the HUD editor. */
	public static ClickGuiScreen showing(@Nullable Screen parent, Feature feature) {
		lastCategory = feature.category;
		lastFeature = feature;
		return new ClickGuiScreen(parent);
	}

	@Nullable Screen parent() {
		return parent;
	}

	/** Swaps the title screen behind the menu, so turning the custom title screen on or off shows at once. */
	public void replaceParentTitle(Screen title) {
		if (parent instanceof TitleScreen || parent instanceof JacTitleScreen) {
			parent = title;
		}
	}

	@Override
	protected void init() {
		chrome.clear();
		chrome.add(new Button(W - 14 - 96, H - FOOTER + 6, 96, 18, Icons.MOVE, "Edit HUD layout", false, this::openHudEditor));
		themeWidgets.clear();
		themeWidgets.addAll(ThemePage.build(LIST_X + 14, 46, SET_X + 12, 46, 160, () -> {
			JacConfig.resetStyle();
			JacConfig.save();
			rebuildWidgets();
		}));
		refreshList();
	}

	private void openHudEditor() {
		saveAll();
		minecraft.setScreen(new HudEditorScreen(this));
	}

	// Data

	private void refreshList() {
		themeHit = !query.isBlank() && ThemePage.matches(query);
		if (!query.isBlank()) {
			shown = Features.all().stream().filter(f -> f.matches(query)).toList();
		} else if (category != null) {
			shown = Features.in(category);
		} else {
			shown = List.of();
		}
		if (category != null || !query.isBlank()) {
			Feature want = selected != null ? selected : lastFeature;
			select(want != null && shown.contains(want) ? want : shown.isEmpty() ? null : shown.getFirst());
		}
		listScrollTarget = 0f;
	}

	private void select(@Nullable Feature feature) {
		if (feature != null && feature == selected && resetButton != null) {
			return;
		}
		selected = feature;
		lastFeature = feature;
		settingWidgets.clear();
		rowY.clear();
		settingsScrollTarget = 0f;
		settingsScroll.snap(0f);
		settingsIn.snap(0f).animateTo(1f);
		resetButton = feature == null ? null : new Button(0, 0, 70, 16, Icons.ROTATE_CCW, "Reset", false, () -> {
			feature.resetToDefaults();
			Profiles.save();
			settingWidgets.clear();
			rowY.clear();
		});
	}

	private void showCategory(@Nullable Category next) {
		showPage(next, false);
	}

	private void showPage(@Nullable Category next, boolean perf) {
		if (next == category && perf == perfPage && query.isBlank()) {
			return;
		}
		perfPage = perf;
		lastPerfPage = perf;
		category = next;
		lastCategory = next;
		query = "";
		pageIn.snap(0f).animateTo(1f);
		selected = null;
		settingWidgets.clear();
		refreshList();
	}

	private List<Widget> settingsLayout() {
		visibleSettings.clear();
		if (selected == null) {
			return visibleSettings;
		}
		float x = SET_X + SET_PAD;
		float w = SET_W - SET_PAD * 2;
		List<Setting<?>> settings = new ArrayList<>(selected.settings());
		settings.add(selected.toggleKey);
		float y = SET_TOP;
		for (Setting<?> setting : settings) {
			if (!setting.isVisible()) {
				continue;
			}
			float rowTop = y;
			Widget widget = settingWidgets.computeIfAbsent(setting, s -> SettingWidgets.create(s, x, rowTop, w));
			// New rows start in place; existing rows glide when a sub-option above them appears or hides.
			rowY.computeIfAbsent(widget, k -> Spring.ui(rowTop)).setTarget(y);
			visibleSettings.add(widget);
			y += widget.height() + ROW_GAP;
		}
		if (resetButton != null) {
			resetButton.setPosition(x, y + 2f);
			y += 20f;
		}
		settingsContentH = y - SET_TOP;
		return visibleSettings;
	}

	// Rendering

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float t = open.animateTo(closing ? 0f : 1f).get();
		if (closing && open.isDone()) {
			minecraft.setScreen(closeTo);
			return;
		}
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
		float radius = JacConfig.get().style.cornerRadius;
		// Footer band and column dividers.
		g.enableScissor(0, (int) BODY_H, (int) W, (int) H);
		Draw.rect(g, 0, 0, W, H, radius, Colors.fade(0x26000000, t));
		g.disableScissor();
		Draw.rect(g, 0, BODY_H, W, 0.75f, 0, Colors.fade(Theme.DIVIDER, t));
		Draw.rect(g, SIDEBAR, 0, 0.75f, BODY_H, 0, Colors.fade(Theme.DIVIDER, t));
		if (!isThemePage() && !isPerfPage()) {
			Draw.rect(g, SET_X, 0, 0.75f, BODY_H, 0, Colors.fade(Theme.DIVIDER, t));
		}

		drawSidebar(g, lx, ly, t);
		if (isThemePage()) {
			drawThemePage(g, lx, ly, t);
		} else if (isPerfPage()) {
			float a = t * pageIn.get();
			PerformancePage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, a);
		} else {
			drawList(g, lx, ly, t);
			drawSettings(g, lx, ly, t);
		}
		drawFooter(g, lx, ly, t);
		drawProfileMenu(g, lx, ly, t);
		g.pose().popMatrix();
	}

	private boolean isThemePage() {
		return category == null && query.isBlank() && !perfPage;
	}

	private boolean isPerfPage() {
		return category == null && query.isBlank() && perfPage;
	}

	private void drawSidebar(GuiGraphics g, double mx, double my, float t) {
		JacText.draw(g, "justaclient", 14, 13, Colors.fade(Theme.TEXT, t), Theme.TITLE, JacText.Weight.SEMIBOLD);

		// Search box: typing anywhere in the menu goes here.
		float sx = 10;
		float sy = 32;
		float sw = SIDEBAR - 20;
		float sh = 17;
		float r = Math.min(5f, JacConfig.get().style.cornerRadius);
		boolean searching = !query.isBlank();
		Draw.rect(g, sx, sy, sw, sh, r, Colors.fade(searching ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		if (searching) {
			Draw.outline(g, sx, sy, sw, sh, r, 0.75f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0x90), t));
		}
		JacText.icon(g, Icons.SEARCH, sx + 6, sy + (sh - 6.5f) / 2f, 6.5f, Colors.fade(Theme.TEXT_DIM, t));
		float ty = sy + (sh - JacText.lineHeight(Theme.LABEL)) / 2f;
		if (searching) {
			String shownQuery = fitTail(query, sw - 26);
			JacText.draw(g, shownQuery, sx + 16, ty, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.REGULAR);
			if (System.currentTimeMillis() / 500 % 2 == 0) {
				float cx = sx + 16 + JacText.width(shownQuery, Theme.LABEL, JacText.Weight.REGULAR) + 0.5f;
				Draw.rect(g, cx, sy + 4, 0.75f, sh - 8, 0, Colors.fade(Theme.TEXT, t));
			}
		} else {
			JacText.draw(g, "Search features", sx + 16, ty, Colors.fade(Theme.TEXT_DIM, t * 0.8f), Theme.LABEL, JacText.Weight.REGULAR);
		}

		// Categories, then Theme below a divider. A single marker slides to the active one.
		List<Object> items = navItems();
		int activeIndex = -1;
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			if (!searching && (item == category || item == ThemePage.class && isThemePage() || item == PerformancePage.class && isPerfPage())) {
				activeIndex = i;
			}
		}
		if (activeIndex >= 0) {
			navMarker.setTarget(navY(activeIndex));
			if (!navMarkerPlaced) {
				navMarker.snap(navY(activeIndex));
				navMarkerPlaced = true;
			}
			float my0 = navMarker.update();
			int accent = JacConfig.accent();
			Draw.rect(g, 10, my0, SIDEBAR - 20, 17, r, Colors.fade(Colors.withAlpha(accent, 0x38), t));
			Draw.rect(g, 10, my0 + 3, 1.5f, 11, 0.75f, Colors.fade(accent, t));
		}
		Draw.rect(g, 14, navY(items.indexOf(ThemePage.class)) - 6, SIDEBAR - 28, 0.75f, 0, Colors.fade(Theme.DIVIDER, t));
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			float y = navY(i);
			boolean hover = mx >= 10 && mx < SIDEBAR - 10 && my >= y && my < y + 17;
			float h = navHover.computeIfAbsent(item, k -> new Animation(Easing.CUBIC_OUT, 120, 0f)).animateTo(hover ? 1f : 0f).get();
			if (h > 0.01f && i != activeIndex) {
				Draw.rect(g, 10, y, SIDEBAR - 20, 17, r, Colors.fade(Colors.withAlpha(0xFFFFFF, 0x0E), t * h));
			}
			boolean active = i == activeIndex;
			Icons icon = item instanceof Category c ? c.icon : item == ThemePage.class ? Icons.PALETTE : Icons.ACTIVITY;
			String label = item instanceof Category c ? c.label : item == ThemePage.class ? "Theme" : "Performance";
			int color = Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, active ? 1f : h * 0.6f);
			JacText.icon(g, icon, 17, y + (17 - 7.5f) / 2f, 7.5f, Colors.fade(active ? JacConfig.accent() : color, t));
			JacText.draw(g, label, 31, y + (17 - JacText.lineHeight(Theme.LABEL)) / 2f, Colors.fade(color, t), Theme.LABEL,
				active ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
		}
	}

	private List<Object> navItems() {
		List<Object> items = new ArrayList<>();
		for (Category c : Category.values()) {
			if (!Features.in(c).isEmpty()) {
				items.add(c);
			}
		}
		items.add(ThemePage.class);
		items.add(PerformancePage.class);
		return items;
	}

	private float navY(int index) {
		int pages = navItems().indexOf(ThemePage.class);
		return 58 + index * 19 + (index >= pages ? 12 : 0);
	}

	private void drawList(GuiGraphics g, double mx, double my, float t) {
		float a = t * pageIn.get();
		float slide = (1f - pageIn.get()) * 6f;
		String title = query.isBlank() ? (category == null ? "" : category.label) : "Search";
		JacText.draw(g, title, LIST_X + 14, 14, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		long on = shown.stream().filter(Feature::isEnabled).count();
		int results = shown.size() + (themeHit ? 1 : 0);
		String count = query.isBlank() ? on + " of " + shown.size() + " on" : results + (results == 1 ? " result" : " results");
		JacText.draw(g, count, SET_X - 14 - JacText.width(count, Theme.SMALL, JacText.Weight.REGULAR), 16, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);

		float viewH = BODY_H - LIST_TOP - 8;
		float contentH = (shown.size() + (themeHit ? 1 : 0)) * (CARD_H + CARD_GAP);
		listScrollTarget = Math.max(0f, Math.min(listScrollTarget, Math.max(0f, contentH - viewH)));
		listScroll.setTarget(listScrollTarget);
		float scroll = listScroll.update();

		g.enableScissor((int) LIST_X, (int) LIST_TOP - 2, (int) SET_X, (int) (BODY_H - 4));
		float cx = LIST_X + 10;
		float cw = LIST_W - 20;
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		boolean inView = my >= LIST_TOP - 2 && my < BODY_H - 4;
		for (int i = 0; i < shown.size(); i++) {
			Feature f = shown.get(i);
			float y = LIST_TOP + i * (CARD_H + CARD_GAP) - scroll + slide * (1 + i * 0.5f);
			if (y + CARD_H < LIST_TOP - 2 || y > BODY_H) {
				continue;
			}
			boolean hover = inView && mx >= cx && mx < cx + cw && my >= y && my < y + CARD_H;
			float h = cardHover.computeIfAbsent(f, k -> new Animation(Easing.CUBIC_OUT, 120, 0f)).animateTo(hover ? 1f : 0f).get();
			boolean sel = f == selected;
			int bg = Colors.lerp(0x10FFFFFF, 0x1CFFFFFF, Math.max(h, sel ? 1f : 0f));
			Draw.rect(g, cx, y, cw, CARD_H, radius, Colors.fade(bg, a));
			if (sel) {
				Draw.outline(g, cx, y, cw, CARD_H, radius, 0.85f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0xB0), a));
			}
			JacText.draw(g, f.name, cx + 9, y + 5.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			String sub = query.isBlank() ? f.description : f.category.label + " · " + f.description;
			JacText.draw(g, fitEnd(sub, cw - 50), cx + 9, y + 15f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			drawSwitch(g, f, cx + cw - 9 - 20, y + (CARD_H - 11) / 2f, a);
		}
		if (themeHit) {
			float y = LIST_TOP + shown.size() * (CARD_H + CARD_GAP) - scroll + slide;
			boolean hover = inView && mx >= cx && mx < cx + cw && my >= y && my < y + CARD_H;
			Draw.rect(g, cx, y, cw, CARD_H, radius, Colors.fade(hover ? 0x1CFFFFFF : 0x10FFFFFF, a));
			JacText.draw(g, "Theme", cx + 9, y + 5.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			JacText.draw(g, "Accent, panels, blur, font and motion", cx + 9, y + 15f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			JacText.icon(g, Icons.CHEVRON_RIGHT, cx + cw - 9 - 8, y + (CARD_H - 8) / 2f, 8f, Colors.fade(Theme.TEXT_DIM, a));
		}
		g.disableScissor();
		if (shown.isEmpty() && !themeHit && !query.isBlank()) {
			JacText.drawCentered(g, "Nothing matches \"" + fitTail(query, 100) + "\"", LIST_X + LIST_W / 2f, LIST_TOP + 20,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
	}

	private void drawSwitch(GuiGraphics g, Feature f, float x, float y, float alpha) {
		Spring knob = knobs.computeIfAbsent(f, k -> Spring.ui(f.isEnabled() ? 1f : 0f));
		knob.setTarget(f.isEnabled() ? 1f : 0f);
		float k = Math.max(0f, Math.min(1f, knob.update()));
		float w = 20f;
		float h = 11f;
		Draw.rect(g, x, y, w, h, h / 2f, Colors.fade(Colors.lerp(Theme.CONTROL_HOVER, JacConfig.accent(), k), alpha));
		float r = h / 2f - 1.5f;
		float cx = x + h / 2f + (w - h) * knob.get();
		Draw.shadow(g, cx - r, y + 1.5f, r * 2, r * 2, r, 2f, Colors.fade(0x50000000, alpha));
		Draw.circle(g, cx, y + h / 2f, r, Colors.fade(Theme.KNOB, alpha));
	}

	private void drawSettings(GuiGraphics g, double mx, double my, float t) {
		Feature f = selected;
		if (f == null) {
			return;
		}
		float a = t * settingsIn.get();
		float px = SET_X + SET_PAD;
		float slide = (1f - settingsIn.get()) * 6f;
		f.drawPreview(g, px, 14 + slide, 46, 46);
		float tx = px + 46 + 10;
		JacText.draw(g, f.name, tx, 25 + slide, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, fitEnd(f.description, W - tx - SET_PAD), tx, 37 + slide, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		if (f instanceof HudFeature) {
			JacText.draw(g, "Drag it in Edit HUD layout", tx, 46 + slide, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0xE0), a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}

		List<Widget> rows = settingsLayout();
		float viewH = BODY_H - SET_TOP - 6;
		settingsScrollTarget = Math.max(0f, Math.min(settingsScrollTarget, Math.max(0f, settingsContentH - viewH)));
		settingsScroll.setTarget(settingsScrollTarget);
		float scroll = settingsScroll.update();
		g.enableScissor((int) SET_X, (int) SET_TOP - 4, (int) W, (int) (BODY_H - 2));
		double sy = my >= SET_TOP - 4 && my < BODY_H - 2 ? my + scroll : -1e4;
		g.pose().pushMatrix();
		g.pose().translate(0, -scroll + slide);
		for (Widget w : rows) {
			Spring ys = rowY.get(w);
			w.setPosition(px, ys.update());
			w.render(g, mx, sy, a);
		}
		if (resetButton != null) {
			resetButton.render(g, mx, sy, a);
		}
		g.pose().popMatrix();
		g.disableScissor();
		if (settingsContentH > viewH) {
			float barH = Math.max(16f, viewH * viewH / settingsContentH);
			float barY = SET_TOP + (viewH - barH) * (scroll / Math.max(1f, settingsContentH - viewH));
			Draw.rect(g, W - 5, barY, 2f, barH, 1f, Colors.fade(0x30FFFFFF, a));
		}
	}

	private void drawThemePage(GuiGraphics g, double mx, double my, float t) {
		float a = t * pageIn.get();
		JacText.draw(g, "Theme", LIST_X + 14, 14, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String hint = "Every colour in the client follows these";
		JacText.draw(g, hint, W - 14 - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), 16, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		Draw.rect(g, SET_X, 46, 0.75f, 180, 0, Colors.fade(Theme.DIVIDER, a));
		for (Widget w : themeWidgets) {
			w.render(g, mx, my, a);
		}
		ThemePage.drawPreview(g, SET_X + 10, 196, a);
	}

	private void drawFooter(GuiGraphics g, double mx, double my, float t) {
		float y = H - FOOTER;
		float ty = y + (FOOTER - JacText.lineHeight(Theme.LABEL)) / 2f;
		JacText.draw(g, "Profile", 14, ty, Colors.fade(Theme.TEXT_DIM, t), Theme.LABEL, JacText.Weight.REGULAR);
		float bx = 46;
		float bw = profileButtonWidth();
		boolean hover = !profileMenu && mx >= bx && mx < bx + bw && my >= y + 6 && my < y + 24;
		float r = Math.min(5f, JacConfig.get().style.cornerRadius);
		Draw.rect(g, bx, y + 6, bw, 18, r, Colors.fade(hover || profileMenu ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		JacText.draw(g, Profiles.active(), bx + 8, ty, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.SEMIBOLD);
		JacText.icon(g, Icons.CHEVRON_DOWN, bx + bw - 14, y + 11.5f, 7f, Colors.fade(Theme.TEXT_DIM, t));

		String cost = costLabel();
		float costX = W - 14 - 96 - 10 - JacText.width(cost, Theme.SMALL, JacText.Weight.REGULAR);
		boolean costHover = !profileMenu && mx >= costX && mx < W - 14 - 96 - 10 && my >= y + 6 && my < y + 24;
		JacText.draw(g, cost, costX, y + (FOOTER - JacText.lineHeight(Theme.SMALL)) / 2f,
			Colors.fade(costHover ? Theme.TEXT : Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		for (Widget w : chrome) {
			w.render(g, mx, profileMenu ? -1e4 : my, t);
		}
	}

	private static String costLabel() {
		return String.format("Frame cost %.1f%%", FrameCost.share() * 100f);
	}

	private float profileButtonWidth() {
		return Math.max(64f, JacText.width(Profiles.active(), Theme.LABEL, JacText.Weight.SEMIBOLD) + 30f);
	}

	private List<String> profileMenuItems() {
		List<String> items = new ArrayList<>(Profiles.list());
		items.add("+");
		items.add("e");
		items.add("i");
		items.add("-");
		items.add("/");
		return items;
	}

	private float profileMenuHeight() {
		return profileMenuItems().size() * 15f + 10f;
	}

	private void drawProfileMenu(GuiGraphics g, double mx, double my, float t) {
		float p = profileMenuAnim.animateTo(profileMenu ? 1f : 0f).get();
		if (p <= 0.01f) {
			return;
		}
		List<String> items = profileMenuItems();
		float w = 120f;
		float h = profileMenuHeight();
		float x = 46;
		float y = H - FOOTER - h + 2 + (1f - p) * 6f;
		g.nextStratum();
		float a = t * p;
		Draw.panel(g, x, y, w, h, a);
		int profiles = items.size() - ACTIONS;
		for (int i = 0; i < items.size(); i++) {
			String item = items.get(i);
			float iy = y + 5 + i * 15f;
			boolean hover = profileMenu && mx >= x + 4 && mx < x + w - 4 && my >= iy && my < iy + 14;
			boolean disabled = item.equals("-") && profiles <= 1;
			if (hover && !disabled) {
				Draw.rect(g, x + 4, iy, w - 8, 14, 4f, Colors.fade(Theme.CONTROL_HOVER, a));
			}
			float ty = iy + (14 - JacText.lineHeight(Theme.LABEL)) / 2f;
			int color = Colors.fade(disabled ? 0x60FFFFFF : Theme.TEXT, a);
			switch (item) {
				case "+" -> menuRow(g, Icons.PLUS, "New profile (copy)", x, iy, ty, color);
				case "e" -> menuRow(g, Icons.UPLOAD, "Copy to clipboard", x, iy, ty, color);
				case "i" -> menuRow(g, Icons.CLIPBOARD_PASTE, "Paste from clipboard", x, iy, ty, color);
				case "-" -> menuRow(g, Icons.TRASH, "Delete this profile", x, iy, ty, color);
				case "/" -> menuRow(g, Icons.LAYERS, "Open profiles folder", x, iy, ty, color);
				default -> {
					boolean active = item.equals(Profiles.active());
					if (active) {
						JacText.icon(g, Icons.CHECK, x + 9, iy + 3.5f, 7f, Colors.fade(JacConfig.accent(), a));
					}
					JacText.draw(g, fitEnd(item, w - 34), x + 21, ty, color, Theme.LABEL, active ? JacText.Weight.SEMIBOLD : JacText.Weight.REGULAR);
				}
			}
			if (i == profiles - 1) {
				Draw.rect(g, x + 8, iy + 14.5f, w - 16, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
			}
		}
	}

	private static void menuRow(GuiGraphics g, Icons icon, String label, float x, float iy, float ty, int color) {
		JacText.icon(g, icon, x + 9, iy + 3.5f, 7f, color);
		JacText.draw(g, label, x + 21, ty, color, Theme.LABEL, JacText.Weight.REGULAR);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			renderPanorama(g, partialTick);
		}
	}

	// Input

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
		double mx = toLocalX(event.x());
		double my = toLocalY(event.y());
		int button = event.button();

		if (profileMenu) {
			clickProfileMenu(mx, my);
			return true;
		}
		// Footer.
		if (button == 0 && mx >= 46 && mx < 46 + profileButtonWidth() && my >= H - FOOTER + 6 && my < H - 6) {
			profileMenu = true;
			return true;
		}
		for (Widget w : chrome) {
			if (w.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		float costRight = W - 14 - 96 - 10;
		if (button == 0 && mx >= costRight - JacText.width(costLabel(), Theme.SMALL, JacText.Weight.REGULAR) && mx < costRight && my >= H - FOOTER + 6
			&& my < H - 6) {
			query = "";
			showPage(null, true);
			return true;
		}
		// Sidebar.
		List<Object> items = navItems();
		for (int i = 0; i < items.size(); i++) {
			float y = navY(i);
			if (button == 0 && mx >= 10 && mx < SIDEBAR - 10 && my >= y && my < y + 17) {
				showPage(items.get(i) instanceof Category c ? c : null, items.get(i) == PerformancePage.class);
				return true;
			}
		}
		if (button == 1 && mx >= 10 && mx < SIDEBAR - 10 && my >= 32 && my < 49) {
			query = "";
			refreshList();
			return true;
		}
		if (isThemePage()) {
			for (Widget w : themeWidgets) {
				if (w.mouseClicked(mx, my, button)) {
					JacConfig.save();
					return true;
				}
			}
			return super.mouseClicked(event, doubleClick);
		}
		// Feature cards: click selects, the switch (or a right click) toggles.
		if (mx >= LIST_X + 10 && mx < SET_X - 10 && my >= LIST_TOP - 2 && my < BODY_H - 4) {
			float scroll = listScroll.get();
			int index = (int) Math.floor((my - LIST_TOP + scroll) / (CARD_H + CARD_GAP));
			float y = LIST_TOP + index * (CARD_H + CARD_GAP) - scroll;
			if (themeHit && index == shown.size() && my < y + CARD_H && button == 0) {
				showCategory(null);
				return true;
			}
			if (index >= 0 && index < shown.size() && my < y + CARD_H) {
				Feature f = shown.get(index);
				boolean onSwitch = mx >= SET_X - 10 - 9 - 24;
				if (button == 1 || button == 0 && onSwitch) {
					f.toggle();
					Profiles.save();
				}
				if (button == 0) {
					select(f);
				}
				return true;
			}
		}
		// Settings.
		if (mx >= SET_X && my >= SET_TOP - 4 && my < BODY_H - 2) {
			double sy = my + settingsScroll.get();
			boolean used = false;
			for (Widget w : new ArrayList<>(visibleSettings)) {
				used |= w.mouseClicked(mx, sy, button);
			}
			if (resetButton != null && resetButton.mouseClicked(mx, sy, button)) {
				used = true;
			}
			if (used) {
				Profiles.save();
				return true;
			}
		} else {
			// Clicking elsewhere stops any key binder that is waiting.
			visibleSettings.forEach(w -> w.mouseClicked(-1e4, -1e4, button));
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void clickProfileMenu(double mx, double my) {
		List<String> items = profileMenuItems();
		float w = 120f;
		float h = profileMenuHeight();
		float x = 46;
		float y = H - FOOTER - h + 2;
		profileMenu = false;
		if (mx < x || mx >= x + w || my < y || my >= y + h) {
			return;
		}
		int i = (int) Math.floor((my - y - 5) / 15f);
		if (i < 0 || i >= items.size()) {
			return;
		}
		String item = items.get(i);
		switch (item) {
			case "+" -> Profiles.duplicate();
			case "e" -> {
				Profiles.exportToClipboard();
				Notifications.push(Icons.UPLOAD, Profiles.active(), "copied to the clipboard");
			}
			case "i" -> {
				String imported = Profiles.importFromClipboard();
				if (imported == null) {
					Notifications.push(Icons.INFO, "No profile", "on the clipboard");
				} else {
					Notifications.push(Icons.CLIPBOARD_PASTE, imported, "imported");
					rebuildWidgets();
				}
			}
			case "-" -> Profiles.deleteActive();
			case "/" -> {
				Profiles.save();
				Util.getPlatform().openPath(Profiles.DIR);
			}
			default -> Profiles.switchTo(item);
		}
		knobs.clear();
		settingWidgets.clear();
		rowY.clear();
		refreshList();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		double mx = toLocalX(event.x());
		double my = toLocalY(event.y());
		if (isThemePage()) {
			themeWidgets.forEach(w -> w.mouseDragged(mx, my));
		} else {
			double sy = my + settingsScroll.get();
			visibleSettings.forEach(w -> w.mouseDragged(mx, sy));
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		double mx = toLocalX(event.x());
		double my = toLocalY(event.y());
		themeWidgets.forEach(w -> w.mouseReleased(mx, my));
		double sy = my + settingsScroll.get();
		visibleSettings.forEach(w -> w.mouseReleased(mx, sy));
		saveAll();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double mx = toLocalX(x);
		double my = toLocalY(y);
		if (mx >= LIST_X && mx < SET_X && !isThemePage()) {
			listScrollTarget -= (float) scrollY * 24f;
			return true;
		}
		if (mx >= SET_X && !isThemePage()) {
			settingsScrollTarget -= (float) scrollY * 24f;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		for (Widget w : visibleSettings) {
			if (w.capturesKeys()) {
				w.keyPressed(event.key());
				Profiles.save();
				return true;
			}
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (profileMenu) {
				profileMenu = false;
			} else if (!query.isEmpty()) {
				query = "";
				refreshList();
			} else {
				onClose();
			}
			return true;
		}
		if (Justaclient.OPEN_MENU.matches(event) && query.isEmpty()) {
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
			query = event.hasControlDown() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1));
			refreshList();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		for (Widget w : visibleSettings) {
			if (w.capturesKeys()) {
				if (event.isAllowedChatCharacter() && w.charTyped(event.codepoint())) {
					Profiles.save();
				}
				return true;
			}
		}
		if (!event.isAllowedChatCharacter() || query.length() >= 32) {
			return false;
		}
		if (query.isEmpty() && event.codepoint() == ' ') {
			return true;
		}
		query += event.codepointAsString();
		refreshList();
		return true;
	}

	private void saveAll() {
		JacConfig.save();
		Profiles.save();
	}

	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			closeTo = parent;
			saveAll();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// Text fitting

	private static String fitEnd(String text, float maxWidth) {
		if (JacText.width(text, Theme.SMALL, JacText.Weight.REGULAR) <= maxWidth) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && JacText.width(s + "...", Theme.SMALL, JacText.Weight.REGULAR) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s.stripTrailing() + "...";
	}

	private static String fitTail(String text, float maxWidth) {
		String s = text;
		while (s.length() > 1 && JacText.width(s, Theme.LABEL, JacText.Weight.REGULAR) > maxWidth) {
			s = s.substring(1);
		}
		return s;
	}
}
