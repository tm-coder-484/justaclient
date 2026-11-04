package dev.justaclient.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Editable text with a caret, for the notepad and other multi-line fields. Plain Java, shared by both builds. */
public final class TextBuffer {
	private final StringBuilder text = new StringBuilder();
	private final int maxLength;
	private int caret;

	public TextBuffer(String initial, int maxLength) {
		this.maxLength = maxLength;
		set(initial);
	}

	public String get() {
		return text.toString();
	}

	public void set(String value) {
		text.setLength(0);
		text.append(value.length() > maxLength ? value.substring(0, maxLength) : value);
		caret = text.length();
	}

	public int caret() {
		return caret;
	}

	public void setCaret(int index) {
		caret = Math.max(0, Math.min(text.length(), index));
	}

	public boolean insert(String s) {
		if (text.length() + s.length() > maxLength) {
			return false;
		}
		text.insert(caret, s);
		caret += s.length();
		return true;
	}

	public void backspace(boolean word) {
		if (caret == 0) {
			return;
		}
		int from = word ? wordStart(caret) : text.offsetByCodePoints(caret, -1);
		text.delete(from, caret);
		caret = from;
	}

	public void delete() {
		if (caret < text.length()) {
			text.delete(caret, text.offsetByCodePoints(caret, 1));
		}
	}

	public void left(boolean word) {
		caret = caret == 0 ? 0 : word ? wordStart(caret) : text.offsetByCodePoints(caret, -1);
	}

	public void right(boolean word) {
		if (caret >= text.length()) {
			return;
		}
		if (!word) {
			caret = text.offsetByCodePoints(caret, 1);
			return;
		}
		int i = caret;
		while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
			i++;
		}
		caret = i;
	}

	public void home() {
		caret = text.lastIndexOf("\n", caret - 1) + 1;
	}

	public void end() {
		int nl = text.indexOf("\n", caret);
		caret = nl < 0 ? text.length() : nl;
	}

	private int wordStart(int from) {
		int i = from;
		while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
			i--;
		}
		while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
			i--;
		}
		return i;
	}

	/** One wrapped line: where it starts in the text and what it shows. */
	public record Line(int start, String text) {
	}

	/** Wraps the text to {@code width}, breaking at spaces where it can, using {@code measure} for widths. */
	public List<Line> wrap(double width, ToDoubleFunction<String> measure) {
		List<Line> lines = new ArrayList<>();
		String all = text.toString();
		int start = 0;
		while (true) {
			int nl = all.indexOf('\n', start);
			int end = nl < 0 ? all.length() : nl;
			wrapParagraph(all, start, end, width, measure, lines);
			if (nl < 0) {
				break;
			}
			start = nl + 1;
		}
		return lines;
	}

	private static void wrapParagraph(String all, int start, int end, double width, ToDoubleFunction<String> measure, List<Line> out) {
		if (start == end) {
			out.add(new Line(start, ""));
			return;
		}
		int lineStart = start;
		while (lineStart < end) {
			int fit = lineStart;
			int lastSpace = -1;
			while (fit < end && measure.applyAsDouble(all.substring(lineStart, fit + 1)) <= width) {
				if (all.charAt(fit) == ' ') {
					lastSpace = fit;
				}
				fit++;
			}
			if (fit >= end) {
				out.add(new Line(lineStart, all.substring(lineStart, end)));
				return;
			}
			int cut = lastSpace > lineStart ? lastSpace + 1 : Math.max(fit, lineStart + 1);
			out.add(new Line(lineStart, all.substring(lineStart, cut)));
			lineStart = cut;
		}
	}
}
