package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.BiomeEffectsFeature;
import dev.justaclient.feature.impl.BiomeEffectsFeature.Look;
import dev.justaclient.feature.setting.Setting;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import dev.justaclient.ui.widget.SettingWidgets;
import dev.justaclient.ui.widget.Widget;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * Every biome in a list, the one you stand in first, and the selected biome's look on the right: its particles, daylight
 * tint, fog colour and ambience, each with its own switch. Changes show in the world straight away.
 */
public final class BiomeEffectsScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 16f;
	private static final float LIST_W = 210f;
	private static final float GAP = 5f;

	private final InputBox search = new InputBox("", 32, "Search biomes", Icons.SEARCH);
	private final Scroller listScroll = new Scroller();
	private final Scroller setScroll = new Scroller();
	private final List<String> all;
	private final Map<Setting<?>, Widget> widgets = new IdentityHashMap<>();
	private final List<Widget> shownWidgets = new ArrayList<>();
	private final Button reset = new Button(0, 0, 70, 16, Icons.ROTATE_CCW, "Clear", false, this::clearSelected);
	private String selected;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float top;
	private float bottom;
	private float lx;
	private float sx;
	private float sw;
	private float setTop;

	public BiomeEffectsScreen(@Nullable Screen parent) {
		super("Biome effects", parent);
		all = biomes();
		String here = BiomeEffectsFeature.get().here();
		selected = !here.isEmpty() ? here : all.isEmpty() ? "minecraft:plains" : all.getFirst();
	}

	/** Every biome the game knows: vanilla's, the world's own (data packs), and any you gave a look. */
	private static List<String> biomes() {
		TreeSet<String> ids = new TreeSet<>();
		for (Field f : Biomes.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == ResourceKey.class) {
				try {
					ids.add(((ResourceKey<?>) f.get(null)).identifier().toString());
				} catch (IllegalAccessException ignored) {
					// Public fields; nothing to do.
				}
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			mc.level.registryAccess().lookupOrThrow(Registries.BIOME).listElementIds().forEach(k -> ids.add(k.identifier().toString()));
		}
		ids.addAll(BiomeEffectsFeature.get().looks().keySet());
		return new ArrayList<>(ids);
	}

	public static String name(String id) {
		int colon = id.indexOf(':');
		String key = "biome." + (colon < 0 ? "minecraft." + id : id.substring(0, colon) + "." + id.substring(colon + 1));
		String text = Component.translatable(key).getString();
		if (!text.equals(key)) {
			return text;
		}
		String path = colon < 0 ? id : id.substring(colon + 1);
		StringBuilder out = new StringBuilder();
		for (String w : path.split("[_/]")) {
			if (!w.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
			}
		}
		return out.toString();
	}

	private List<String> shown() {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		String here = BiomeEffectsFeature.get().here();
		List<String> out = new ArrayList<>();
		if (!here.isEmpty() && matches(here, q)) {
			out.add(here);
		}
		for (String id : all) {
			if (!id.equals(here) && matches(id, q)) {
				out.add(id);
			}
		}
		return out;
	}

	private static boolean matches(String id, String q) {
		return q.isEmpty() || id.contains(q) || name(id).toLowerCase(Locale.ROOT).contains(q);
	}

	private void layout() {
		pw = Math.min(700f, width - 24f);
		ph = Math.min(420f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		top = py + 58;
		bottom = py + ph - PAD;
		lx = px + PAD;
		search.place(lx, py + 36, LIST_W);
		sx = lx + LIST_W + 14;
		sw = px + pw - PAD - sx;
		setTop = top + 26;
		reset.setPosition(px + pw - PAD - 70, top);
	}

	private Look look() {
		return BiomeEffectsFeature.get().look(selected);
	}

	private List<Widget> settingsLayout() {
		shownWidgets.clear();
		float y = 0;
		for (Setting<?> s : look().settings()) {
			if (!s.isVisible()) {
				continue;
			}
			Widget w = widgets.computeIfAbsent(s, k -> SettingWidgets.create(k, sx, setTop, Math.min(sw, 320f)));
			w.setPosition(sx, setTop + y - setScroll.get());
			shownWidgets.add(w);
			y += w.height() + GAP;
		}
		setScroll.setMax(y - (bottom - setTop));
		return shownWidgets;
	}

	private void select(String id) {
		BiomeEffectsFeature.get().tidy();
		selected = id;
		widgets.clear();
		setScroll.scrollTo(0);
	}

	private void clearSelected() {
		BiomeEffectsFeature.get().clear(selected);
		widgets.clear();
	}

	private static int swatch(@Nullable Look look) {
		if (look == null || !look.changesAnything()) {
			return 0;
		}
		if (look.particles.get().ordinal() >= BiomeEffectsFeature.Particles.FIREFLIES.ordinal()) {
			return look.particleColor.argb();
		}
		if (look.fog.on()) {
			return look.fogColor.argb();
		}
		if (look.tint.on()) {
			return look.tintColor.argb();
		}
		return JacConfig.accent();
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		BiomeEffectsFeature feature = BiomeEffectsFeature.get();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.LEAF, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Biome effects", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Each biome's particles, daylight tint, fog colour and ambience, only on your screen", px + PAD + 15, py + PAD + 12,
			Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		if (!feature.isEnabled()) {
			String off = "Biome effects is off: click to turn it on";
			float w = JacText.width(off, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
			float ox = px + pw - PAD - 20 - w;
			boolean hot = offHit(mx, my, ox, w);
			Draw.rect(g, ox, py + 11, w, 15, 7.5f, Colors.fade(Colors.withAlpha(0xFFFBBF24, hot ? 0x60 : 0x38), a));
			JacText.draw(g, off, ox + 7, py + 15, Colors.fade(0xFFFBBF24, a), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
		}
		search.draw(g, mx, my, a);

		// The biome list
		List<String> list = shown();
		listScroll.setMax(list.size() * ROW - (bottom - top));
		float s = listScroll.get();
		String here = feature.here();
		g.enableScissor((int) lx, (int) top, (int) (lx + LIST_W), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float y = top + i * ROW - s;
			if (y + ROW < top || y > bottom) {
				continue;
			}
			String id = list.get(i);
			boolean on = id.equals(selected);
			boolean hot = mx >= lx && mx < lx + LIST_W && my >= y && my < y + ROW && my >= top && my < bottom;
			if (on || hot) {
				Draw.rect(g, lx, y, LIST_W, ROW - 1, 4f, Colors.fade(on ? Colors.withAlpha(accent, 0x40) : Theme.CONTROL_HOVER, a));
			}
			int dot = swatch(feature.existing(id));
			Draw.circle(g, lx + 8, y + 7.5f, 3f, Colors.fade(dot == 0 ? 0x30FFFFFF : dot, a));
			float right = id.equals(here) ? 36 : 6;
			JacText.draw(g, fit(name(id), Theme.LABEL, JacText.Weight.REGULAR, LIST_W - 22 - right), lx + 16, y + 4, Colors.fade(on ? Theme.TEXT
				: Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (id.equals(here)) {
				Draw.rect(g, lx + LIST_W - 34, y + 2.5f, 30, 10, 5f, Colors.fade(Colors.withAlpha(0xFF4ADE80, 0x40), a));
				JacText.draw(g, "Here", lx + LIST_W - 28, y + 4.5f, Colors.fade(0xFF4ADE80, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}
		g.disableScissor();
		if (list.isEmpty()) {
			JacText.draw(g, "Nothing matches", lx + 2, top + 4, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}
		Draw.rect(g, sx - 7, top, 0.75f, bottom - top, 0, Colors.fade(Theme.DIVIDER, a));

		// The selected biome's look
		JacText.draw(g, fit(name(selected), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD, sw - 90), sx, top + 1, Colors.fade(Theme.TEXT, a),
			Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		JacText.draw(g, selected + (selected.equals(here) ? "  ·  you are here" : ""), sx, top + 13, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		Look kept = feature.existing(selected);
		reset.active(kept != null && (kept.changesAnything() || !kept.on.on()));
		reset.render(g, mx, my, a);
		List<Widget> rows = settingsLayout();
		g.enableScissor((int) sx - 2, (int) setTop - 2, (int) (px + pw), (int) bottom);
		double wy = my >= setTop - 2 && my < bottom ? my : -1e4;
		for (Widget w : rows) {
			w.render(g, mx, wy, a);
		}
		g.disableScissor();
	}

	private boolean offHit(double mx, double my, float ox, float w) {
		return mx >= ox && mx < ox + w && my >= py + 11 && my < py + 26;
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		layout();
		if (button != 0) {
			return false;
		}
		search.click(mx, my);
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		BiomeEffectsFeature feature = BiomeEffectsFeature.get();
		if (!feature.isEnabled()) {
			String off = "Biome effects is off: click to turn it on";
			float w = JacText.width(off, Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
			if (offHit(mx, my, px + pw - PAD - 20 - w, w)) {
				feature.setEnabled(true);
				Profiles.save();
				return true;
			}
		}
		if (mx >= lx && mx < lx + LIST_W && my >= top && my < bottom) {
			List<String> list = shown();
			int i = (int) ((my - top + listScroll.get()) / ROW);
			if (i >= 0 && i < list.size()) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				select(list.get(i));
			}
			return true;
		}
		if (reset.mouseClicked(mx, my, button)) {
			return true;
		}
		if (my >= setTop - 2 && my < bottom) {
			for (Widget w : new ArrayList<>(shownWidgets)) {
				if (w.mouseClicked(mx, my, button)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void drag(double mx, double my, int button) {
		shownWidgets.forEach(w -> w.mouseDragged(mx, my));
	}

	@Override
	protected void release(double mx, double my, int button) {
		shownWidgets.forEach(w -> w.mouseReleased(mx, my));
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		if (mx < sx - 7) {
			listScroll.scroll(amount, ROW * 3);
		} else {
			setScroll.scroll(amount, 30);
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		for (Widget w : shownWidgets) {
			if (w.capturesKeys()) {
				w.keyPressed(key);
				return true;
			}
		}
		if (search.focused && search.key(key, ctrl)) {
			listScroll.scrollTo(0);
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		for (Widget w : shownWidgets) {
			if (w.capturesKeys()) {
				text.codePoints().forEach(w::charTyped);
				return true;
			}
		}
		if (!search.focused) {
			search.focused = true;
		}
		search.typed(text);
		listScroll.scrollTo(0);
		return true;
	}

	@Override
	public void onClose() {
		BiomeEffectsFeature.get().tidy();
		Profiles.save();
		super.onClose();
	}
}
