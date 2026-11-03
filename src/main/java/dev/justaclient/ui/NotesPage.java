package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.Notes;
import dev.justaclient.common.TextBuffer;
import dev.justaclient.config.JacConfig;
import dev.justaclient.notes.NotesStore;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Notes page of the click GUI: one notepad on the left, a todo list on the right. A todo's pin puts it on the
 * Pinned todos HUD widget. Click a box to type in it; typing elsewhere still searches the menu.
 */
final class NotesPage {
	private static final float LINE = 9.5f;
	private static final float ROW = 15f;
	private static final int NONE = 0;
	private static final int PAD_FOCUS = 1;
	private static final int TODO_FOCUS = 2;
	private static int focus = NONE;
	private static TextBuffer pad;
	private static final TextBuffer newTodo = new TextBuffer("", 80);
	private static float padX;
	private static float padY;
	private static float padW;
	private static float padH;
	private static float listX;
	private static float listY;
	private static float listW;
	private static float padScroll;
	private static List<TextBuffer.Line> lastLines = List.of();

	private NotesPage() {
	}

	private static TextBuffer pad() {
		if (pad == null) {
			pad = new TextBuffer(NotesStore.get().notepad, 20_000);
		}
		return pad;
	}

	static boolean focused() {
		return focus != NONE;
	}

	static void leave() {
		if (focus == PAD_FOCUS) {
			NotesStore.get().notepad = pad().get();
			NotesStore.save();
		}
		focus = NONE;
	}

