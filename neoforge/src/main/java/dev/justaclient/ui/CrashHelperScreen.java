package dev.justaclient.ui;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.crash.CrashHelper;
import dev.justaclient.feedback.FeedbackSender;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import dev.justaclient.platform.input.KeyEvent;
import dev.justaclient.platform.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Shown once after a crash: what probably happened, what to try, and tools to report it. */
public final class CrashHelperScreen extends dev.justaclient.platform.PlatformScreen {
	private static final float W = 330f;
	private static final float PAD = 14f;
	private final @Nullable Screen parent;
	private final CrashHelper.Crash crash;
	private final Animation in = new Animation(Easing.CUBIC_OUT, 220, 0f);
	private final List<Button> buttons = new ArrayList<>();
	private String status = "";
	private boolean statusError;
	private float cardY;
	private float cardH;

	public CrashHelperScreen(@Nullable Screen parent, CrashHelper.Crash crash) {
		super(Component.literal("The game crashed"));
		this.parent = parent;
		this.crash = crash;
	}

	private final UiFit.Canvas canvas = new UiFit.Canvas();
	/** The card height the canvas was last fitted for. */
	private float fittedFor = 300f;

	@Override
	protected void init() {
		canvas.fit(this, W + 24f, fittedFor + 24f);
		buttons.clear();
		float bw = (W - PAD * 2 - 12) / 3f;
		buttons.add(new Button(0, 0, bw, 18, Icons.BOX, "Diagnostic zip", true, this::zip));
		buttons.add(new Button(0, 0, bw, 18, Icons.COPY, "Copy mod list", false, this::mods));
		buttons.add(new Button(0, 0, bw, 18, Icons.FLAG, "Report it", false, this::report));
		buttons.add(new Button(0, 0, bw, 18, Icons.EYE, "Open the report", false, () -> Util.getPlatform().openPath(crash.file())));
		buttons.add(new Button(0, 0, bw * 2 + 6, 18, "Continue", false, this::onClose));
	}

	private void zip() {
		try {
			Path zip = CrashHelper.buildZip(crash);
			status = "Saved " + zip.getFileName() + " in config/justaclient/diagnostics";
			statusError = false;
			Util.getPlatform().openPath(CrashHelper.DIAGNOSTICS);
		} catch (IOException e) {
			status = "Could not build the zip (" + e.getClass().getSimpleName() + ")";
			statusError = true;
		}
	}

	private void mods() {
		minecraft.keyboardHandler.setClipboard(FeedbackSender.modList());
		try {
			CrashHelper.exportModList();
			status = "Mod list copied, and saved in config/justaclient/diagnostics";
		} catch (IOException e) {
			status = "Mod list copied to the clipboard";
		}
		statusError = false;
	}

	private void report() {
		CrashHelper.markSeen(crash.file());
		String text = "The game crashed. " + crash.info().explanation() + "\n" + (crash.info().exception().isEmpty() ? "" : crash.info().exception()
			+ (crash.info().detail().isEmpty() ? "" : ": " + crash.info().detail()) + "\n") + "\nWhat I was doing: ";
		minecraft.setScreen(ClickGuiScreen.feedback(parent, text));
	}

