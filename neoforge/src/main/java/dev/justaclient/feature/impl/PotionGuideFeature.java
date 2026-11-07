package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.potions.BrewGraph;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.inventory.ContainerOverlays;
import dev.justaclient.inventory.InvCompat;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Brewing help. A panel beside the brewing stand shows what the ingredient in the top slot will make of each bottle, and,
 * once you pick a target potion, every brew on the way there with the next one marked and its ingredient ringed in your
 * inventory. The full guide ({@code /potions}) lists every potion with its route. Routes come from the game's own brewing
 * rules (on 1.21.11 the server's, so data pack potions show up too).
 */
public final class PotionGuideFeature extends Feature {
	private static final int GREEN = 0xFF4ADE80;
	private static final int RED = 0xFFF87171;
	private static final float W = 138;
	private static final float ROW = 12;

	private final BoolSetting panel = bool("panel", "Panel beside the brewing stand", true);
	private final BoolSetting ringNext = bool("ring_next", "Ring the next ingredient", true);
	private final TextSetting target = add(new TextSetting("target", "Target", "", 96)).visibleWhen(() -> false);

	private boolean picking;
	private float pickScroll;

	public PotionGuideFeature() {
		super("potion_guide", "Potion guide", "Brewing routes for every potion, and a helper panel at the brewing stand", Category.INVENTORY,
			Icons.FLASK_ROUND, true);
	}

	public static PotionGuideFeature get() {
		return Features.get(PotionGuideFeature.class);
	}

	public static void init() {
		ContainerOverlays.register(get().new Panel());
	}

	/** The potion you are brewing towards, or null. Stored as {@code item|potion}. */
	public BrewGraph.@Nullable State target() {
		String t = target.get();
		int bar = t.indexOf('|');
		return bar <= 0 ? null : new BrewGraph.State(t.substring(0, bar), t.substring(bar + 1));
	}

	public void setTarget(BrewGraph.@Nullable State state) {
		boolean changed = state != null && !state.equals(target());
		target.set(state == null ? "" : state.item() + "|" + state.potion());
		Profiles.save();
		if (changed) {
			// Point at a chest holding what the route needs that you don't carry.
			List<BrewGraph.Step> route = InvCompat.brewing().route(state).orElse(List.of());
			java.util.Map<String, Integer> need = new java.util.LinkedHashMap<>(BrewGraph.shoppingList(route));
			need.merge("minecraft:blaze_powder", 1, Math::max);
			dev.justaclient.inventory.StorageFinder.offer(List.copyOf(dev.justaclient.inventory.StorageFinder.missing(need).keySet()),
				"For " + InvItems.shortName(state));
		}
	}

	/** Every potion the rules can make from water, drinkable ones first by name, for pickers. */
	public static List<BrewGraph.State> brewable(BrewGraph graph, String bottle) {
		List<BrewGraph.State> out = new ArrayList<>();
		for (BrewGraph.State s : graph.reachable()) {
			if (s.item().equals(bottle) && !s.equals(BrewGraph.WATER)) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparing(InvItems::shortName));
		return out;
	}

	private final class Panel implements ContainerOverlays.Overlay {
		@Override
		public boolean applies(AbstractContainerScreen<?> screen) {
			return isEnabled() && panel.on() && screen instanceof BrewingStandScreen;
		}

		@Override
		public float width(ContainerOverlays.Box box) {
			return W;
		}

		private List<BrewGraph.State> bottles(BrewingStandMenu menu) {
			List<BrewGraph.State> out = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				BrewGraph.State s = InvItems.state(menu.slots.get(i).getItem());
				if (s != null && !out.contains(s)) {
					out.add(s);
				}
			}
			return out;
		}

		@Override
		public float height(ContainerOverlays.Box box) {
			if (picking) {
				return box.height();
			}
			BrewGraph graph = InvCompat.brewing();
			BrewingStandMenu menu = (BrewingStandMenu) box.screen().getMenu();
			int now = Math.max(1, bottles(menu).size());
			BrewGraph.State t = target();
			int steps = t == null ? 1 : graph.route(t).map(List::size).orElse(1);
			return 18 + 10 + now * ROW + 8 + 10 + 16 + steps * ROW + 6;
		}

		@Override
		public void draw(ContainerOverlays.Box box, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			BrewGraph graph = InvCompat.brewing();
			if (picking) {
				drawPicker(graph, g, x, y, w, h, mx, my);
				return;
			}
			BrewingStandMenu menu = (BrewingStandMenu) box.screen().getMenu();
			float ly = ContainerOverlays.header(g, Icons.FLASK_ROUND, "Brewing guide", x, y, w);
			ItemStack reagent = menu.slots.get(3).getItem();
			String reagentId = reagent.isEmpty() ? null : InvItems.id(reagent.getItem());
			JacText.draw(g, reagent.isEmpty() ? "No ingredient in the top slot" : "With " + reagent.getHoverName().getString(), x + 6, ly,
				Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			ly += 10;
			List<BrewGraph.State> now = bottles(menu);
			if (now.isEmpty()) {
				JacText.draw(g, "No bottles yet", x + 8, ly + 2, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			for (BrewGraph.State s : now) {
				InvItems.draw(g, InvItems.stack(s), x + 6, ly, 10);
				Optional<BrewGraph.State> out = reagentId == null ? Optional.empty() : graph.result(s, reagentId);
				String text = out.map(InvItems::shortName).map(n -> "> " + n).orElse(reagentId == null ? InvItems.shortName(s) : InvItems.shortName(s) + ": no change");
				JacText.draw(g, JacScreen.fit(text, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 26), x + 19, ly + 2,
					out.isPresent() ? Theme.TEXT : Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				ly += ROW;
			}
			ly += 4;
			Draw.rect(g, x + 6, ly, w - 12, 0.75f, 0, Theme.DIVIDER);
			ly += 4;

			BrewGraph.State t = target();
			JacText.draw(g, "Target", x + 6, ly, JacConfig.accent(), Theme.SMALL, JacText.Weight.SEMIBOLD);
			String change = t == null ? "Pick" : "Change";
			float cw = JacText.width(change, Theme.SMALL, JacText.Weight.SEMIBOLD);
			boolean hot = mx >= x + w - 10 - cw && mx < x + w && my >= ly - 2 && my < ly + 9;
			JacText.draw(g, change, x + w - 6 - cw, ly, hot ? Theme.TEXT : Theme.TEXT_DIM, Theme.SMALL, JacText.Weight.SEMIBOLD);
			ly += 10;
			if (t == null) {
				JacText.draw(g, "Pick a potion to see its route", x + 6, ly + 2, Theme.TEXT_DIM, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				return;
			}
			InvItems.draw(g, InvItems.stack(t), x + 6, ly, 12);
			JacText.draw(g, JacScreen.fit(InvItems.name(t), Theme.LABEL, JacText.Weight.SEMIBOLD, w - 28), x + 21, ly + 2.5f, Theme.TEXT, Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			ly += 16;
			Optional<List<BrewGraph.Step>> route = graph.route(t);
			if (route.isEmpty()) {
				JacText.draw(g, "Can't be brewed here", x + 6, ly + 2, RED, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				return;
			}
			BrewGraph.State current = now.size() == 1 ? now.getFirst() : null;
			Optional<BrewGraph.Step> next = current == null ? Optional.empty() : graph.next(current, t);
			int doneUpTo = -1;
			for (int i = 0; i < route.get().size(); i++) {
				if (current != null && route.get().get(i).result().equals(current)) {
					doneUpTo = i;
				}
			}
			for (int i = 0; i < route.get().size(); i++) {
				BrewGraph.Step step = route.get().get(i);
				boolean done = i <= doneUpTo;
				boolean isNext = next.isPresent() && next.get().equals(step);
				if (isNext) {
					Draw.rect(g, x + 3, ly - 1, w - 6, ROW, 3f, Colors.withAlpha(JacConfig.accent(), 0x38));
				}
				InvItems.draw(g, InvItems.stack(step.reagent()), x + 6, ly, 10);
				String text = InvItems.itemName(step.reagent()) + " > " + InvItems.shortName(step.result());
				JacText.draw(g, JacScreen.fit(text, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 32), x + 19, ly + 2,
					done ? Theme.TEXT_DIM : Theme.TEXT, Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
				if (done) {
					JacText.icon(g, Icons.CHECK, x + w - 12, ly + 2, 6.5f, GREEN);
				}
				ly += ROW;
			}
			if (current != null && current.equals(t)) {
				JacText.draw(g, "Done", x + w - 30, y + 6, GREEN, Theme.SMALL, JacText.Weight.SEMIBOLD);
			}
		}

		private void drawPicker(BrewGraph graph, GuiGraphics g, float x, float y, float w, float h, double mx, double my) {
			float ly = ContainerOverlays.header(g, Icons.FLASK_ROUND, "Pick a target", x, y, w);
			JacText.icon(g, Icons.X, x + w - 13, y + 6, 7f, Theme.TEXT_DIM);
			List<BrewGraph.State> list = brewable(graph, BrewGraph.POTION);
			float top = ly;
			float bottom = y + h - 4;
			float max = Math.max(0, list.size() * ROW - (bottom - top));
			pickScroll = Math.max(0, Math.min(max, pickScroll));
			dev.justaclient.render.LegacyImages.enableScissor(g, (int) x, (int) top, (int) (x + w), (int) bottom);
			for (int i = 0; i < list.size(); i++) {
				float ry = top + i * ROW - pickScroll;
				if (ry + ROW < top || ry > bottom) {
					continue;
				}
				BrewGraph.State s = list.get(i);
				boolean hot = mx >= x && mx < x + w && my >= ry && my < ry + ROW && my >= top && my < bottom;
				boolean chosen = s.equals(target());
				if (hot || chosen) {
					Draw.rect(g, x + 3, ry, w - 6, ROW, 3f, hot ? Theme.CONTROL_HOVER : Colors.withAlpha(JacConfig.accent(), 0x30));
				}
				InvItems.draw(g, InvItems.stack(s), x + 6, ry + 1, 10);
				JacText.draw(g, JacScreen.fit(InvItems.shortName(s), Theme.SMALL + 0.5f, JacText.Weight.REGULAR, w - 30), x + 19, ry + 3, Theme.TEXT,
					Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			}
			g.disableScissor();
		}

		@Override
		public boolean click(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, int button) {
			if (button != 0) {
				return true;
			}
			if (picking) {
				if (my < y + 18) {
					if (mx >= x + w - 18) {
						picking = false;
					}
					return true;
				}
				List<BrewGraph.State> list = brewable(InvCompat.brewing(), BrewGraph.POTION);
				int i = (int) ((my - (y + 18) + pickScroll) / ROW);
				if (i >= 0 && i < list.size()) {
					GuiSoundsFeature.menuClick();
					BrewGraph.State chosen = list.get(i);
					// Keep the bottle kind the old target had (splash or lingering).
					BrewGraph.State old = target();
					if (old != null && !old.item().equals(BrewGraph.POTION)) {
						BrewGraph.State other = new BrewGraph.State(old.item(), chosen.potion());
						if (InvCompat.brewing().route(other).isPresent()) {
							chosen = other;
						}
					}
					setTarget(chosen);
					picking = false;
				}
				return true;
			}
			// The Pick/Change link sits on the Target line; find it the same way draw() does.
			BrewingStandMenu menu = (BrewingStandMenu) box.screen().getMenu();
			float targetY = y + 18 + 10 + Math.max(1, bottles(menu).size()) * ROW + 8;
			if (my >= targetY - 2 && my < targetY + 9 && mx >= x + w - 40) {
				GuiSoundsFeature.menuClick();
				picking = true;
				pickScroll = 0;
			}
			// Clicking the target cycles potion, splash, lingering (where those can be brewed).
			BrewGraph.State t = target();
			if (t != null && my >= targetY + 10 && my < targetY + 24) {
				List<String> forms = List.of(BrewGraph.POTION, BrewGraph.SPLASH, BrewGraph.LINGERING);
				int at = forms.indexOf(t.item());
				for (int i = 1; i <= forms.size(); i++) {
					BrewGraph.State other = new BrewGraph.State(forms.get((at + i) % forms.size()), t.potion());
					if (InvCompat.brewing().route(other).isPresent()) {
						GuiSoundsFeature.menuClick();
						setTarget(other);
						break;
					}
				}
			}
			return true;
		}

		@Override
		public boolean scroll(ContainerOverlays.Box box, float x, float y, float w, float h, double mx, double my, double amount) {
			if (picking) {
				pickScroll -= (float) amount * ROW * 2;
			}
			return true;
		}

		@Override
		public void decorate(ContainerOverlays.Box box, GuiGraphics g, double mx, double my) {
			BrewGraph.State t = target();
			if (!ringNext.on() || t == null) {
				return;
			}
			BrewingStandMenu menu = (BrewingStandMenu) box.screen().getMenu();
			List<BrewGraph.State> now = bottles(menu);
			if (now.size() != 1) {
				return;
			}
			Optional<BrewGraph.Step> next = InvCompat.brewing().next(now.getFirst(), t);
			if (next.isEmpty()) {
				return;
			}
			String want = next.get().reagent();
			ItemStack inTop = menu.slots.get(3).getItem();
			if (!inTop.isEmpty() && InvItems.id(inTop.getItem()).equals(want)) {
				box.outline(g, menu.slots.get(3), GREEN);
				return;
			}
			if (!inTop.isEmpty()) {
				box.outline(g, menu.slots.get(3), RED);
			}
			for (int i = 5; i < menu.slots.size(); i++) {
				Slot slot = menu.slots.get(i);
				if (!slot.getItem().isEmpty() && InvItems.id(slot.getItem().getItem()).equals(want)) {
					box.outline(g, slot, JacConfig.accent());
				}
			}
		}
	}
}