	static void draw(GuiGraphics g, float x, float y, float w, float h, float a, double mx, double my) {
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		int accent = JacConfig.accent();
		JacText.draw(g, "Notes", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String hint = "Saved in config/justaclient/notes.json";
		JacText.draw(g, hint, x + w - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		boolean blink = System.currentTimeMillis() / 500 % 2 == 0;

		// Notepad.
		padX = x;
		padY = y + 22;
		padW = w * 0.56f;
		padH = h - 30;
		Draw.rect(g, padX, padY, padW, padH, radius, Colors.fade(focus == PAD_FOCUS ? 0x16FFFFFF : 0x0CFFFFFF, a));
		if (focus == PAD_FOCUS) {
			Draw.outline(g, padX, padY, padW, padH, radius, 0.75f, Colors.fade(Colors.withAlpha(accent, 0x90), a));
		}
		TextBuffer text = pad();
		lastLines = text.wrap(padW - 16, s -> JacText.width(s, Theme.LABEL, JacText.Weight.REGULAR));
		int caretLine = caretLine(lastLines, text.caret());
		float visible = padH - 14;
		// Keep the caret line in view.
		float caretTop = caretLine * LINE;
		if (caretTop - padScroll > visible - LINE) {
			padScroll = caretTop - visible + LINE;
		} else if (caretTop < padScroll) {
			padScroll = caretTop;
		}
		g.enableScissor((int) padX, (int) padY + 4, (int) (padX + padW), (int) (padY + padH - 4));
		if (text.get().isEmpty() && focus != PAD_FOCUS) {
			JacText.draw(g, "Click to write. Coordinates, plans, anything.", padX + 8, padY + 7, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.LABEL,
				JacText.Weight.REGULAR);
		}
		for (int i = 0; i < lastLines.size(); i++) {
			float ly = padY + 7 + i * LINE - padScroll;
			if (ly < padY - LINE || ly > padY + padH) {
				continue;
			}
			TextBuffer.Line line = lastLines.get(i);
			JacText.draw(g, line.text(), padX + 8, ly, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (focus == PAD_FOCUS && i == caretLine && blink) {
				String before = line.text().substring(0, Math.min(line.text().length(), text.caret() - line.start()));
				float cx = padX + 8 + JacText.width(before, Theme.LABEL, JacText.Weight.REGULAR);
				Draw.rect(g, cx, ly - 0.5f, 0.75f, LINE - 1f, 0, Colors.fade(Theme.TEXT, a));
			}
		}
		g.disableScissor();

		// Todo list.
		listX = x + padW + 10;
		listY = padY;
		listW = w - padW - 10;
		JacText.draw(g, "Todo", listX, listY, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		float iy = listY + 11;
		Draw.rect(g, listX, iy, listW, 15, Math.min(5f, radius), Colors.fade(focus == TODO_FOCUS ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
		JacText.icon(g, Icons.PLUS, listX + 5, iy + 4, 6.5f, Colors.fade(accent, a));
		String typed = newTodo.get();
		if (typed.isEmpty() && focus != TODO_FOCUS) {
			JacText.draw(g, "Add a todo", listX + 15, iy + 4, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.LABEL, JacText.Weight.REGULAR);
		} else {
			JacText.draw(g, typed, listX + 15, iy + 4, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (focus == TODO_FOCUS && blink) {
				float cx = listX + 15 + JacText.width(typed.substring(0, newTodo.caret()), Theme.LABEL, JacText.Weight.REGULAR);
				Draw.rect(g, cx, iy + 3.5f, 0.75f, 8, 0, Colors.fade(Theme.TEXT, a));
			}
		}
		List<Notes.Todo> todos = NotesStore.get().todos;
		float ty = iy + 20;
		for (Notes.Todo t : todos) {
			if (ty > padY + padH - ROW) {
				break;
			}
			boolean hover = mx >= listX && mx < listX + listW && my >= ty && my < ty + ROW;
			if (hover) {
				Draw.rect(g, listX, ty, listW, ROW - 1, 4f, Colors.fade(0x0CFFFFFF, a));
			}
			// Checkbox.
			Draw.rect(g, listX + 4, ty + 3.5f, 8, 8, 2f, Colors.fade(t.done ? accent : Theme.CONTROL_HOVER, a));
			if (t.done) {
				JacText.icon(g, Icons.CHECK, listX + 5, ty + 4.5f, 6f, Colors.fade(0xFFFFFFFF, a));
			}
			String label = fit(t.text, listW - 46);
			JacText.draw(g, label, listX + 16, ty + 4, Colors.fade(t.done ? Theme.TEXT_DIM : Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (t.done) {
				Draw.rect(g, listX + 16, ty + 7.5f, JacText.width(label, Theme.LABEL, JacText.Weight.REGULAR), 0.6f, 0, Colors.fade(Theme.TEXT_DIM, a));
			}
			// Pin and delete.
			int pinColor = t.pinned ? accent : hover ? Theme.TEXT_DIM : 0;
			if (pinColor != 0) {
				JacText.icon(g, Icons.MAP_PIN, listX + listW - 26, ty + 4, 6.5f, Colors.fade(pinColor, a));
			}
			if (hover) {
				JacText.icon(g, Icons.X, listX + listW - 12, ty + 4, 6.5f, Colors.fade(Theme.TEXT_DIM, a));
			}
			ty += ROW;
		}
		if (todos.isEmpty()) {
			JacText.draw(g, "Pin a todo to see it on your HUD.", listX + 4, ty + 2, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL,
				JacText.Weight.REGULAR);
		}
	}

	private static int caretLine(List<TextBuffer.Line> lines, int caret) {
		int line = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).start() <= caret) {
				line = i;
			}
		}
		return line;
	}

	private static String fit(String s, float max) {
		if (JacText.width(s, Theme.LABEL, JacText.Weight.REGULAR) <= max) {
			return s;
		}
		while (s.length() > 1 && JacText.width(s + "...", Theme.LABEL, JacText.Weight.REGULAR) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	/** Returns true if the click landed on the page. {@code button} is 0 for left. */
	static boolean click(double mx, double my, int button) {
		int before = focus;
		if (mx >= padX && mx < padX + padW && my >= padY && my < padY + padH) {
			focus = PAD_FOCUS;
			placeCaret(mx, my);
			return true;
		}
		if (before == PAD_FOCUS) {
			leave();
		}
		focus = NONE;
		float iy = listY + 11;
		if (mx >= listX && mx < listX + listW && my >= iy && my < iy + 15) {
			focus = TODO_FOCUS;
			return true;
		}
		List<Notes.Todo> todos = NotesStore.get().todos;
		float ty = iy + 20;
		for (int i = 0; i < todos.size(); i++) {
			if (mx >= listX && mx < listX + listW && my >= ty && my < ty + ROW) {
				Notes.Todo t = todos.get(i);
				if (mx >= listX + listW - 15) {
					todos.remove(i);
				} else if (mx >= listX + listW - 29) {
					t.pinned = !t.pinned;
				} else {
					t.done = !t.done;
				}
				NotesStore.save();
				return true;
			}
			ty += ROW;
		}
		return false;
	}

	private static void placeCaret(double mx, double my) {
		int line = (int) Math.floor((my - padY - 7 + padScroll) / LINE);
		if (lastLines.isEmpty()) {
			return;
		}
		line = Math.max(0, Math.min(lastLines.size() - 1, line));
		TextBuffer.Line l = lastLines.get(line);
		int index = l.text().length();
		for (int i = 0; i <= l.text().length(); i++) {
			if (padX + 8 + JacText.width(l.text().substring(0, i), Theme.LABEL, JacText.Weight.REGULAR) > mx) {
				index = Math.max(0, i - 1);
				break;
			}
		}
		pad().setCaret(l.start() + index);
	}

	static boolean keyPressed(int key, boolean ctrl) {
		if (focus == NONE) {
			return false;
		}
		TextBuffer b = focus == PAD_FOCUS ? pad() : newTodo;
		switch (key) {
			case InputConstants.KEY_ESCAPE -> leave();
			case InputConstants.KEY_BACKSPACE -> b.backspace(ctrl);
			case InputConstants.KEY_DELETE -> b.delete();
			case InputConstants.KEY_LEFT -> b.left(ctrl);
			case InputConstants.KEY_RIGHT -> b.right(ctrl);
			case InputConstants.KEY_HOME -> b.home();
			case InputConstants.KEY_END -> b.end();
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (focus == PAD_FOCUS) {
					b.insert("\n");
				} else if (!newTodo.get().isBlank()) {
					NotesStore.get().todos.add(new Notes.Todo(newTodo.get().trim()));
					newTodo.set("");
					NotesStore.save();
				}
			}
			default -> {
				if (ctrl && key == InputConstants.KEY_V) {
					String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "");
					b.insert(focus == PAD_FOCUS ? clip : clip.replace('\n', ' '));
				}
			}
		}
		saveSoon();
		return true;
	}

	static boolean charTyped(String s) {
		if (focus == NONE) {
			return false;
		}
		(focus == PAD_FOCUS ? pad() : newTodo).insert(s);
		saveSoon();
		return true;
	}

	private static long lastSave;

	/** Saves the notepad at most once a second while typing, and always when the page is left. */
	private static void saveSoon() {
		long now = System.currentTimeMillis();
		if (focus == PAD_FOCUS && now - lastSave > 1000) {
			lastSave = now;
			NotesStore.get().notepad = pad().get();
			NotesStore.save();
		}
	}
}