	private List<String> wrap(String text, float size, float max) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && JacText.width(next, size, JacText.Weight.REGULAR) > max) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		boolean pushed = canvas.push(g);
		super.renderBackground(g, canvas.in(mouseX), canvas.in(mouseY), partialTick);
		canvas.pop(g, pushed);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (Math.abs(cardH - fittedFor) > 0.5f && cardH > 0) {
			// The card's height is known once it has been laid out: fit the canvas to it so a long report never runs off.
			fittedFor = cardH;
			canvas.fit(this, W + 24f, fittedFor + 24f);
		}
		boolean pushed = canvas.push(g);
		drawCard(g, canvas.in(mouseX), canvas.in(mouseY), partialTick);
		canvas.pop(g, pushed);
	}

	private void drawCard(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		float t = in.animateTo(1f).get();
		float inner = W - PAD * 2;
		List<String> explanation = wrap(crash.info().explanation(), Theme.LABEL + 1f, inner);
		List<String> advice = wrap(crash.info().advice(), Theme.LABEL, inner);
		String error = crash.info().exception().isEmpty() ? crash.info().description() : crash.info().exception().replaceAll("^.*\\.", "")
			+ (crash.info().detail().isEmpty() ? "" : ": " + crash.info().detail());
		List<String> errorLines = wrap(error, Theme.SMALL, inner - 12);
		if (errorLines.size() > 2) {
			errorLines = new ArrayList<>(errorLines.subList(0, 2));
			errorLines.set(1, errorLines.get(1) + "...");
		}
		List<String> suspects = crash.info().suspects().stream().map(CrashHelper::modName).toList();
		cardH = PAD + 34 + explanation.size() * 11f + 4 + advice.size() * 10f + 8 + (errorLines.isEmpty() ? 0 : errorLines.size() * 8.5f + 10)
			+ (suspects.isEmpty() ? 0 : 12 + suspects.size() * 9f) + 8 + 18 + 6 + 18 + 12 + PAD;
		float x = (width - W) / 2f;
		cardY = (height - cardH) / 2f + (1f - t) * 8f;
		float y = cardY;
		Draw.panel(g, x, y, W, cardH, t);
		int accent = JacConfig.accent();
		JacText.icon(g, Icons.INFO, x + PAD, y + PAD + 1, 10f, Colors.fade(0xFFF87171, t));
		JacText.draw(g, crash.info().nativeCrash() ? "Java crashed last time" : "The game crashed last time", x + PAD + 16, y + PAD,
			Colors.fade(Theme.TEXT, t), Theme.TITLE, JacText.Weight.SEMIBOLD);
		String when = new SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(new Date(crash.file().toFile().lastModified()));
		JacText.draw(g, when + "  ·  " + crash.file().getFileName(), x + PAD + 16, y + PAD + 13, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL,
			JacText.Weight.REGULAR);
		float ly = y + PAD + 34;
		for (String line : explanation) {
			JacText.draw(g, line, x + PAD, ly, Colors.fade(Theme.TEXT, t), Theme.LABEL + 1f, JacText.Weight.SEMIBOLD);
			ly += 11f;
		}
		ly += 4;
		for (String line : advice) {
			JacText.draw(g, line, x + PAD, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.LABEL, JacText.Weight.REGULAR);
			ly += 10f;
		}
		ly += 8;
		if (!errorLines.isEmpty()) {
			Draw.rect(g, x + PAD, ly - 3, inner, errorLines.size() * 8.5f + 6, 4f, Colors.fade(0x14FFFFFF, t));
			for (String line : errorLines) {
				JacText.draw(g, line, x + PAD + 6, ly, Colors.fade(0xFFFCA5A5, t), Theme.SMALL, JacText.Weight.REGULAR);
				ly += 8.5f;
			}
			ly += 10;
		}
		if (!suspects.isEmpty()) {
			JacText.draw(g, "Mods that show up in it", x + PAD, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
			ly += 11;
			for (String s : suspects) {
				Draw.circle(g, x + PAD + 3, ly + 3, 1.5f, Colors.fade(accent, t));
				JacText.draw(g, s, x + PAD + 9, ly, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.REGULAR);
				ly += 9f;
			}
			ly += 1;
		}
		ly += 8;
		float bw = (W - PAD * 2 - 12) / 3f;
		for (int i = 0; i < 3; i++) {
			buttons.get(i).setPosition(x + PAD + i * (bw + 6), ly);
		}
		buttons.get(3).setPosition(x + PAD, ly + 24);
		buttons.get(4).setPosition(x + PAD + bw + 6, ly + 24);
		for (Button b : buttons) {
			b.render(g, mouseX, mouseY, t);
		}
		if (!status.isEmpty()) {
			JacText.draw(g, status, x + PAD, ly + 48, Colors.fade(statusError ? 0xFFF87171 : 0xFF34D399, t), Theme.SMALL, JacText.Weight.REGULAR);
		}
		JacText.draw(g, "Nothing is sent anywhere unless you choose Report it.", x + PAD, y + cardH - PAD + 2, Colors.fade(Theme.TEXT_DIM, t * 0.7f),
			Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		event = canvas.in(event);
		for (Button b : buttons) {
			if (b.mouseClicked(event.x(), event.y(), event.button())) {
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		CrashHelper.markSeen(crash.file());
		minecraft.setScreen(parent);
	}
}
