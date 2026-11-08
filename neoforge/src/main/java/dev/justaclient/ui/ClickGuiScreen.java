package dev.justaclient.ui;

import dev.justaclient.Justaclient;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.common.ui.Fit;
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
import dev.justaclient.platform.input.CharacterEvent;
import dev.justaclient.platform.input.KeyEvent;
import dev.justaclient.platform.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Right Shift menu: categories and search on the left, the features of the chosen category in the middle, and the
 * selected feature's settings on the right. The Theme page swaps the two right columns for the style token controls.
 */
public final class ClickGuiScreen extends dev.justaclient.platform.PlatformScreen {
	/** The smallest panel; it grows taller to fit the sidebar and wider when the window's shape leaves room. */
	private static final float MIN_W = 500f;
	private static final float MIN_H = 290f;
	private static final float MAX_W = 640f;
	private static final float SIDEBAR = 108f;
	private static final float MIN_LIST_W = 182f;
	private static final float FOOTER = 30f;
	private static final float LIST_X = SIDEBAR;
	private static final float NAV_TOP = 58f;
	/** Sidebar rows: at least this far apart, so a row's highlight never touches the next one. */
	private static final float NAV_MIN_STEP = 17f;
	private static final float NAV_MAX_STEP = 19f;
	private static final float NAV_GAP = 2f;
	private static final float NAV_DIVIDER = 10f;
	// Panel size, worked out in init() from the window and the number of sidebar rows.
	private float W = MIN_W;
	private float H = MIN_H;
	private float BODY_H = MIN_H - FOOTER;
	private float LIST_W = MIN_LIST_W;
	private float SET_X = SIDEBAR + MIN_LIST_W;
	private float SET_W = MIN_W - SET_X;
	/** Feature cards have room for a two-line description. */
	private static final float CARD_H = 33f;
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
	/** Which page shows when no category is picked: the Theme, Performance or Stats page. */
	static final int THEME = 0;
	static final int PERF = 1;
	static final int STATS = 2;
	static final int NOTES = 3;
	static final int FEEDBACK = 4;
	static final int HUB = 5;
	private int page;
	private static int lastPage;

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
	private final Map<String, List<String>> descCache = new java.util.HashMap<>();
	private final List<Widget> visibleSettings = new ArrayList<>();
	private final List<Widget> chrome = new ArrayList<>();
	private final List<Widget> themeWidgets = new ArrayList<>();
	private @Nullable Button resetButton;

	private boolean profileMenu;
	/** Rows after the profile names in the profile menu: new, copy, paste, delete, open folder, reset all. */
	private static final int ACTIONS = 8;
	/** An "are you sure?" card drawn over the menu, e.g. before Reset all. */
	private @Nullable Overlay dialog;
	/** Set when a key binder takes a key, so that key's typed character does not also land in the search. */
	private boolean swallowChar;
	/** The search box was clicked: it shows a caret even while empty. Typing anywhere still searches. */
	private boolean searchFocused;
	private final Animation profileMenuAnim = new Animation(Easing.CUBIC_OUT, 160, 0f);

	public ClickGuiScreen(@Nullable Screen parent) {
		super(Component.literal("Justaclient"));
		this.parent = parent;
		this.category = lastCategory;
		this.page = lastCategory == null ? lastPage : THEME;
	}

	/** The menu the menu key opens: the classic panel, or the ring when the Theme page's layout says so. */
	public static Screen open(@Nullable Screen parent) {
		return JacConfig.get().style.layout == dev.justaclient.config.StyleTokens.Layout.RING ? new RingMenuScreen(parent) : new ClickGuiScreen(parent);
	}

	/** For the UI tour: a page by name (theme, performance, stats, notes, feedback, hub). */
	public static int tourPage(String name) {
		return switch (name) {
			case "performance" -> PERF;
			case "stats" -> STATS;
			case "notes" -> NOTES;
			case "feedback" -> FEEDBACK;
			case "hub" -> HUB;
			default -> THEME;
		};
	}

	/** For the UI tour: the panel on a page. */
	public static ClickGuiScreen tourAt(@Nullable Screen parent, int page) {
		return at(parent, null, page);
	}

