package dev.justaclient.hud.widgets;

import dev.justaclient.common.Notes;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.notes.NotesStore;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Todos pinned on the Notes page, on your HUD. */
public final class TodoHud extends HudFeature {
	private final List<Notes.Todo> shown = new ArrayList<>();
	private final NumberSetting maxWidth = number("max_width", "Widest", 200, 80, 320, 4, NumberSetting.unit(" px"));

	public TodoHud() {
		super("todos", "Pinned todos", "Todos you pin on the Notes page", Icons.CHECK, true, 0f, 0.9f);
	}

	@Override
	public void layout(boolean preview) {
		shown.clear();
		if (preview) {
			Notes.Todo a = new Notes.Todo("Build the iron farm");
			Notes.Todo b = new Notes.Todo("Find a mending villager");
			b.done = true;
			shown.add(a);
			shown.add(b);
		} else {
			shown.addAll(NotesStore.pinned());
		}
		float w = 60f;
		for (Notes.Todo t : shown) {
			w = Math.max(w, JacText.width(t.text, TEXT, JacText.Weight.REGULAR) + 12f);
		}
		width = PAD * 2 + Math.min(w, maxWidth.f());
		height = PAD * 2 + shown.size() * ROW - (ROW - JacText.lineHeight(TEXT));
	}

	@Override
	public NumberSetting freeWidth() {
		return maxWidth;
	}

	private static String fit(String text, float max) {
		if (JacText.width(text, TEXT, JacText.Weight.REGULAR) <= max) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && JacText.width(cut + "…", TEXT, JacText.Weight.REGULAR) > max) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "…";
	}

	@Override
	public boolean hasContent() {
		return !shown.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		panel(g, width, height);
		float y = PAD;
		for (Notes.Todo t : shown) {
			Draw.rect(g, PAD, y + 0.5f, 6f, 6f, 1.5f, t.done ? JacConfig.accent() : Theme.CONTROL_HOVER);
			text(g, fit(t.text, width - PAD * 2 - 10f), PAD + 10f, y, t.done ? Theme.TEXT_DIM : Theme.TEXT, TEXT, JacText.Weight.REGULAR);
			y += ROW;
		}
	}
}
