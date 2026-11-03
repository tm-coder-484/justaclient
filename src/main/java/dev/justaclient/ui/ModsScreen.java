package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.mods.DeferredRenames;
import dev.justaclient.common.mods.ManagedMod;
import dev.justaclient.common.mods.ModManager;
import dev.justaclient.common.mods.ModStatus;
import dev.justaclient.common.mods.Relauncher;
import dev.justaclient.common.mods.ToggleResult;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.ModsPanelFeature;
import dev.justaclient.mods.ModConfigScreens;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.ModIcons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The Mods panel: every installed mod with its icon, details and settings screen, and a switch to turn it off or on.
 * Switching renames the jar, so changes apply after a restart, which the panel can do for you.
 */
public final class ModsScreen extends JacScreen {
	private static final float ROW_H = 24f;
	private static final float PAD = 12f;
	private static final int OK = 0xFF34D399;
	private static final int WARN = 0xFFFBBF24;
	private static final int BAD = 0xFFF87171;

	private enum Filter {
		ALL("All"), ON("On"), OFF("Off"), CHANGED("Changed");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private List<ManagedMod> mods = List.of();
	private List<ManagedMod> shown = List.of();
	private @Nullable String selectedKey;
	private Filter filter = Filter.ALL;
	private final InputBox search = new InputBox("", 64, "Search mods", Icons.SEARCH);
	private final Scroller scroll = new Scroller();
	private final Scroller detailScroll = new Scroller();
	private final List<Button> buttons = new ArrayList<>();
	private final List<Button> linkButtons = new ArrayList<>();
	private @Nullable Button toggleButton;
	private @Nullable Button configButton;
	private @Nullable Button restartButton;
	/** Set after a first click on "Turn off" when other mods need this one. */
	private @Nullable String confirmKey;
	private String status = "";
	private int statusColor = OK;
	private boolean draggingBar;

	// Layout, worked out each frame.
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float detailX;
	private float chipsY;

	public ModsScreen(@Nullable Screen parent) {
		super("Mods", parent);
	}

	@Override
	protected void setup() {
		if (mods.isEmpty()) {
			rescan();
		}
	}

	private void rescan() {
		mods = ModManager.scan();
		ModsPanelFeature feature = Features.get(ModsPanelFeature.class);
		if (feature != null && !feature.showLibraries()) {
			mods = mods.stream().filter(m -> !m.library() || m.status() != ModStatus.ENABLED || m.key().equals(selectedKey)).toList();
		}
		applyFilter();
		if (selected() == null && !shown.isEmpty()) {
			select(shown.getFirst());
		} else {
			select(selected());
		}
	}