	/** For the UI tour: the panel on a category, with its first feature's settings showing. */
	public static ClickGuiScreen tourCategory(@Nullable Screen parent, Category category) {
		lastFeature = null;
		return at(parent, category, THEME);
	}

	/** Opens the classic panel on a category, or on a page ({@link #THEME}, {@link #HUB} and so on) when {@code category} is null. */
	static ClickGuiScreen at(@Nullable Screen parent, @Nullable Category category, int page) {
		lastCategory = category;
		lastPage = page;
		ClickGuiScreen screen = new ClickGuiScreen(parent);
		screen.page = page;
		return screen;
	}

	/** Opens straight onto one feature's settings, e.g. from the HUD editor. */
	public static ClickGuiScreen showing(@Nullable Screen parent, Feature feature) {
		lastCategory = feature.category;
		lastFeature = feature;
		return new ClickGuiScreen(parent);
	}

	/** Opens on the Feedback page as a bug report with {@code message} filled in, e.g. from the crash helper. */
	public static ClickGuiScreen feedback(@Nullable Screen parent, String message) {
		lastCategory = null;
		lastPage = FEEDBACK;
		FeedbackPage.prefill(message);
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
		layoutPanel();
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

	/**
	 * Sizes the panel: tall enough for every sidebar row at a comfortable spacing, and as wide as the window's shape
	 * allows without making the panel smaller on screen. The extra width goes to the feature list and the settings.
	 */
	private void layoutPanel() {
		int rows = navItems().size();
		float navBottom = NAV_TOP + (rows - 1) * NAV_MIN_STEP + NAV_DIVIDER + (NAV_MIN_STEP - NAV_GAP) + 8f;
		float h = Math.max(MIN_H, navBottom + FOOTER);
		float w = Fit.menuWidth(width - 16f, height - 16f, h, MIN_W, MAX_W);
		if (w != W || h != H) {
			settingWidgets.clear();
			rowY.clear();
		}
		descCache.clear();
		W = w;
		H = h;
		BODY_H = H - FOOTER;
		LIST_W = MIN_LIST_W + (W - MIN_W) * 0.45f;
		SET_X = SIDEBAR + LIST_W;
		SET_W = W - SET_X;
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
		showPage(next, THEME);
	}

	private void showPage(@Nullable Category next, int next2) {
		if (next == category && next2 == page && query.isBlank()) {
			return;
		}
		page = next2;
		lastPage = next2;
		StatsPage.leave();
		NotesPage.leave();
		FeedbackPage.leave();
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
		renderBackground(g, mouseX, mouseY, partialTick);
		float fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		scale = fit * (0.94f + 0.06f * t);
		originX = (width - W * scale) / 2f;
		originY = (height - H * scale) / 2f + (1f - t) * 6f;
		double lx = toLocalX(mouseX);
		double ly = toLocalY(mouseY);

		g.pose().pushPose();
		g.pose().translate(originX, originY, 0);
		g.pose().scale(scale, scale, 1);

		Draw.panel(g, 0, 0, W, H, t);
		float radius = JacConfig.get().style.cornerRadius;
		// Footer band and column dividers.
		dev.justaclient.render.LegacyImages.enableScissor(g, 0, (int) BODY_H, (int) W, (int) H);
		Draw.rect(g, 0, 0, W, H, radius, Colors.fade(0x26000000, t));
		g.disableScissor();
		Draw.rect(g, 0, BODY_H, W, 0.75f, 0, Colors.fade(Theme.DIVIDER, t));
		Draw.rect(g, SIDEBAR, 0, 0.75f, BODY_H, 0, Colors.fade(Theme.DIVIDER, t));
		if (!isThemePage() && !isPerfPage() && !isStatsPage() && !isNotesPage() && !isFeedbackPage() && !isHubPage()) {
			Draw.rect(g, SET_X, 0, 0.75f, BODY_H, 0, Colors.fade(Theme.DIVIDER, t));
		}

		drawSidebar(g, lx, ly, t);
		if (isThemePage()) {
			drawThemePage(g, lx, ly, t);
		} else if (isPerfPage()) {
			float a = t * pageIn.get();
			PerformancePage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, a);
		} else if (isStatsPage()) {
			float a = t * pageIn.get();
			StatsPage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, a, lx, ly);
		} else if (isNotesPage()) {
			NotesPage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, t * pageIn.get(), lx, ly);
		} else if (isFeedbackPage()) {
			FeedbackPage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, t * pageIn.get(), lx, ly);
		} else if (isHubPage()) {
			HubPage.draw(g, LIST_X + 14, 14, W - LIST_X - 28, BODY_H - 14, t * pageIn.get(), lx, ly);
		} else {
			drawList(g, lx, ly, t);
			drawSettings(g, lx, ly, t);
		}
		drawFooter(g, lx, ly, t);
		drawProfileMenu(g, lx, ly, t);
		drawSearchHistory(g, lx, ly, t);
		g.pose().popPose();
		// Key and character events for one key press arrive before the next frame, so a frame ends the swallow window.
		swallowChar = false;
		if (dialog != null) {
			dialog.render(g, width, height, mouseX, mouseY);
		}
	}

	private boolean isThemePage() {
		return category == null && query.isBlank() && page == THEME;
	}

	private boolean isPerfPage() {
		return category == null && query.isBlank() && page == PERF;
	}

	private boolean isStatsPage() {
		return category == null && query.isBlank() && page == STATS;
	}

	private boolean isNotesPage() {
		return category == null && query.isBlank() && page == NOTES;
	}

	private boolean isHubPage() {
		return category == null && query.isBlank() && page == HUB;
	}

	private boolean isFeedbackPage() {
		return category == null && query.isBlank() && page == FEEDBACK;
	}

	/** Pages listed under the categories: which page each opens, with its icon and label. */
	private static int pageOf(Object item) {
		return item == PerformancePage.class ? PERF : item == StatsPage.class ? STATS : item == NotesPage.class ? NOTES
			: item == FeedbackPage.class ? FEEDBACK : item == HubPage.class ? HUB : THEME;
	}

	private static Icons pageIcon(Object item) {
		return item == ThemePage.class ? Icons.PALETTE : item == StatsPage.class ? Icons.CHART_COLUMN : item == NotesPage.class ? Icons.PENCIL
			: item == FeedbackPage.class ? Icons.FLAG : item == HubPage.class ? Icons.COMMAND : Icons.ACTIVITY;
	}

	private static String pageLabel(Object item) {
		return item == ThemePage.class ? "Theme" : item == StatsPage.class ? "Stats" : item == NotesPage.class ? "Notes"
			: item == FeedbackPage.class ? "Feedback" : item == HubPage.class ? "Command Centre" : "Performance";
	}

	private boolean isPageShown(Object item) {
		return category == null && query.isBlank() && !(item instanceof Category) && page == pageOf(item);
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
		boolean boxActive = searching || searchFocused;
		boolean boxHover = mx >= sx && mx < sx + sw && my >= sy && my < sy + sh;
		Draw.rect(g, sx, sy, sw, sh, r, Colors.fade(boxActive || boxHover ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		if (boxActive) {
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
			// Clear button.
			boolean clearHover = mx >= sx + sw - 15 && mx < sx + sw && my >= sy && my < sy + sh;
			JacText.icon(g, Icons.X, sx + sw - 11, sy + (sh - 6f) / 2f, 6f, Colors.fade(clearHover ? Theme.TEXT : Theme.TEXT_DIM, t));
		} else {
			JacText.draw(g, "Search features", sx + (searchFocused ? 19 : 16), ty, Colors.fade(Theme.TEXT_DIM, t * 0.8f), Theme.LABEL, JacText.Weight.REGULAR);
			if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0) {
				Draw.rect(g, sx + 16, sy + 4, 0.75f, sh - 8, 0, Colors.fade(Theme.TEXT, t));
			}
		}

		// Categories, then Theme below a divider. A single marker slides to the active one.
		List<Object> items = navItems();
		float rowH = navRowH();
		int activeIndex = -1;
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			if (!searching && (item == category || isPageShown(item))) {
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
			Draw.rect(g, 10, my0, SIDEBAR - 20, rowH, r, Colors.fade(Colors.withAlpha(accent, 0x38), t));
			Draw.rect(g, 10, my0 + (rowH - 11) / 2f, 1.5f, 11, 0.75f, Colors.fade(accent, t));
		}
		Draw.rect(g, 14, navY(items.indexOf(HubPage.class)) - 6, SIDEBAR - 28, 0.75f, 0, Colors.fade(Theme.DIVIDER, t));
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			float y = navY(i);
			boolean hover = mx >= 10 && mx < SIDEBAR - 10 && my >= y && my < y + rowH;
			float h = navHover.computeIfAbsent(item, k -> new Animation(Easing.CUBIC_OUT, 120, 0f)).animateTo(hover ? 1f : 0f).get();
			if (h > 0.01f && i != activeIndex) {
				Draw.rect(g, 10, y, SIDEBAR - 20, rowH, r, Colors.fade(Colors.withAlpha(0xFFFFFF, 0x0E), t * h));
			}
			boolean active = i == activeIndex;
			Icons icon = item instanceof Category c ? c.icon : pageIcon(item);
			String label = item instanceof Category c ? c.label : pageLabel(item);
			int color = Colors.lerp(Theme.TEXT_DIM, Theme.TEXT, active ? 1f : h * 0.6f);
			JacText.icon(g, icon, 17, y + (rowH - 7.5f) / 2f, 7.5f, Colors.fade(active ? JacConfig.accent() : color, t));
			JacText.draw(g, label, 31, y + (rowH - JacText.lineHeight(Theme.LABEL)) / 2f, Colors.fade(color, t), Theme.LABEL,
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
		items.add(HubPage.class);
		items.add(ThemePage.class);
		items.add(PerformancePage.class);
		items.add(StatsPage.class);
		items.add(NotesPage.class);
		items.add(FeedbackPage.class);
		return items;
	}

	private float navY(int index) {
		List<Object> items = navItems();
		int pages = items.indexOf(HubPage.class);
		return NAV_TOP + index * navStep(items.size()) + (index >= pages ? NAV_DIVIDER : 0);
	}

	/** How far apart the sidebar rows are: roomy when the panel has space, never closer than {@link #NAV_MIN_STEP}. */
	private float navStep(int rows) {
		float room = (BODY_H - 8f - NAV_TOP - NAV_DIVIDER - (NAV_MIN_STEP - NAV_GAP)) / Math.max(1, rows - 1);
		return Math.max(NAV_MIN_STEP, Math.min(NAV_MAX_STEP, room));
	}

	/** The height of a sidebar row's highlight, which leaves a gap before the next row. */
	private float navRowH() {
		return navStep(navItems().size()) - NAV_GAP;
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

		dev.justaclient.render.LegacyImages.enableScissor(g, (int) LIST_X, (int) LIST_TOP - 2, (int) SET_X, (int) (BODY_H - 4));
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
			String sub = query.isBlank() ? f.description : f.category.label + " · " + f.description;
			List<String> lines = descLines(sub, cw - 50);
			float lh = JacText.lineHeight(Theme.SMALL);
			// A one-line description sits in the middle of the card's spare line.
			float ty = y + 5.5f + (2 - lines.size()) * lh / 2f;
			String name = JacScreen.fit(f.name, Theme.LABEL, JacText.Weight.SEMIBOLD, cw - 50);
			JacText.draw(g, name, cx + 9, ty, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			if (f.isExperimental()) {
				float bxl = cx + 9 + JacText.width(name, Theme.LABEL, JacText.Weight.SEMIBOLD) + 4;
				float bwl = JacText.width("Labs", Theme.SMALL - 0.5f, JacText.Weight.SEMIBOLD) + 8;
				Draw.rect(g, bxl, ty - 0.5f, bwl, 9, 4.5f, Colors.fade(0x40A78BFA, a));
				JacText.draw(g, "Labs", bxl + 4, ty + 1.5f, Colors.fade(0xFFC4B5FD, a), Theme.SMALL - 0.5f, JacText.Weight.SEMIBOLD);
			}
			for (int l = 0; l < lines.size(); l++) {
				JacText.draw(g, lines.get(l), cx + 9, ty + 9.5f + l * lh, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			}
			drawSwitch(g, f, cx + cw - 9 - 20, y + (CARD_H - 11) / 2f, a);
		}
		if (themeHit) {
			float y = LIST_TOP + shown.size() * (CARD_H + CARD_GAP) - scroll + slide;
			boolean hover = inView && mx >= cx && mx < cx + cw && my >= y && my < y + CARD_H;
			Draw.rect(g, cx, y, cw, CARD_H, radius, Colors.fade(hover ? 0x1CFFFFFF : 0x10FFFFFF, a));
			float ty = y + 5.5f + JacText.lineHeight(Theme.SMALL) / 2f;
			JacText.draw(g, "Theme", cx + 9, ty, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			JacText.draw(g, "Accent, panels, blur, font and motion", cx + 9, ty + 9.5f, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			JacText.icon(g, Icons.CHEVRON_RIGHT, cx + cw - 9 - 8, y + (CARD_H - 8) / 2f, 8f, Colors.fade(Theme.TEXT_DIM, a));
		}
		g.disableScissor();
		if (contentH > viewH) {
			// A thin bar, so a card cut off at the bottom reads as "more below".
			float barH = Math.max(16f, viewH * viewH / contentH);
			float barY = LIST_TOP + (viewH - barH) * (scroll / Math.max(1f, contentH - viewH));
			Draw.rect(g, SET_X - 5, barY, 2f, barH, 1f, Colors.fade(0x30FFFFFF, a));
		}
		if (shown.isEmpty() && !themeHit && !query.isBlank()) {
			JacText.drawCentered(g, "Nothing matches \"" + fitTail(query, 100) + "\"", LIST_X + LIST_W / 2f, LIST_TOP + 20,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
	}

	/** A card's description in at most two lines, the second cut with an ellipsis; remembered per text and width. */
	private List<String> descLines(String text, float max) {
		return descCache.computeIfAbsent(text + "\u0000" + max, k -> {
			List<String> wrapped = JacScreen.wrap(text, Theme.SMALL, JacText.Weight.REGULAR, max);
			if (wrapped.size() <= 2) {
				return wrapped;
			}
			return List.of(wrapped.getFirst(), fitEnd(String.join(" ", wrapped.subList(1, wrapped.size())), max));
		});
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
		List<String> desc = descLines(f.description, W - tx - SET_PAD);
		float dy = 37 + slide;
		for (String line : desc) {
			JacText.draw(g, line, tx, dy, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			dy += JacText.lineHeight(Theme.SMALL);
		}
		if (f instanceof HudFeature) {
			JacText.draw(g, "Drag it in Edit HUD layout", tx, dy + 2f, Colors.fade(Colors.withAlpha(JacConfig.accent(), 0xE0), a), Theme.SMALL,
				JacText.Weight.REGULAR);
		}

		List<Widget> rows = settingsLayout();
		float viewH = BODY_H - SET_TOP - 6;
		settingsScrollTarget = Math.max(0f, Math.min(settingsScrollTarget, Math.max(0f, settingsContentH - viewH)));
		settingsScroll.setTarget(settingsScrollTarget);
		float scroll = settingsScroll.update();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) SET_X, (int) SET_TOP - 4, (int) W, (int) (BODY_H - 2));
		double sy = my >= SET_TOP - 4 && my < BODY_H - 2 ? my + scroll : -1e4;
		g.pose().pushPose();
		g.pose().translate(0, -scroll + slide, 0);
		for (Widget w : rows) {
			Spring ys = rowY.get(w);
			w.setPosition(px, ys.update());
			w.render(g, mx, sy, a);
		}
		if (resetButton != null) {
			resetButton.render(g, mx, sy, a);
		}
		g.pose().popPose();
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
		ThemePage.drawPreview(g, SET_X + 10, 216, a);
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

	private boolean searchHistoryShown() {
		return dialog == null && searchFocused && query.isEmpty() && !JacConfig.get().searchHistory.isEmpty();
	}

	private static final float HISTORY_Y = 51f;

	/** Recent searches under the search box while it is focused and empty. */
	private void drawSearchHistory(GuiGraphics g, double mx, double my, float t) {
		if (!searchHistoryShown()) {
			return;
		}
		List<String> history = JacConfig.get().searchHistory;
		float x = 10;
		float w = SIDEBAR - 20;
		float h = 14 + history.size() * 13f + 4;
		g.flush();
		Draw.panel(g, x, HISTORY_Y, w, h, t);
		JacText.draw(g, "Recent", x + 7, HISTORY_Y + 5, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
		for (int i = 0; i < history.size(); i++) {
			float iy = HISTORY_Y + 14 + i * 13f;
			boolean hover = mx >= x + 3 && mx < x + w - 3 && my >= iy && my < iy + 13;
			if (hover) {
				Draw.rect(g, x + 3, iy, w - 6, 13, 4f, Colors.fade(Theme.CONTROL_HOVER, t));
			}
			JacText.icon(g, Icons.CLOCK, x + 7, iy + 3.5f, 6f, Colors.fade(Theme.TEXT_DIM, t));
			JacText.draw(g, fitEnd(history.get(i), w - 26), x + 17, iy + 3, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.REGULAR);
		}
	}

	/** Returns true if the click picked a recent search. */
	private boolean clickSearchHistory(double mx, double my) {
		if (!searchHistoryShown()) {
			return false;
		}
		List<String> history = JacConfig.get().searchHistory;
		float x = 10;
		float w = SIDEBAR - 20;
		for (int i = 0; i < history.size(); i++) {
			float iy = HISTORY_Y + 14 + i * 13f;
			if (mx >= x + 3 && mx < x + w - 3 && my >= iy && my < iy + 13) {
				query = history.get(i);
				refreshList();
				return true;
			}
		}
		return false;
	}

	private float profileButtonWidth() {
		return Math.max(64f, JacText.width(Profiles.active(), Theme.LABEL, JacText.Weight.SEMIBOLD) + 30f);
	}

	private List<String> profileMenuItems() {
		List<String> items = new ArrayList<>(Profiles.list());
		items.add("+");
		items.add("e");
		items.add("i");
		items.add("E");
		items.add("I");
		items.add("-");
		items.add("/");
		items.add("r");
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
		g.flush();
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
				case "E" -> menuRow(g, Icons.UPLOAD, "Export to file...", x, iy, ty, color);
				case "I" -> menuRow(g, Icons.DOWNLOAD, "Import from file...", x, iy, ty, color);
				case "-" -> menuRow(g, Icons.TRASH, "Delete this profile", x, iy, ty, color);
				case "/" -> menuRow(g, Icons.LAYERS, "Open profiles folder", x, iy, ty, color);
				case "r" -> menuRow(g, Icons.ROTATE_CCW, "Reset all settings", x, iy, ty, color);
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
		if (dialog != null) {
			return dialog.mouseClicked(event.x(), event.y(), event.button());
		}
		double mx = toLocalX(event.x());
		double my = toLocalY(event.y());
		int button = event.button();
		boolean inSearch = mx >= 10 && mx < SIDEBAR - 10 && my >= 32 && my < 49;
		if (button == 0 && clickSearchHistory(mx, my)) {
			return true;
		}
		searchFocused = inSearch && button == 0;

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
			showPage(null, PERF);
			return true;
		}
		// Sidebar.
		List<Object> items = navItems();
		for (int i = 0; i < items.size(); i++) {
			float y = navY(i);
			if (button == 0 && mx >= 10 && mx < SIDEBAR - 10 && my >= y && my < y + navRowH()) {
				Object item = items.get(i);
				showPage(item instanceof Category c ? c : null, item instanceof Category ? THEME : pageOf(item));
				return true;
			}
		}
		// Search box: a right click or the clear button empties it, a left click focuses it.
		if (inSearch && (button == 1 || button == 0 && !query.isEmpty() && mx >= SIDEBAR - 25)) {
			query = "";
			refreshList();
			return true;
		}
		if (inSearch && button == 0) {
			return true;
		}
		if (isStatsPage() && button == 0 && StatsPage.click(mx, my)) {
			return true;
		}
		if (isNotesPage() && NotesPage.click(mx, my, button)) {
			return true;
		}
		if (isFeedbackPage() && FeedbackPage.click(mx, my, button)) {
			return true;
		}
		if (isHubPage() && button == 0) {
			Screen next = HubPage.click(mx, my, this);
			if (next != null) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				minecraft.setScreen(next);
				return true;
			}
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
					if (!query.isBlank()) {
						JacConfig.rememberSearch(query);
					}
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
			case "E" -> {
				dialog = new ProfileFilesDialog(true, () -> dialog = null, () -> dialog = null);
				return;
			}
			case "I" -> {
				dialog = new ProfileFilesDialog(false, () -> dialog = null, () -> {
					dialog = null;
					knobs.clear();
					settingWidgets.clear();
					rowY.clear();
					refreshList();
					select(selected);
				});
				return;
			}
			case "-" -> Profiles.deleteActive();
			case "/" -> {
				Profiles.save();
				Util.getPlatform().openPath(Profiles.DIR);
			}
			case "r" -> {
				dialog = new ConfirmDialog(Icons.ROTATE_CCW, "Reset all settings?",
					"Every feature, setting, key and HUD position in " + Profiles.active() + " goes back to its default. A backup is saved first.",
					"Reset all", () -> {
						dialog = null;
						String backup = Profiles.resetAll();
						Notifications.push(Icons.ROTATE_CCW, "Reset all", backup == null ? "cancelled: the backup could not be saved" : "done, backup saved");
						knobs.clear();
						settingWidgets.clear();
						rowY.clear();
						refreshList();
						select(selected);
					}, () -> dialog = null);
				return;
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
		if (dialog != null) {
			return dialog.mouseScrolled(scrollY);
		}
		if (isStatsPage() && mx >= LIST_X && StatsPage.scroll(scrollY)) {
			return true;
		}
		if (isHubPage()) {
			return mx >= LIST_X && HubPage.scroll(mx, my, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
		}
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
		if (dialog == null && isNotesPage() && NotesPage.focused()) {
			return NotesPage.keyPressed(event.key(), event.hasControlDown());
		}
		if (dialog == null && isFeedbackPage() && FeedbackPage.focused()) {
			return FeedbackPage.keyPressed(event.key(), event.hasControlDown());
		}
		if (dialog != null) {
			return dialog.keyPressed(event.key());
		}
		for (Widget w : visibleSettings) {
			if (w.capturesKeys()) {
				w.keyPressed(event.key());
				Profiles.save();
				// A key binder stops listening once it has its key; that key's character must not reach the search.
				swallowChar = !w.capturesKeys();
				return true;
			}
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (profileMenu) {
				profileMenu = false;
			} else if (searchFocused && query.isEmpty()) {
				searchFocused = false;
			} else if (!query.isEmpty()) {
				query = "";
				refreshList();
			} else {
				onClose();
			}
			return true;
		}
		if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !query.isBlank()) {
			if (!shown.isEmpty()) {
				JacConfig.rememberSearch(query);
				select(shown.getFirst());
			}
			return true;
		}
		if (Justaclient.OPEN_MENU.matches(event.key(), event.scancode()) && query.isEmpty()) {
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
		if (dialog == null && isNotesPage() && NotesPage.focused()) {
			return NotesPage.charTyped(event.codepointAsString());
		}
		if (dialog == null && isFeedbackPage() && FeedbackPage.focused()) {
			return FeedbackPage.charTyped(event.codepointAsString());
		}
		if (dialog != null) {
			return dialog.charTyped(event.codepointAsString());
		}
		if (swallowChar) {
			swallowChar = false;
			return true;
		}
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
			NotesPage.leave();
			FeedbackPage.leave();
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
