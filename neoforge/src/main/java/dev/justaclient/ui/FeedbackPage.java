package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Colors;
import dev.justaclient.common.TextBuffer;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.FeedbackFeature;
import dev.justaclient.feedback.FeedbackSender;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Feedback page of the click GUI: pick bug, idea or other, write a message, choose what to attach and send it to
 * the feedback Worker. Nothing is sent until the Feedback feature is switched on and a server address is set.
 */
final class FeedbackPage {
	private static final String[] KINDS = {"bug", "idea", "other"};
	private static final String[] KIND_LABELS = {"Bug", "Idea", "Other"};
	private static final String[] ATTACH_LABELS = {"Latest log", "Crash report", "Mod list", "Settings"};
	private static final float LINE = 9.5f;
	private static final int NONE = 0;
	private static final int MESSAGE = 1;
	private static final int SERVER = 2;
	private static int focus = NONE;
	private static int kind;
	private static final TextBuffer message = new TextBuffer("", 4000);
	private static final TextBuffer server = new TextBuffer("", 120);
	private static boolean[] attach;
	private static boolean sending;
	private static String status = "";
	private static boolean statusError;
	private static List<FeedbackSender.Sent> sent;
	// Hit boxes from the last draw.
	private static float boxX;
	private static float boxY;
	private static float boxW;
	private static float boxH;
	private static float kindsY;
	private static final float[] kindX = new float[3];
	private static final float[] kindW = new float[3];
	private static float attachY;
	private static final float[] attachX = new float[4];
	private static final float[] attachW = new float[4];
	private static float sendX;
	private static float sendY;
	private static float sendW;
	private static float onX;
	private static float onY;
	private static float onW;
	private static float serverX;
	private static float serverY;
	private static float serverW;
	private static int mode;
	private static final int MODE_OFF = 0;
	private static final int MODE_NO_SERVER = 1;
	private static final int MODE_FORM = 2;

	private FeedbackPage() {
	}

	private static FeedbackFeature feature() {
		return Features.get(FeedbackFeature.class);
	}

	private static boolean[] attach() {
		if (attach == null) {
			FeedbackFeature f = feature();
			attach = new boolean[] {f.attachLog.on(), f.attachCrash.on(), f.attachMods.on(), f.attachSettings.on()};
		}
		return attach;
	}

	/** Fills the form as a bug report with the crash report attached. */
	static void prefill(String text) {
		kind = 0;
		message.set(text);
		attach = null;
		boolean[] at = attach();
		at[1] = true;
		focus = MESSAGE;
		status = "";
	}

	static boolean focused() {
		return focus != NONE;
	}

	static void leave() {
		focus = NONE;
		sent = null;
	}

