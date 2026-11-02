package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/** The list of features that are on, sliding in and out as they toggle. Lines up against whichever side it sits on. */
public final class ModuleListHud extends HudFeature {
	public enum Sort implements ChoiceSetting.Named {
		LENGTH("Longest first"), ALPHABET("A to Z");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final float LINE = 10f;
	private final ChoiceSetting<Sort> sort = choice("sort", "Order", Sort.LENGTH);
	private final BoolSetting gradient = bool("gradient", "Gradient text", true);
	private final BoolSetting sideLine = bool("side_line", "Side line", true);
	private final BoolSetting hudWidgets = bool("hud_widgets", "Include HUD widgets", false);
	private final Map<Feature, Animation> shown = new HashMap<>();
	private final List<Feature> rows = new ArrayList<>();
	private boolean rightAligned;

	public ModuleListHud() {
		super("module_list", "Module list", "The features you have on, as a sliding list", Icons.LAYERS, false, 1f, 0f);
		background.set(false);
	}

	@Override
	public void layout(boolean preview) {
		rows.clear();
		for (Feature f : Features.all()) {
			if (f == this || (!hudWidgets.on() && f instanceof HudFeature)) {
				continue;
			}
			boolean on = f.isEnabled() || (preview && rows.size() < 5 && !(f instanceof HudFeature));
			Animation a = shown.computeIfAbsent(f, k -> new Animation(Easing.CUBIC_OUT, 220, on ? 1f : 0f));
			a.animateTo(on ? 1f : 0f);
			if (on || a.get() > 0.01f) {
				rows.add(f);
			}
		}
		Comparator<Feature> order = sort.get() == Sort.ALPHABET
			? Comparator.comparing(f -> f.name)
			: Comparator.comparingDouble((Feature f) -> -JacText.width(f.name, TEXT, JacText.Weight.SEMIBOLD));
		rows.sort(order);
		float w = 40f;
		float h = 0f;
		for (Feature f : rows) {
			w = Math.max(w, JacText.width(f.name, TEXT, JacText.Weight.SEMIBOLD) + PAD * 2 + 3f);
			h += LINE * shown.get(f).get();
		}
		width = w;
		height = Math.max(LINE, h);
		rightAligned = relX > 0.5f;
	}

	@Override
	public boolean hasContent() {
		return !rows.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		float y = 0f;
		int n = Math.max(1, rows.size() - 1);
		for (int i = 0; i < rows.size(); i++) {
			Feature f = rows.get(i);
			float t = shown.get(f).get();
			float textW = JacText.width(f.name, TEXT, JacText.Weight.SEMIBOLD);
			float rowW = textW + PAD * 2;
			// Rows slide in from the edge they hang off.
			float slide = (1f - t) * (rowW + 6f);
			float x = rightAligned ? width - rowW + slide : -slide;
			int color = gradient.on() ? JacConfig.accentAt(i / (float) n) : 0xFFFFFFFF;
			if (background.on()) {
				Draw.rect(g, x, y, rowW, LINE * t, 0f, Colors.fade(JacConfig.surface(), t));
			}
			if (sideLine.on()) {
				Draw.rect(g, rightAligned ? width - 1.5f + slide : -slide, y, 1.5f, LINE * t, 0f, Colors.fade(color, t));
			}
			text(g, f.name, x + PAD - (rightAligned ? 1.5f : -1.5f), y + (LINE - JacText.lineHeight(TEXT)) / 2f, Colors.fade(color, t), TEXT,
				JacText.Weight.SEMIBOLD);
			y += LINE * t;
		}
	}
}