	private void applyFilter() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		shown = mods.stream().filter(m -> switch (filter) {
			case ALL -> true;
			case ON -> m.status().activeNextLaunch();
			case OFF -> !m.status().activeNextLaunch();
			case CHANGED -> m.status().needsRestart();
		}).filter(m -> q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q) || m.id().toLowerCase(Locale.ROOT).contains(q)
			|| m.description().toLowerCase(Locale.ROOT).contains(q)).toList();
	}

	private @Nullable ManagedMod selected() {
		for (ManagedMod m : mods) {
			if (m.key().equals(selectedKey)) {
				return m;
			}
		}
		return null;
	}

	private void select(@Nullable ManagedMod mod) {
		String key = mod == null ? null : mod.key();
		if (key == null || !key.equals(selectedKey)) {
			detailScroll.scrollTo(0);
			detailScroll.snap();
			confirmKey = null;
			status = "";
		}
		selectedKey = key;
		buildDetailButtons(mod);
	}

	private void buildDetailButtons(@Nullable ManagedMod mod) {
		linkButtons.clear();
		toggleButton = null;
		configButton = null;
		if (mod == null) {
			return;
		}
		boolean on = mod.status().activeNextLaunch();
		String label;
		if (mod.status() == ModStatus.LOCKED) {
			label = "Always on";
		} else if (confirmKey != null && confirmKey.equals(mod.key())) {
			label = "Turn off anyway";
		} else {
			label = on ? "Turn off" : "Turn on";
		}
		toggleButton = new Button(0, 0, 92, 18, Icons.POWER, label, !on, () -> toggle(mod)).active(mod.status() != ModStatus.LOCKED);
		if (mod.loaded()) {
			boolean has = ModConfigScreens.has(mod.id());
			configButton = new Button(0, 0, 92, 18, Icons.SETTINGS, has ? "Settings" : "No settings", false, () -> openConfig(mod)).active(has);
		}
		for (Map.Entry<String, String> link : mod.links().entrySet()) {
			if (link.getValue().startsWith("http")) {
				String url = link.getValue();
				float w = JacText.width(link.getKey(), Theme.SMALL, JacText.Weight.SEMIBOLD) + 26;
				linkButtons.add(new Button(0, 0, w, 15, Icons.EXTERNAL_LINK, link.getKey(), false, () -> openUri(url)));
			}
		}
	}

	private void toggle(ManagedMod mod) {
		if (mod.status() == ModStatus.ENABLED && confirmKey == null) {
			List<String> dependents = ModManager.dependentsOf(mod, mods);
			if (!dependents.isEmpty()) {
				confirmKey = mod.key();
				status = "Needed by " + String.join(", ", dependents) + ". Click again to turn it off anyway.";
				statusColor = WARN;
				buildDetailButtons(mod);
				return;
			}
		}
		confirmKey = null;
		ToggleResult result = ModManager.toggle(mod);
		switch (result.outcome()) {
			case FAILED -> {
				status = result.error();
				statusColor = BAD;
			}
			case UNDONE -> {
				status = mod.name() + " is back as it was";
				statusColor = OK;
			}
			case DEFERRED -> {
				status = mod.name() + " will be turned off when the game closes";
				statusColor = OK;
			}
			default -> {
				boolean nowOn = !mod.status().activeNextLaunch();
				List<String> missing = nowOn ? ModManager.missingDependencies(mod, mods) : List.of();
				if (!missing.isEmpty()) {
					status = mod.name() + " also needs " + String.join(", ", missing) + ", which is not installed or is off";
					statusColor = WARN;
				} else {
					status = mod.name() + (nowOn ? " turns on" : " turns off") + " after a restart";
					statusColor = OK;
				}
			}
		}
		String key = mod.key();
		rescan();
		for (ManagedMod m : mods) {
			if (m.key().equals(key)) {
				select(m);
			}
		}
	}

	private void openConfig(ManagedMod mod) {
		Screen screen = ModConfigScreens.open(mod.id(), this);
		if (screen == null) {
			status = "Could not open the settings of " + mod.name();
			statusColor = BAD;
			return;
		}
		open(screen);
	}

	private int changes() {
		int n = 0;
		for (ManagedMod m : mods) {
			if (m.status().needsRestart()) {
				n++;
			}
		}
		return n;
	}

	private void restart() {
		ModsPanelFeature feature = Features.get(ModsPanelFeature.class);
		boolean relaunch = feature == null || feature.relaunch();
		if (relaunch && !Relauncher.requestRestart()) {
			status = "Could not work out how to start the game again; it will just close";
			statusColor = WARN;
		}
		if (minecraft.level != null) {
			minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		minecraft.stop();
	}

	@Override
	public void removed() {
		ModIcons.clear();
		super.removed();
	}

	// Drawing

	private void layout() {
		pw = Math.min(560f, width - 24f);
		ph = Math.min(340f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		listX = px + PAD;
		listW = Math.max(150f, pw * 0.44f);
		chipsY = py + 40;
		listY = chipsY + 18;
		listH = py + ph - 34 - listY;
		detailX = listX + listW + PAD;
		search.place(px + pw - PAD - 150 - 20, py + 10, 150);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float y0 = py + (1f - t) * 8f;
		float a = t;
		Draw.panel(g, px, y0, pw, ph, a);
		int accent = JacConfig.accent();
		float off = y0 - py;
		// Header.
		JacText.icon(g, Icons.PACKAGE, px + PAD, py + PAD + 1 + off, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Mods", px + PAD + 15, py + PAD + off, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		long off2 = mods.stream().filter(m -> !m.status().activeNextLaunch()).count();
		JacText.draw(g, mods.size() + " installed" + (off2 > 0 ? " · " + off2 + " off" : ""), px + PAD + 15, py + PAD + 12 + off,
			Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		search.draw(g, mx, my, a);
		boolean closeHot = mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		// Filter chips.
		float cx = listX;
		for (Filter f : Filter.values()) {
			String label = f.label + (f == Filter.CHANGED && changes() > 0 ? " " + changes() : "");
			float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			boolean on = f == filter;
			boolean hot = mx >= cx && mx < cx + w && my >= chipsY && my < chipsY + 13;
			Draw.rect(g, cx, chipsY, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, label, cx + 6, chipsY + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}
		drawList(g, mx, my, a);
		drawDetail(g, mx, my, a);
		drawFooter(g, mx, my, a);
	}

	private void drawList(GuiGraphics g, int mx, int my, float a) {
		scroll.setMax(shown.size() * ROW_H - listH);
		float s = scroll.get();
		Draw.rect(g, listX, listY, listW, listH, 6f, Colors.fade(0x18000000, a));
		g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + listH));
		int accent = JacConfig.accent();
		int first = Math.max(0, (int) (s / ROW_H));
		for (int i = first; i < shown.size(); i++) {
			float y = listY + i * ROW_H - s;
			if (y > listY + listH) {
				break;
			}
			ManagedMod m = shown.get(i);
			boolean sel = m.key().equals(selectedKey);
			boolean hot = mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H && my >= listY && my < listY + listH;
			if (sel || hot) {
				Draw.rect(g, listX + 2, y + 1, listW - 4, ROW_H - 2, 5f, Colors.fade(sel ? Colors.withAlpha(accent, 0x30) : 0x10FFFFFF, a));
			}
			boolean on = m.status().activeNextLaunch();
			float fade = on ? 1f : 0.55f;
			ModIcons.draw(g, m, listX + 6, y + 4, 16, a * fade);
			float textW = listW - 36 - 34;
			JacText.draw(g, fit(m.name(), Theme.LABEL, JacText.Weight.SEMIBOLD, textW), listX + 28, y + 4, Colors.fade(Theme.TEXT, a * fade),
				Theme.LABEL, JacText.Weight.SEMIBOLD);
			String sub = m.status().needsRestart() ? (on ? "Turns on after restart" : "Turns off after restart") : m.version();
			JacText.draw(g, fit(sub, Theme.SMALL, JacText.Weight.REGULAR, textW), listX + 28, y + 13.5f,
				Colors.fade(m.status().needsRestart() ? WARN : Theme.TEXT_DIM, a * (m.status().needsRestart() ? 1f : fade)), Theme.SMALL,
				JacText.Weight.REGULAR);
			// Switch.
			float sx = listX + listW - 30;
			float sy = y + (ROW_H - 10) / 2f;
			if (m.status() == ModStatus.LOCKED) {
				JacText.icon(g, Icons.LOCK, sx + 10, sy + 1.5f, 7f, Colors.fade(Theme.TEXT_DIM, a * 0.7f));
			} else {
				Draw.rect(g, sx, sy, 20, 10, 5f, Colors.fade(on ? accent : 0x30FFFFFF, a));
				Draw.circle(g, on ? sx + 15 : sx + 5, sy + 5, 3.5f, Colors.fade(Theme.KNOB, a));
			}
		}
		g.disableScissor();
		if (shown.isEmpty()) {
			JacText.drawCentered(g, mods.isEmpty() ? "No mods found" : "Nothing matches", listX + listW / 2f, listY + 20,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		if (scroll.max() > 0) {
			float barH = Math.max(16f, listH * listH / (listH + scroll.max()));
			float barY = listY + (listH - barH) * (s / scroll.max());
			Draw.rect(g, listX + listW - 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
	}

	private void drawDetail(GuiGraphics g, int mx, int my, float a) {
		ManagedMod m = selected();
		float x = detailX;
		float w = px + pw - PAD - x;
		float top = chipsY;
		float h = listY + listH - top;
		if (m == null) {
			JacText.drawCentered(g, "Pick a mod", x + w / 2f, top + h / 2f - 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			return;
		}
		g.enableScissor((int) x, (int) top, (int) (x + w), (int) (top + h));
		float y = top - detailScroll.get();
		ModIcons.draw(g, m, x, y, 32, a);
		JacText.draw(g, fit(m.name(), Theme.TITLE, JacText.Weight.SEMIBOLD, w - 40), x + 40, y + 2, Colors.fade(Theme.TEXT, a), Theme.TITLE,
			JacText.Weight.SEMIBOLD);
		String line2 = m.version() + "  ·  " + m.id();
		JacText.draw(g, fit(line2, Theme.SMALL, JacText.Weight.REGULAR, w - 40), x + 40, y + 15, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		if (!m.authors().isEmpty()) {
			JacText.draw(g, fit("by " + String.join(", ", m.authors()), Theme.SMALL, JacText.Weight.REGULAR, w - 40), x + 40, y + 24,
				Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		y += 40;
		// Status pill.
		String state;
		int stateColor;
		switch (m.status()) {
			case ENABLED -> {
				state = "On";
				stateColor = OK;
			}
			case DISABLED -> {
				state = "Off";
				stateColor = Theme.TEXT_DIM;
			}
			case DISABLE_PENDING -> {
				state = "Turns off after a restart";
				stateColor = WARN;
			}
			case ENABLE_PENDING -> {
				state = "Turns on after a restart";
				stateColor = WARN;
			}
			default -> {
				state = m.lockReason() != null ? m.lockReason() : "Always on";
				stateColor = Theme.TEXT_DIM;
			}
		}
		float sw = JacText.width(state, Theme.SMALL, JacText.Weight.SEMIBOLD) + 16;
		Draw.rect(g, x, y, sw, 12, 6f, Colors.fade(Colors.withAlpha(stateColor, 0x26), a));
		Draw.circle(g, x + 6, y + 6, 2f, Colors.fade(stateColor, a));
		JacText.draw(g, state, x + 11, y + 2.5f, Colors.fade(stateColor, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 20;
		// Buttons.
		if (toggleButton != null) {
			toggleButton.setPosition(x, y);
			toggleButton.render(g, mx, my, a);
		}
		if (configButton != null) {
			configButton.setPosition(x + 98, y);
			configButton.render(g, mx, my, a);
		}
		y += 26;
		if (!status.isEmpty()) {
			for (String line : wrap(status, Theme.SMALL, JacText.Weight.REGULAR, w - 4)) {
				JacText.draw(g, line, x, y - 2, Colors.fade(statusColor, a), Theme.SMALL, JacText.Weight.REGULAR);
				y += 9;
			}
			y += 6;
		}
		if (!m.description().isBlank()) {
			for (String line : wrap(m.description(), Theme.LABEL, JacText.Weight.REGULAR, w - 4)) {
				JacText.draw(g, line, x, y, Colors.fade(Theme.TEXT, a * 0.9f), Theme.LABEL, JacText.Weight.REGULAR);
				y += 10;
			}
			y += 6;
		}
		List<String> needs = new ArrayList<>(m.requiredIds());
		needs.removeIf(id -> id.equals("minecraft") || id.equals("java") || id.equals("fabricloader"));
		if (!needs.isEmpty()) {
			List<String> missing = ModManager.missingDependencies(m, mods);
			JacText.draw(g, "Needs", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			y += 10;
			float cx = x;
			for (String id : needs) {
				boolean miss = missing.contains(id);
				String label = nameOf(id);
				float cw = JacText.width(label, Theme.SMALL, JacText.Weight.REGULAR) + 10;
				if (cx + cw > x + w) {
					cx = x;
					y += 14;
				}
				Draw.rect(g, cx, y, cw, 12, 6f, Colors.fade(miss ? Colors.withAlpha(BAD, 0x30) : Theme.CONTROL, a));
				JacText.draw(g, label, cx + 5, y + 2.5f, Colors.fade(miss ? BAD : Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
				cx += cw + 4;
			}
			y += 20;
		}
		List<String> dependents = ModManager.dependentsOf(m, mods);
		if (!dependents.isEmpty() && m.status().activeNextLaunch()) {
			JacText.draw(g, "Needed by", x, y, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			y += 10;
			for (String line : wrap(String.join(", ", dependents), Theme.SMALL, JacText.Weight.REGULAR, w - 4)) {
				JacText.draw(g, line, x, y, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
				y += 9;
			}
			y += 8;
		}
		if (!linkButtons.isEmpty()) {
			float cx = x;
			for (Button b : linkButtons) {
				if (cx + b.width() > x + w) {
					cx = x;
					y += 19;
				}
				b.setPosition(cx, y);
				b.render(g, mx, my, a);
				cx += b.width() + 4;
			}
			y += 22;
		}
		if (!m.fileName().isEmpty()) {
			JacText.draw(g, fit(m.fileName(), Theme.SMALL, JacText.Weight.REGULAR, w), x, y, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL,
				JacText.Weight.REGULAR);
			y += 10;
		}
		g.disableScissor();
		float contentH = y + detailScroll.get() - top;
		detailScroll.setMax(contentH - h);
	}

	private String nameOf(String id) {
		for (ManagedMod m : mods) {
			if (m.providedIds().contains(id) && m.id().equals(id)) {
				return m.name();
			}
		}
		return id;
	}

	private void drawFooter(GuiGraphics g, int mx, int my, float a) {
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		buttons.clear();
		Button folder = new Button(px + PAD, fy, 100, 16, Icons.FOLDER_OPEN, "Mods folder", false, () -> openPath(ModManager.modsFolder()));
		buttons.add(folder);
		int changes = changes();
		boolean deferred = DeferredRenames.anyPending();
		if (changes > 0 || deferred) {
			ModsPanelFeature feature = Features.get(ModsPanelFeature.class);
			boolean relaunch = feature == null || feature.relaunch();
			String label = relaunch ? "Restart now" : "Close the game";
			float bw = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 30;
			restartButton = new Button(px + pw - PAD - bw, fy, bw, 16, Icons.REFRESH_CW, label, true, this::restart);
			buttons.add(restartButton);
			String note = changes == 1 ? "1 change applies after a restart" : changes + " changes apply after a restart";
			JacText.draw(g, note, px + pw - PAD - bw - 8 - JacText.width(note, Theme.SMALL, JacText.Weight.REGULAR), fy + 4.5f,
				Colors.fade(WARN, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		for (Button b : buttons) {
			b.render(g, mx, my, a);
		}
	}

	// Input

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		if (search.click(mx, my)) {
			return true;
		}
		if (mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26) {
			onClose();
			return true;
		}
		for (Button b : buttons) {
			if (b.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		if (toggleButton != null && toggleButton.mouseClicked(mx, my, button)) {
			return true;
		}
		if (configButton != null && configButton.mouseClicked(mx, my, button)) {
			return true;
		}
		for (Button b : List.copyOf(linkButtons)) {
			if (b.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		float cx = listX;
		for (Filter f : Filter.values()) {
			String label = f.label + (f == Filter.CHANGED && changes() > 0 ? " " + changes() : "");
			float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			if (mx >= cx && mx < cx + w && my >= chipsY && my < chipsY + 13) {
				filter = f;
				applyFilter();
				scroll.scrollTo(0);
				return true;
			}
			cx += w + 4;
		}
		if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			if (scroll.max() > 0 && mx >= listX + listW - 6) {
				draggingBar = true;
				dragBar(my);
				return true;
			}
			int i = (int) ((my - listY + scroll.get()) / ROW_H);
			if (i >= 0 && i < shown.size()) {
				ManagedMod m = shown.get(i);
				if (mx >= listX + listW - 34 && m.status() != ModStatus.LOCKED) {
					select(m);
					toggle(m);
				} else {
					select(m);
				}
			}
			return true;
		}
		return false;
	}

	private void dragBar(double my) {
		scroll.scrollTo((float) ((my - listY) / listH) * (scroll.max() + listH) - listH / 2f);
		scroll.snap();
	}

	@Override
	protected void drag(double mx, double my, int button) {
		if (draggingBar) {
			dragBar(my);
		}
	}

	@Override
	protected void release(double mx, double my, int button) {
		draggingBar = false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx >= detailX) {
			detailScroll.scroll(amount, 20f);
		} else {
			scroll.scroll(amount, ROW_H * 1.5f);
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused) {
			String before = search.get();
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				applyFilter();
				scroll.scrollTo(0);
			}
			if (used) {
				return true;
			}
		}
		if (ctrl && key == InputConstants.KEY_F) {
			search.focused = true;
			return true;
		}
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
			int i = shown.indexOf(selected());
			int next = Math.max(0, Math.min(shown.size() - 1, i + (key == InputConstants.KEY_DOWN ? 1 : -1)));
			if (next >= 0 && next < shown.size()) {
				select(shown.get(next));
				scroll.reveal(next * ROW_H, (next + 1) * ROW_H, listH);
			}
			return true;
		}
		if (key == InputConstants.KEY_SPACE && !search.focused) {
			ManagedMod m = selected();
			if (m != null && m.status() != ModStatus.LOCKED) {
				toggle(m);
			}
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (!search.focused) {
			if (text.isBlank()) {
				return false;
			}
			// Typing anywhere starts a search.
			search.focused = true;
		}
		search.typed(text);
		applyFilter();
		scroll.scrollTo(0);
		return true;
	}
}
