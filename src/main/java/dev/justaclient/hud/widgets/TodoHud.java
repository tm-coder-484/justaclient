package dev.justaclient.hud.widgets;

import dev.justaclient.common.Notes;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.notes.NotesStore;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Todos pinned on the Notes page, on your HUD. */
public final class TodoHud extends HudFeature {
	private final List<Notes.Todo> shown = new ArrayList<>();

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
			for (Notes.Todo t : NotesStore.get().todos) {
				if (t.pinned) {
					shown.add(t);
				}
			}
		}
		float w = 60f;
		for (Notes.Todo t : shown) {
			w = Math.max(w, JacText.width(t.text, TEXT, JacText.Weight.REGULAR) + 12f);
		}
		width = PAD * 2 + Math.min(w, 200f);
		height = PAD * 2 + shown.size() * ROW - (ROW - JacText.lineHeight(TEXT));
	}

	@Override
	public boolean hasContent() {
		return !shown.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float y = PAD;
		for (Notes.Todo t : shown) {
			Draw.rect(g, PAD, y + 0.5f, 6f, 6f, 1.5f, t.done ? JacConfig.accent() : Theme.CONTROL_HOVER);
			text(g, t.text, PAD + 10f, y, t.done ? Theme.TEXT_DIM : Theme.TEXT, TEXT, JacText.Weight.REGULAR);
			y += ROW;
		}
	}
}