	static void draw(GuiGraphics g, float x, float y, float w, float h, float a, double mx, double my) {
		FeedbackFeature f = feature();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		int accent = JacConfig.accent();
		JacText.draw(g, "Feedback", x, y, Colors.fade(Theme.TEXT, a), Theme.TITLE - 1.5f, JacText.Weight.SEMIBOLD);
		String address = f.server.get().trim();
		mode = !f.isEnabled() ? MODE_OFF : address.isEmpty() ? MODE_NO_SERVER : MODE_FORM;
		String hint = mode == MODE_FORM ? "Sends to " + host(address) + (f.anonymous.on() ? ", anonymously" : "") : "Off: nothing is sent";
		JacText.draw(g, hint, x + w - JacText.width(hint, Theme.SMALL, JacText.Weight.REGULAR), y + 2, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL,
			JacText.Weight.REGULAR);
		boolean blink = System.currentTimeMillis() / 500 % 2 == 0;
		float top = y + 22;

		if (mode != MODE_FORM) {
			Draw.rect(g, x, top, w, 70, radius, Colors.fade(0x0CFFFFFF, a));
			JacText.icon(g, Icons.FLAG, x + 10, top + 10, 9f, Colors.fade(accent, a));
			String head = mode == MODE_OFF ? "Send bug reports and ideas from here" : "Where should feedback go?";
			JacText.draw(g, head, x + 26, top + 10, Colors.fade(Theme.TEXT, a), Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
			String body = mode == MODE_OFF ? "Reports go to your own Cloudflare Worker (see cloudflare/README.md). Off until you turn it on."
				: "Paste the address deploy.bat printed, then press Enter.";
			JacText.draw(g, body, x + 26, top + 24, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			if (mode == MODE_OFF) {
				onX = x + 26;
				onY = top + 42;
				onW = JacText.width("Turn on", Theme.LABEL, JacText.Weight.SEMIBOLD) + 20;
				boolean hover = inside(mx, my, onX, onY, onW, 17);
				Draw.rect(g, onX, onY, onW, 17, Math.min(5f, radius), Colors.fade(hover ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent, a));
				JacText.drawCentered(g, "Turn on", onX + onW / 2f, onY + 5, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
			} else {
				serverX = x + 26;
				serverY = top + 42;
				serverW = w - 36;
				Draw.rect(g, serverX, serverY, serverW, 17, Math.min(5f, radius), Colors.fade(focus == SERVER ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
				String typed = server.get();
				if (typed.isEmpty() && focus != SERVER) {
					JacText.draw(g, "https://justaclient.<you>.workers.dev", serverX + 6, serverY + 5, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.LABEL,
						JacText.Weight.REGULAR);
				} else {
					JacText.draw(g, typed, serverX + 6, serverY + 5, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
					if (focus == SERVER && blink) {
						float cx = serverX + 6 + JacText.width(typed.substring(0, server.caret()), Theme.LABEL, JacText.Weight.REGULAR);
						Draw.rect(g, cx, serverY + 4.5f, 0.75f, 8, 0, Colors.fade(Theme.TEXT, a));
					}
				}
			}
			drawStatus(g, x, top + 80, a);
			return;
		}

		float formW = w * 0.62f;
		// Kind chips.
		kindsY = top;
		float cx = x;
		for (int i = 0; i < KINDS.length; i++) {
			float cw = JacText.width(KIND_LABELS[i], Theme.LABEL, JacText.Weight.SEMIBOLD) + 18;
			kindX[i] = cx;
			kindW[i] = cw;
			boolean on = kind == i;
			boolean hover = inside(mx, my, cx, kindsY, cw, 15);
			Draw.rect(g, cx, kindsY, cw, 15, 7.5f, Colors.fade(on ? accent : hover ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			JacText.drawCentered(g, KIND_LABELS[i], cx + cw / 2f, kindsY + 4, Colors.fade(on ? 0xFFFFFFFF : Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			cx += cw + 5;
		}

		// Message box.
		boxX = x;
		boxY = top + 21;
		boxW = formW;
		boxH = Math.max(40, h - 86);
		Draw.rect(g, boxX, boxY, boxW, boxH, radius, Colors.fade(focus == MESSAGE ? 0x16FFFFFF : 0x0CFFFFFF, a));
		if (focus == MESSAGE) {
			Draw.outline(g, boxX, boxY, boxW, boxH, radius, 0.75f, Colors.fade(Colors.withAlpha(accent, 0x90), a));
		}
		List<TextBuffer.Line> lines = message.wrap(boxW - 16, s -> JacText.width(s, Theme.LABEL, JacText.Weight.REGULAR));
		int caretLine = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).start() <= message.caret()) {
				caretLine = i;
			}
		}
		int visibleLines = (int) ((boxH - 14) / LINE);
		int first = Math.max(0, caretLine - visibleLines + 1);
		if (message.get().isEmpty() && focus != MESSAGE) {
			String placeholder = kind == 0 ? "What happened, and what did you expect?" : kind == 1 ? "What would you like to see?" : "Anything else on your mind";
			JacText.draw(g, placeholder, boxX + 8, boxY + 7, Colors.fade(Theme.TEXT_DIM, a * 0.8f), Theme.LABEL, JacText.Weight.REGULAR);
		}
		for (int i = first; i < lines.size() && i < first + visibleLines; i++) {
			float ly = boxY + 7 + (i - first) * LINE;
			TextBuffer.Line line = lines.get(i);
			JacText.draw(g, line.text(), boxX + 8, ly, Colors.fade(Theme.TEXT, a), Theme.LABEL, JacText.Weight.REGULAR);
			if (focus == MESSAGE && i == caretLine && blink) {
				String before = line.text().substring(0, Math.min(line.text().length(), message.caret() - line.start()));
				Draw.rect(g, boxX + 8 + JacText.width(before, Theme.LABEL, JacText.Weight.REGULAR), ly - 0.5f, 0.75f, LINE - 1f, 0, Colors.fade(Theme.TEXT, a));
			}
		}
		String count = message.get().length() + " / 4000";
		JacText.draw(g, count, boxX + boxW - 6 - JacText.width(count, Theme.SMALL - 0.5f, JacText.Weight.REGULAR), boxY + boxH - 9,
			Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);

		// Attachments.
		attachY = boxY + boxH + 7;
		boolean[] at = attach();
		float ax = x;
		for (int i = 0; i < ATTACH_LABELS.length; i++) {
			float aw = 13 + JacText.width(ATTACH_LABELS[i], Theme.SMALL, JacText.Weight.REGULAR) + 10;
			attachX[i] = ax;
			attachW[i] = aw;
			Draw.rect(g, ax, attachY + 1, 8, 8, 2f, Colors.fade(at[i] ? accent : Theme.CONTROL_HOVER, a));
			if (at[i]) {
				JacText.icon(g, Icons.CHECK, ax + 1, attachY + 2, 6f, Colors.fade(0xFFFFFFFF, a));
			}
			JacText.draw(g, ATTACH_LABELS[i], ax + 12, attachY + 2, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
			ax += aw;
		}

		// Send.
		sendY = attachY + 15;
		String label = sending ? "Sending..." : "Send";
		sendW = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 24;
		sendX = x;
		boolean ready = !sending && !message.get().isBlank();
		boolean hoverSend = ready && inside(mx, my, sendX, sendY, sendW, 17);
		int sendColor = ready ? hoverSend ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent : Theme.CONTROL;
		Draw.rect(g, sendX, sendY, sendW, 17, Math.min(5f, radius), Colors.fade(sendColor, a));
		JacText.drawCentered(g, label, sendX + sendW / 2f, sendY + 5, Colors.fade(ready ? 0xFFFFFFFF : Theme.TEXT_DIM, a), Theme.LABEL,
			JacText.Weight.SEMIBOLD);
		drawStatus(g, sendX + sendW + 8, sendY + 5, a);

		// Sent list.
		float lx = x + formW + 12;
		float lw = w - formW - 12;
		JacText.draw(g, "Sent", lx, top + 3, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
		if (sent == null) {
			sent = FeedbackSender.sent();
		}
		float sy = top + 16;
		if (sent.isEmpty()) {
			JacText.draw(g, "Reports you send show up here.", lx, sy, Colors.fade(Theme.TEXT_DIM, a * 0.7f), Theme.SMALL, JacText.Weight.REGULAR);
		}
		for (FeedbackSender.Sent s : sent) {
			if (sy > y + h - 26) {
				break;
			}
			Draw.rect(g, lx, sy, lw, 24, 4f, Colors.fade(0x0CFFFFFF, a));
			String kindLabel = s.kind().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.kind().substring(1);
			JacText.draw(g, kindLabel + "  ·  " + s.created().substring(0, Math.min(10, s.created().length())) + "  ·  #" + s.id().substring(0, Math.min(8, s.id().length())), lx + 6, sy + 4,
				Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
			JacText.draw(g, fit(s.summary(), lw - 12), lx + 6, sy + 13, Colors.fade(Theme.TEXT, a), Theme.SMALL, JacText.Weight.REGULAR);
			sy += 28;
		}
	}

	private static void drawStatus(GuiGraphics g, float x, float y, float a) {
		if (!status.isEmpty()) {
			JacText.draw(g, status, x, y, Colors.fade(statusError ? 0xFFF87171 : 0xFF34D399, a), Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	private static String host(String address) {
		return address.replaceFirst("^https?://", "").replaceAll("/.*$", "");
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static String fit(String s, float max) {
		if (JacText.width(s, Theme.SMALL, JacText.Weight.REGULAR) <= max) {
			return s;
		}
		while (s.length() > 1 && JacText.width(s + "...", Theme.SMALL, JacText.Weight.REGULAR) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	static boolean click(double mx, double my, int button) {
		focus = NONE;
		if (mode == MODE_OFF) {
			if (inside(mx, my, onX, onY, onW, 17)) {
				feature().setEnabled(true);
				status = "";
				return true;
			}
			return false;
		}
		if (mode == MODE_NO_SERVER) {
			if (inside(mx, my, serverX, serverY, serverW, 17)) {
				focus = SERVER;
				return true;
			}
			return false;
		}
		for (int i = 0; i < KINDS.length; i++) {
			if (inside(mx, my, kindX[i], kindsY, kindW[i], 15)) {
				kind = i;
				return true;
			}
		}
		if (inside(mx, my, boxX, boxY, boxW, boxH)) {
			focus = MESSAGE;
			return true;
		}
		for (int i = 0; i < ATTACH_LABELS.length; i++) {
			if (inside(mx, my, attachX[i], attachY, attachW[i], 11)) {
				attach()[i] = !attach()[i];
				return true;
			}
		}
		if (inside(mx, my, sendX, sendY, sendW, 17)) {
			send();
			return true;
		}
		return false;
	}

	private static void send() {
		if (sending || message.get().isBlank()) {
			return;
		}
		sending = true;
		status = "";
		boolean[] at = attach();
		FeedbackSender.send(KINDS[kind], message.get().strip(), new FeedbackSender.Attach(at[0], at[1], at[2], at[3]), error -> {
			sending = false;
			if (error == null) {
				status = "Sent. Thanks!";
				statusError = false;
				message.set("");
				sent = null;
			} else {
				status = error;
				statusError = true;
			}
		});
	}

	static boolean keyPressed(int key, boolean ctrl) {
		if (focus == NONE) {
			return false;
		}
		TextBuffer b = focus == MESSAGE ? message : server;
		switch (key) {
			case InputConstants.KEY_ESCAPE -> focus = NONE;
			case InputConstants.KEY_BACKSPACE -> b.backspace(ctrl);
			case InputConstants.KEY_DELETE -> b.delete();
			case InputConstants.KEY_LEFT -> b.left(ctrl);
			case InputConstants.KEY_RIGHT -> b.right(ctrl);
			case InputConstants.KEY_HOME -> b.home();
			case InputConstants.KEY_END -> b.end();
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (focus == SERVER) {
					String address = server.get().trim();
					if (address.startsWith("https://") || address.startsWith("http://")) {
						feature().server.set(address);
						dev.justaclient.profile.Profiles.save();
						focus = NONE;
						status = "";
					} else {
						status = "The address starts with https://";
						statusError = true;
					}
				} else if (ctrl) {
					send();
				} else {
					b.insert("\n");
				}
			}
			default -> {
				if (ctrl && key == InputConstants.KEY_V) {
					String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "");
					b.insert(focus == MESSAGE ? clip : clip.replace("\n", "").trim());
				}
			}
		}
		return true;
	}

	static boolean charTyped(String s) {
		if (focus == NONE) {
			return false;
		}
		(focus == MESSAGE ? message : server).insert(s);
		return true;
	}
}
