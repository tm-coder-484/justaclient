package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.KeybindProfiles;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.KeybindsFeature;
import dev.justaclient.feature.setting.KeySetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.PopupMenu;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * Every key bind in one searchable list, with clashes marked (including the client's own feature toggle keys), mouse
 * buttons, reset, and saved key sets that can be tied to a server.
 */
public final class KeybindsScreen extends JacScreen {
	private static final float ROW_H = 18f;
	private static final float HEAD_H = 16f;
	private static final float PAD = 12f;
	private static final int BAD = 0xFFF87171;
	private static final int WARN = 0xFFFBBF24;

	private enum Filter {
		ALL("All"), CLASHES("Clashes"), CHANGED("Changed");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	/** A list line: a category heading or a binding. */
	private record Line(@Nullable String heading, @Nullable KeyMapping key) {
	}

	private final InputBox search = new InputBox("", 64, "Search binds or keys", Icons.SEARCH);
	private final InputBox nameBox = new InputBox("", 32, "Name for this key set", Icons.KEYBOARD);
	private boolean naming;
	private final Scroller scroll = new Scroller();
	private Filter filter = Filter.ALL;
	private List<Line> lines = List.of();
	private Map<String, List<String>> clashes = Map.of();
	private @Nullable KeyMapping capturing;
	private @Nullable PopupMenu menu;
	private final List<Button> buttons = new ArrayList<>();
	private @Nullable Button setButton;
	private String status = "";
	/** The character typed by the key just bound, which should not start a search. */
	private boolean swallowTyped;

	private float px;
	private float py;
	private float pw;
	private float ph;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float chipsY;

	public KeybindsScreen(@Nullable Screen parent) {
		super("Key binds", parent);
	}

	@Override
	protected void setup() {
		buttons.clear();
		setButton = new Button(0, 0, 110, 16, Icons.LAYERS, "Key sets", false, this::openSets);
		buttons.add(setButton);
		buttons.add(new Button(0, 0, 80, 16, Icons.ROTATE_CCW, "Reset all", false, () -> open(new JacConfirmScreen(this, Icons.ROTATE_CCW,
			"Reset all key binds", "Put every key bind back to its default? Save a key set first to keep this one.", "Reset", () -> {
				for (KeyMapping k : minecraft.options.keyMappings) {
					k.setKey(k.getDefaultKey());
				}
				KeyMapping.resetMapping();
				minecraft.options.save();
				open(this);
			}))));
		rebuild();
	}

	private static String keyName(KeyMapping k) {
		return k.isUnbound() ? "Not bound" : k.getTranslatedKeyMessage().getString();
	}

	private void rebuild() {
		// Clashes: binds on the same key, plus the client's feature toggle keys.
		Map<String, List<String>> byKey = new HashMap<>();
		for (KeyMapping k : minecraft.options.keyMappings) {
			// Debug binds are F3 combinations, so sharing a letter with them is not a clash.
			if (!k.isUnbound() && k.getCategory() != KeyMapping.Category.DEBUG) {
				byKey.computeIfAbsent(k.saveString(), s -> new ArrayList<>()).add(net.minecraft.client.resources.language.I18n.get(k.getName()));
			}
		}
		for (Feature f : Features.all()) {
			int code = f.toggleKey.get();
			if (code != KeySetting.NONE) {
				byKey.computeIfAbsent(keyboardKey(code).getName(), s -> new ArrayList<>()).add(f.name + " (client toggle)");
			}
		}
		Map<String, List<String>> found = new HashMap<>();
		for (KeyMapping k : minecraft.options.keyMappings) {
			List<String> same = byKey.get(k.saveString());
			if (!k.isUnbound() && k.getCategory() != KeyMapping.Category.DEBUG && same != null && same.size() > 1) {
				List<String> others = new ArrayList<>(same);
				others.remove(net.minecraft.client.resources.language.I18n.get(k.getName()));
				found.put(k.getName(), others);
			}
		}
		clashes = found;
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		Map<String, List<KeyMapping>> groups = new LinkedHashMap<>();
		KeyMapping[] all = minecraft.options.keyMappings.clone();
		java.util.Arrays.sort(all);
		for (KeyMapping k : all) {
			String name = net.minecraft.client.resources.language.I18n.get(k.getName());
			boolean match = q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || keyName(k).toLowerCase(Locale.ROOT).contains(q);
			boolean pass = switch (filter) {
				case ALL -> true;
				case CLASHES -> clashes.containsKey(k.getName());
				case CHANGED -> !k.isDefault();
			};
			if (match && pass) {
				groups.computeIfAbsent(k.getCategory().label().getString(), c -> new ArrayList<>()).add(k);
			}
		}
		List<Line> out = new ArrayList<>();
		groups.forEach((heading, keys) -> {
			out.add(new Line(heading, null));
			for (KeyMapping k : keys) {
				out.add(new Line(null, k));
			}
		});
		lines = out;
	}

	private float contentHeight() {
		float h = 0;
		for (Line l : lines) {
			h += l.heading != null ? HEAD_H : ROW_H;
		}
		return h;
	}

	private void bind(KeyMapping k, InputConstants.Key key) {
		k.setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		capturing = null;
		rebuild();
		List<String> others = clashes.get(k.getName());
		status = others == null ? "" : "Also on " + keyName(k) + ": " + String.join(", ", others);
	}

	// Key sets

	private KeybindProfiles sets() {
		return KeybindsFeature.get().data();
	}

	private void openSets() {
		KeybindProfiles d = sets();
		ServerData server = minecraft.getCurrentServer();
		PopupMenu m = new PopupMenu();
		if (!d.profiles.isEmpty()) {
			m.header("Use a key set");
			for (String name : d.profiles.keySet()) {
				m.add(Icons.KEYBOARD, name, () -> {
					KeybindsFeature.apply(d.profiles.get(name));
					rebuild();
					status = "Key set " + name + " is on";
				});
			}
		}
		m.add(Icons.PLUS, "Save these binds as a set", () -> {
			naming = true;
			nameBox.set("");
			nameBox.focused = true;
		});
		if (!d.profiles.isEmpty()) {
			m.header("Delete");
			for (String name : d.profiles.keySet()) {
				m.add(Icons.TRASH, name, () -> {
					d.removeProfile(name);
					KeybindsFeature.get().save();
					status = "Deleted key set " + name;
				});
			}
		}
		if (server != null && !d.profiles.isEmpty()) {
			String current = d.profileFor(server.ip);
			m.header("Put on when joining " + server.name);
			for (String name : d.profiles.keySet()) {
				m.add(name.equals(current) ? Icons.CHECK : Icons.SERVER, name, () -> {
					d.setProfileFor(server.ip, name);
					KeybindsFeature.get().save();
					status = name + " goes on whenever you join " + server.name;
				});
			}
			if (current != null) {
				m.add(Icons.X, "No key set for this server", () -> {
					d.setProfileFor(server.ip, null);
					KeybindsFeature.get().save();
					status = server.name + " no longer changes your binds";
				});
			}
		}
		menu = m.openAbove(setButton.x(), setButton.y() - 3, width, height);
	}

	private void finishNaming() {
		String name = nameBox.get().trim();
		naming = false;
		nameBox.focused = false;
		if (name.isEmpty()) {
			return;
		}
		sets().profiles.put(name, KeybindsFeature.snapshot());
		KeybindsFeature.get().save();
		status = "Saved key set " + name;
	}

	// Drawing

	private void layout() {
		pw = Math.min(460f, width - 24f);
		ph = Math.min(360f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		listX = px + PAD;
		listW = pw - PAD * 2;
		chipsY = py + 40;
		listY = chipsY + 18;
		listH = py + ph - 34 - listY;
		search.place(px + pw - PAD - 150 - 20, py + 10, 150);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		int accent = JacConfig.accent();
		JacText.icon(g, Icons.KEYBOARD, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Key binds", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, minecraft.options.keyMappings.length + " binds" + (clashes.isEmpty() ? "" : " · " + clashes.size() + " clashing"), px + PAD + 15,
			py + PAD + 12, Colors.fade(clashes.isEmpty() ? Theme.TEXT_DIM : BAD, a), Theme.SMALL, JacText.Weight.REGULAR);
		search.draw(g, mx, my, a);
		boolean closeHot = mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHot ? Theme.TEXT : Theme.TEXT_DIM, a));
		float cx = listX;
		for (Filter f : Filter.values()) {
			String label = f.label + (f == Filter.CLASHES && !clashes.isEmpty() ? " " + clashes.size() : "");
			float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			boolean on = f == filter;
			boolean hot = mx >= cx && mx < cx + w && my >= chipsY && my < chipsY + 13;
			Draw.rect(g, cx, chipsY, w, 13, 6.5f, Colors.fade(on ? Colors.withAlpha(accent, 0x55) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.draw(g, label, cx + 6, chipsY + 3, Colors.fade(on ? Theme.TEXT : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			cx += w + 4;
		}
		if (capturing != null) {
			String hint = "Press a key or click a mouse button · Esc unbinds";
			JacText.draw(g, hint, listX + listW - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), chipsY + 3, Colors.fade(accent, a),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
		drawList(g, mx, my, a);
		float fy = py + ph - 26;
		Draw.rect(g, px + PAD, fy - 6, pw - PAD * 2, 0.75f, 0, Colors.fade(Theme.DIVIDER, a));
		float bx = px + PAD;
		for (Button b : buttons) {
			b.setPosition(bx, fy);
			b.render(g, mx, my, a);
			bx += b.width() + 5;
		}
		if (naming) {
			nameBox.place(bx + 4, fy, px + pw - PAD - bx - 4);
			nameBox.draw(g, mx, my, a);
		} else if (!status.isEmpty()) {
			JacText.draw(g, fit(status, Theme.SMALL, JacText.Weight.REGULAR, px + pw - PAD - bx - 6), bx + 4, fy + 4.5f,
				Colors.fade(status.startsWith("Also on") ? WARN : Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
		if (menu != null) {
			g.nextStratum();
			menu.draw(g, mx, my);
		}
	}

	private void drawList(GuiGraphics g, int mx, int my, float a) {
		scroll.setMax(contentHeight() - listH);
		float s = scroll.get();
		g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + listH));
		int accent = JacConfig.accent();
		float y = listY - s;
		float chipW = Math.min(110f, listW * 0.3f);
		for (Line l : lines) {
			float h = l.heading != null ? HEAD_H : ROW_H;
			if (y + h < listY) {
				y += h;
				continue;
			}
			if (y > listY + listH) {
				break;
			}
			if (l.heading != null) {
				JacText.draw(g, l.heading.toUpperCase(Locale.ROOT), listX + 2, y + 6, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
					JacText.Weight.SEMIBOLD);
				y += h;
				continue;
			}
			KeyMapping k = l.key;
			boolean hot = menu == null && my >= y && my < y + h && my >= listY && my < listY + listH && mx >= listX && mx < listX + listW;
			if (hot) {
				Draw.rect(g, listX, y, listW, h, 4f, Colors.fade(0x0CFFFFFF, a));
			}
			List<String> others = clashes.get(k.getName());
			String name = net.minecraft.client.resources.language.I18n.get(k.getName());
			float nameMax = listW - chipW - 34;
			String shownName = fit(name, Theme.LABEL, JacText.Weight.REGULAR, nameMax);
			JacText.draw(g, shownName, listX + 6, y + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (others != null) {
				float nx = listX + 6 + JacText.width(shownName, Theme.LABEL, JacText.Weight.REGULAR) + 6;
				String clash = "also " + String.join(", ", others);
				if (nameMax - (nx - listX - 6) > 30) {
					JacText.draw(g, fit(clash, Theme.SMALL, JacText.Weight.REGULAR, nameMax - (nx - listX - 6)), nx, y + 6, Colors.fade(BAD, a * 0.9f),
						Theme.SMALL, JacText.Weight.REGULAR);
				}
			}
			float chipX = listX + listW - chipW - 22;
			boolean cap = k == capturing;
			boolean chipHot = hot && mx >= chipX && mx < chipX + chipW;
			int fill = cap ? Colors.withAlpha(accent, 0x55) : others != null ? Colors.withAlpha(BAD, 0x30) : chipHot ? Theme.CONTROL_HOVER : Theme.CONTROL;
			Draw.rect(g, chipX, y + 2, chipW, h - 4, 5f, Colors.fade(fill, a));
			if (cap) {
				Draw.outline(g, chipX, y + 2, chipW, h - 4, 5f, 0.75f, Colors.fade(accent, a));
			}
			String label = cap ? "> " + keyName(k) + " <" : keyName(k);
			JacText.drawCentered(g, fit(label, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD, chipW - 8), chipX + chipW / 2f, y + 5.5f,
				Colors.fade(k.isUnbound() && !cap ? Theme.TEXT_DIM : others != null ? BAD : Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			if (!k.isDefault()) {
				boolean resetHot = hot && mx >= listX + listW - 18;
				JacText.icon(g, Icons.ROTATE_CCW, listX + listW - 15, y + 5.5f, 7f, Colors.fade(resetHot ? accent : Theme.TEXT_DIM, a));
			}
			y += h;
		}
		g.disableScissor();
		if (lines.isEmpty()) {
			JacText.drawCentered(g, filter == Filter.CLASHES ? "No clashes" : "Nothing matches", listX + listW / 2f, listY + 20,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		if (scroll.max() > 0) {
			float barH = Math.max(16f, listH * listH / (listH + scroll.max()));
			float barY = listY + (listH - barH) * (s / scroll.max());
			Draw.rect(g, listX + listW + 3, barY, 2, barH, 1f, Colors.fade(0x50FFFFFF, a));
		}
	}

	// Input

	private @Nullable KeyMapping keyAt(double mx, double my) {
		if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH) {
			return null;
		}
		float y = listY - scroll.get();
		for (Line l : lines) {
			float h = l.heading != null ? HEAD_H : ROW_H;
			if (my >= y && my < y + h) {
				return l.key;
			}
			y += h;
		}
		return null;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (capturing != null) {
			// Any mouse button while waiting becomes the bind.
			bind(capturing, mouseKey(button));
			return true;
		}
		if (menu != null) {
			PopupMenu m = menu;
			menu = null;
			m.click(mx, my);
			return true;
		}
		if (naming && !nameBox.inside(mx, my)) {
			finishNaming();
		}
		if (button != 0) {
			return false;
		}
		if (naming && nameBox.click(mx, my)) {
			return true;
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
		float cx = listX;
		for (Filter f : Filter.values()) {
			String label = f.label + (f == Filter.CLASHES && !clashes.isEmpty() ? " " + clashes.size() : "");
			float w = JacText.width(label, Theme.SMALL, JacText.Weight.SEMIBOLD) + 12;
			if (mx >= cx && mx < cx + w && my >= chipsY && my < chipsY + 13) {
				filter = f;
				rebuild();
				scroll.scrollTo(0);
				return true;
			}
			cx += w + 4;
		}
		KeyMapping k = keyAt(mx, my);
		if (k != null) {
			if (!k.isDefault() && mx >= listX + listW - 18) {
				bind(k, k.getDefaultKey());
				status = net.minecraft.client.resources.language.I18n.get(k.getName()) + " is back to " + keyName(k);
				return true;
			}
			capturing = k;
			status = "";
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		scroll.scroll(amount, ROW_H * 2f);
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		swallowTyped = false;
		if (capturing != null) {
			bind(capturing, key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : keyboardKey(key));
			swallowTyped = true;
			return true;
		}
		if (menu != null && key == InputConstants.KEY_ESCAPE) {
			menu = null;
			return true;
		}
		if (naming) {
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				finishNaming();
			} else if (key == InputConstants.KEY_ESCAPE) {
				naming = false;
				nameBox.focused = false;
			} else {
				nameBox.key(key, ctrl);
			}
			return true;
		}
		if (search.focused) {
			if (key == InputConstants.KEY_ESCAPE) {
				search.focused = false;
				return true;
			}
			String before = search.get();
			boolean used = search.key(key, ctrl);
			if (!before.equals(search.get())) {
				rebuild();
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
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (capturing != null || swallowTyped) {
			swallowTyped = false;
			return true;
		}
		if (naming) {
			nameBox.typed(text);
			return true;
		}
		if (!search.focused) {
			if (text.isBlank()) {
				return false;
			}
			search.focused = true;
		}
		search.typed(text);
		rebuild();
		scroll.scrollTo(0);
		return true;
	}
}
