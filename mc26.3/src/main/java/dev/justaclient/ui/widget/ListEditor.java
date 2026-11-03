package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.ListSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A list of names as chips with an x to remove each one, and a box under them to add another (Enter adds). */
public final class ListEditor extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float CHIP_H = 13f;
	private static final float GAP = 3f;
	private static final float BOX_H = 15f;
	private static final float TEXT = Theme.SMALL + 0.5f;

	private record Chip(String item, float x, float y, float w) {
	}

	private final ListSetting setting;
	private final List<Chip> chips = new ArrayList<>();
	private String draft = "";
	private boolean editing;
	private float boxY;

	public ListEditor(float x, float y, float width, ListSetting setting) {
		super(x, y, width, LABEL_GAP + BOX_H);
		this.setting = setting;
	}

	/** Flows the chips into rows; the widget grows with the list. */
	private void layout() {
		chips.clear();
		float cx = 0;
		float cy = LABEL_GAP;
		for (String item : setting.get()) {
			float w = Math.min(width, JacText.width(item, TEXT, JacText.Weight.REGULAR) + 18f);
			if (cx > 0 && cx + w > width) {
				cx = 0;
				cy += CHIP_H + GAP;
			}
			chips.add(new Chip(item, cx, cy, w));
			cx += w + GAP;
		}
		boxY = chips.isEmpty() ? LABEL_GAP : cy + CHIP_H + GAP + 1f;
		height = boxY + BOX_H;
	}

	@Override
	public float height() {
		layout();
		return height;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		layout();
		String count = setting.get().isEmpty() ? "" : "  " + setting.get().size();
		JacText.draw(g, setting.name + count, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		for (Chip chip : chips) {
			float cx = x + chip.x;
			float cy = y + chip.y;
			boolean hot = mouseX >= cx && mouseX < cx + chip.w && mouseY >= cy && mouseY < cy + CHIP_H;
			Draw.rect(g, cx, cy, chip.w, CHIP_H, CHIP_H / 2f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, alpha));
			String shown = chip.item;
			while (shown.length() > 1 && JacText.width(shown, TEXT, JacText.Weight.REGULAR) > chip.w - 18f) {
				shown = shown.substring(0, shown.length() - 1);
			}
			JacText.draw(g, shown, cx + 6f, cy + (CHIP_H - JacText.lineHeight(TEXT)) / 2f, Colors.fade(Theme.TEXT, alpha), TEXT, JacText.Weight.REGULAR);
			JacText.icon(g, Icons.X, cx + chip.w - 10f, cy + 3.5f, 6f, Colors.fade(hot ? 0xFFF87171 : Theme.TEXT_DIM, alpha));
		}
		float by = y + boxY;
		boolean full = setting.get().size() >= setting.maxItems;
		int fill = Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, editing ? 1f : mouseY >= by ? hover.get() : 0f);
		Draw.rect(g, x, by, width, BOX_H, radius, Colors.fade(fill, alpha));
		if (editing) {
			Draw.outline(g, x, by, width, BOX_H, radius, 1f, Colors.fade(JacConfig.accent(), alpha));
		}
		boolean empty = draft.isEmpty();
		String shown = empty && !editing ? full ? "List is full" : setting.placeholder : draft;
		float max = width - 22f;
		while (shown.length() > 1 && JacText.width(shown, Theme.LABEL, JacText.Weight.REGULAR) > max) {
			shown = shown.substring(1);
		}
		float ty = by + (BOX_H - JacText.lineHeight(Theme.LABEL)) / 2f;
		JacText.icon(g, Icons.PLUS, x + 5f, by + 4f, 7f, Colors.fade(editing ? JacConfig.accent() : Theme.TEXT_DIM, alpha));
		JacText.draw(g, shown, x + 16f, ty, Colors.fade(empty ? Theme.TEXT_DIM : Theme.TEXT, alpha), Theme.LABEL, JacText.Weight.REGULAR);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			float cx = x + 16f + (empty ? 0f : JacText.width(shown, Theme.LABEL, JacText.Weight.REGULAR)) + 0.5f;
			Draw.rect(g, cx, by + 3.5f, 1f, BOX_H - 7f, 0f, Colors.fade(JacConfig.accent(), alpha));
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		if (!isMouseOver(mouseX, mouseY)) {
			commit();
			editing = false;
			return false;
		}
		for (Chip chip : chips) {
			float cx = x + chip.x;
			float cy = y + chip.y;
			if (mouseX >= cx && mouseX < cx + chip.w && mouseY >= cy && mouseY < cy + CHIP_H) {
				if (button == 0) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					setting.remove(chip.item);
				}
				return true;
			}
		}
		boolean inBox = mouseY >= y + boxY;
		editing = inBox && button == 0 && setting.get().size() < setting.maxItems;
		return true;
	}

	private void commit() {
		if (!draft.isBlank()) {
			setting.add(draft);
		}
		draft = "";
	}

	@Override
	public boolean capturesKeys() {
		return editing;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!editing) {
			return false;
		}
		switch (key) {
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				commit();
				editing = setting.get().size() < setting.maxItems;
			}
			case InputConstants.KEY_ESCAPE -> {
				draft = "";
				editing = false;
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (!draft.isEmpty()) {
					draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
				} else if (!setting.get().isEmpty()) {
					// Backspace in an empty box takes the last chip back into the box to edit it.
					String last = setting.get().getLast();
					setting.remove(last);
					draft = last;
				}
			}
			case InputConstants.KEY_V -> {
				boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)
					|| InputConstants.isKeyDown(InputConstants.KEY_LGUI);
				if (ctrl) {
					// Pasting "a, b, c" adds each name.
					String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", ",");
					String[] parts = (draft + clip).split(",");
					for (int i = 0; i < parts.length - 1; i++) {
						setting.add(parts[i]);
					}
					draft = parts.length == 0 ? "" : parts[parts.length - 1].stripLeading();
				}
			}
			default -> {
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (!editing) {
			return false;
		}
		if (codepoint == ',') {
			commit();
		} else if (draft.length() < setting.maxLength) {
			draft += Character.toString(codepoint);
		}
		return true;
	}
}
