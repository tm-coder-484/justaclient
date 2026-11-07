package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.potions.BrewGraph;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.PotionGuideFeature;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.InputBox;
import dev.justaclient.ui.widget.Scroller;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;

/**
 * The potion guide: every potion that can be brewed, a search, and for the chosen one its effects, the brews from a water
 * bottle in order, and what to bring. "Brew this" makes it the brewing stand panel's target.
 */
public final class PotionGuideScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 17f;
	private static final List<String> FORMS = List.of(BrewGraph.POTION, BrewGraph.SPLASH, BrewGraph.LINGERING);
	private static final List<String> FORM_LABELS = List.of("Potion", "Splash", "Lingering");

	private final InputBox search = new InputBox("", 32, "Search potions or ingredients", Icons.SEARCH);
	private final Scroller listScroll = new Scroller();
	private @Nullable String selected;
	private String form = BrewGraph.POTION;
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float lx;
	private float lw;
	private float rx;
	private float rw;
	private float top;
	private float bottom;
	private float formY;
	private float brewY;

	public PotionGuideScreen(@Nullable Screen parent) {
		super("Potion guide", parent);
		BrewGraph.State t = PotionGuideFeature.get().target();
		if (t != null) {
			selected = t.potion();
			form = t.item();
		}
	}

	private void layout() {
		pw = Math.min(660f, width - 24f);
		ph = Math.min(390f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		lx = px + PAD;
		lw = Math.min(200f, (pw - PAD * 3) * 0.38f);
		rx = lx + lw + PAD;
		rw = px + pw - PAD - rx;
		top = py + 62;
		bottom = py + ph - PAD;
		search.place(lx, py + 38, lw);
	}

	private List<BrewGraph.State> shown(BrewGraph graph) {
		String q = search.get().trim().toLowerCase(Locale.ROOT);
		List<BrewGraph.State> out = new ArrayList<>();
		for (BrewGraph.State s : PotionGuideFeature.brewable(graph, BrewGraph.POTION)) {
			if (q.isEmpty() || InvItems.shortName(s).toLowerCase(Locale.ROOT).contains(q) || graph.route(s).orElse(List.of()).stream()
				.anyMatch(step -> InvItems.itemName(step.reagent()).toLowerCase(Locale.ROOT).contains(q))) {
				out.add(s);
			}
		}
		return out;
	}

	private BrewGraph.@Nullable State current(BrewGraph graph) {
		if (selected == null) {
			return null;
		}
		BrewGraph.State s = new BrewGraph.State(form, selected);
		return graph.route(s).isPresent() ? s : new BrewGraph.State(BrewGraph.POTION, selected);
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		BrewGraph graph = InvCompat.brewing();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.FLASK_ROUND, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Potion guide", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		List<BrewGraph.State> all = PotionGuideFeature.brewable(graph, BrewGraph.POTION);
		JacText.draw(g, all.size() + " potions, routes from a water bottle", px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));
		search.draw(g, mx, my, a);

		List<BrewGraph.State> list = shown(graph);
		listScroll.setMax(list.size() * ROW - (bottom - top));
		float s = listScroll.get();
		dev.justaclient.render.LegacyImages.enableScissor(g, (int) lx, (int) top, (int) (lx + lw), (int) bottom);
		for (int i = 0; i < list.size(); i++) {
			float y = top + i * ROW - s;
			if (y + ROW < top || y > bottom) {
				continue;
			}
			BrewGraph.State st = list.get(i);
			boolean hot = mx >= lx && mx < lx + lw && my >= y && my < y + ROW && my >= top && my < bottom;
			boolean chosen = st.potion().equals(selected);
			if (hot || chosen) {
				Draw.rect(g, lx, y, lw, ROW - 1, 4f, Colors.fade(chosen ? Colors.withAlpha(accent, 0x38) : Theme.CONTROL_HOVER, a));
			}
			InvItems.draw(g, InvItems.stack(st), lx + 4, y + 2, 12);
			JacText.draw(g, fit(InvItems.shortName(st), Theme.LABEL, JacText.Weight.REGULAR, lw - 26), lx + 21, y + 4.5f, Colors.fade(Theme.TEXT, a),
				Theme.LABEL, JacText.Weight.REGULAR);
		}
		g.disableScissor();
		if (list.isEmpty()) {
			JacText.draw(g, graph.isEmpty() ? "No brewing rules loaded yet: join a world" : "Nothing matches", lx + 4, top + 4,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		}

		Draw.rect(g, rx - PAD / 2f, py + 38, 0.75f, ph - 50, 0, Colors.fade(Theme.DIVIDER, a));
		BrewGraph.State cur = current(graph);
		if (cur == null) {
			JacText.draw(g, "Pick a potion on the left", rx, py + 42, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			return;
		}
		drawDetails(g, graph, cur, mx, my, a);
	}

	private void drawDetails(GuiGraphics g, BrewGraph graph, BrewGraph.State cur, int mx, int my, float a) {
		int accent = JacConfig.accent();
		float y = py + 38;
		InvItems.draw(g, InvItems.stack(cur), rx, y, 24);
		JacText.draw(g, fit(InvItems.name(cur), Theme.TITLE, JacText.Weight.SEMIBOLD, rw - 32), rx + 30, y + 2, Colors.fade(Theme.TEXT, a), Theme.TITLE,
			JacText.Weight.SEMIBOLD);
		String effects = effects(cur);
		JacText.draw(g, fit(effects, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, rw - 32), rx + 30, y + 15, Colors.fade(Theme.TEXT_DIM, a),
			Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
		y += 32;

		// Bottle kind.
		formY = y;
		float fx = rx;
		for (int i = 0; i < FORMS.size(); i++) {
			BrewGraph.State st = new BrewGraph.State(FORMS.get(i), cur.potion());
			boolean can = graph.route(st).isPresent();
			boolean on = FORMS.get(i).equals(cur.item());
			float w = JacText.width(FORM_LABELS.get(i), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
			boolean hot = can && mx >= fx && mx < fx + w && my >= y && my < y + 14;
			Draw.rect(g, fx, y, w, 14, 7f, Colors.fade(on ? accent : hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a * (can ? 1f : 0.4f)));
			JacText.draw(g, FORM_LABELS.get(i), fx + 7, y + 3.5f, Colors.fade(on ? 0xFFFFFFFF : Theme.TEXT, a * (can ? 1f : 0.5f)),
				Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD);
			fx += w + 4;
		}
		// Brew this.
		boolean isTarget = cur.equals(PotionGuideFeature.get().target());
		String label = isTarget ? "Brewing target" : "Brew this";
		float bw = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 26;
		float bx = rx + rw - bw;
		brewY = y - 2;
		boolean bhot = mx >= bx && mx < bx + bw && my >= brewY && my < brewY + 18;
		Draw.rect(g, bx, brewY, bw, 18, 5f, Colors.fade(isTarget ? Colors.withAlpha(accent, 0x40) : bhot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f)
			: accent, a));
		JacText.icon(g, isTarget ? Icons.CHECK : Icons.TARGET, bx + 7, brewY + 5.5f, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, label, bx + 19, brewY + 5.5f, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		y += 24;

		List<BrewGraph.Step> steps = graph.route(cur).orElse(List.of());
		JacText.draw(g, "Brews", rx, y, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 10;
		// Rows shrink on small windows so the whole route always fits above the shopping list.
		Map<String, Integer> bring = BrewGraph.shoppingList(steps);
		int chipRows = 1;
		float chipX = rx;
		for (Map.Entry<String, Integer> e : bring.entrySet()) {
			float w = JacText.width((e.getValue() > 1 ? e.getValue() + "x " : "") + InvItems.itemName(e.getKey()), Theme.SMALL + 0.5f,
				JacText.Weight.REGULAR) + 24;
			if (chipX + w > rx + rw && chipX > rx) {
				chipRows++;
				chipX = rx;
			}
			chipX += w + 4;
		}
		float step = Math.max(12f, Math.min(21f, (bottom - y - 18 - Math.min(chipRows, 2) * 18) / (steps.size() + 1)));
		float rowH = step - 3;
		float icon = Math.min(14f, rowH - 2);
		float ty = (rowH - JacText.lineHeight(Theme.LABEL)) / 2f;
		Draw.rect(g, rx, y, rw, rowH, 4f, Colors.fade(Theme.CONTROL, a));
		InvItems.draw(g, InvItems.stack(BrewGraph.WATER), rx + 4, y + (rowH - icon) / 2f, icon);
		JacText.draw(g, "Start with water bottles (up to 3)", rx + 24, y + ty, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
		y += step;
		for (int i = 0; i < steps.size(); i++) {
			BrewGraph.Step st = steps.get(i);
			Draw.rect(g, rx, y, rw, rowH, 4f, Colors.fade(Theme.CONTROL, a));
			JacText.draw(g, String.valueOf(i + 1), rx + 6, y + ty, Colors.fade(accent, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			InvItems.draw(g, InvItems.stack(st.reagent()), rx + 16, y + (rowH - icon) / 2f, icon);
			String reagent = InvItems.itemName(st.reagent());
			JacText.draw(g, reagent, rx + 35, y + ty, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			float ax = rx + 35 + JacText.width(reagent, Theme.LABEL, JacText.Weight.SEMIBOLD) + 6;
			JacText.icon(g, Icons.CHEVRON_RIGHT, ax, y + ty + 0.5f, 6.5f, Colors.fade(Theme.TEXT_DIM, a));
			InvItems.draw(g, InvItems.stack(st.result()), ax + 11, y + (rowH - icon) / 2f, icon);
			JacText.draw(g, fit(BrewGraph.POTION.equals(st.result().item()) ? InvItems.shortName(st.result()) : InvItems.name(st.result()), Theme.LABEL, JacText.Weight.REGULAR, rx + rw - ax - 34), ax + 29, y + ty,
				Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			y += step;
		}
		y += 4;
		JacText.draw(g, "Bring", rx, y, Colors.fade(accent, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		y += 10;
		if (y + chipRows * 18 > bottom + 2) {
			// No room for the chips on a small window: one line of names instead.
			List<String> names = new ArrayList<>();
			bring.forEach((item, n) -> names.add((n > 1 ? n + "x " : "") + InvItems.itemName(item)));
			JacText.draw(g, fit(String.join(", ", names), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, rw), rx, y, Colors.fade(Theme.TEXT, a),
				Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			return;
		}
		float cx = rx;
		for (Map.Entry<String, Integer> e : bring.entrySet()) {
			String text = (e.getValue() > 1 ? e.getValue() + "x " : "") + InvItems.itemName(e.getKey());
			float w = JacText.width(text, Theme.SMALL + 0.5f, JacText.Weight.REGULAR) + 24;
			if (cx + w > rx + rw && cx > rx) {
				cx = rx;
				y += 18;
			}
			Draw.rect(g, cx, y, w, 15, 7.5f, Colors.fade(Theme.CONTROL, a));
			InvItems.draw(g, InvItems.stack(e.getKey()), cx + 4, y + 2, 11);
			JacText.draw(g, text, cx + 18, y + 4, Colors.fade(Theme.TEXT, a), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			cx += w + 4;
		}
		y += 20;
		if (y + 6 <= bottom) {
			JacText.draw(g, fit("One of each ingredient brews up to 3 bottles at once. Blaze powder fuels the stand.", Theme.SMALL,
				JacText.Weight.REGULAR, rw), rx, y, Colors.fade(Theme.TEXT_DIM, a),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	/** "Speed II 1:30, Strength 3:00". Lingering bottles last a quarter as long. */
	private static String effects(BrewGraph.State state) {
		Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.item.alchemy.Potion>> potion = InvItems.potion(state.potion());
		if (potion.isEmpty() || potion.get().value().getEffects().isEmpty()) {
			return "No effect";
		}
		float scale = state.item().equals(BrewGraph.LINGERING) ? 0.25f : 1f;
		List<String> parts = new ArrayList<>();
		for (MobEffectInstance e : potion.get().value().getEffects()) {
			String name = e.getEffect().value().getDisplayName().getString();
			if (e.getAmplifier() > 0) {
				name += " " + roman(e.getAmplifier() + 1);
			}
			if (e.getDuration() > 1) {
				int secs = (int) (e.getDuration() * scale / 20);
				name += " " + secs / 60 + ":" + String.format("%02d", secs % 60);
			}
			parts.add(name);
		}
		return String.join(", ", parts);
	}

	private static String roman(int n) {
		return switch (n) {
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(n);
		};
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		search.click(mx, my);
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		BrewGraph graph = InvCompat.brewing();
		if (mx >= lx && mx < lx + lw && my >= top && my < bottom) {
			List<BrewGraph.State> list = shown(graph);
			int i = (int) ((my - top + listScroll.get()) / ROW);
			if (i >= 0 && i < list.size()) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				selected = list.get(i).potion();
			}
			return true;
		}
		BrewGraph.State cur = current(graph);
		if (cur == null) {
			return false;
		}
		if (my >= formY && my < formY + 14) {
			float fx = rx;
			for (int i = 0; i < FORMS.size(); i++) {
				float w = JacText.width(FORM_LABELS.get(i), Theme.SMALL + 0.5f, JacText.Weight.SEMIBOLD) + 14;
				if (mx >= fx && mx < fx + w && graph.route(new BrewGraph.State(FORMS.get(i), cur.potion())).isPresent()) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					form = FORMS.get(i);
					return true;
				}
				fx += w + 4;
			}
		}
		if (my >= brewY && my < brewY + 18 && mx >= rx + rw - 140) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			PotionGuideFeature.get().setTarget(cur.equals(PotionGuideFeature.get().target()) ? null : cur);
			return true;
		}
		return false;
	}

	@Override
	protected boolean scroll(double mx, double my, double amount) {
		listScroll.scroll(amount, ROW * 2);
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		if (search.focused && search.key(key, ctrl)) {
			listScroll.scrollTo(0);
			return true;
		}
		return false;
	}

	@Override
	protected boolean typed(String text) {
		if (!search.focused) {
			search.focused = true;
		}
		search.typed(text);
		listScroll.scrollTo(0);
		return true;
	}
}
