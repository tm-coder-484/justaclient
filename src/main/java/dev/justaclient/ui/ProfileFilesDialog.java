package dev.justaclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.Notifications;
import dev.justaclient.profile.Profiles;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.widget.Button;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Export the active profile to a file, choosing which parts go in it, or import one from the exports, backups or
 * Downloads folder with a preview of what it changes first.
 */
public final class ProfileFilesDialog implements Overlay {
	private static final float PAD = 14f;
	private static final String[] PARTS = {"Features on or off", "Feature settings", "Toggle keys", "HUD layout", "Theme"};
	private static final boolean[] parts = {true, true, true, true, true};
	private static final float ITEM = 22f;
	private final boolean export;
	private final Runnable onClose;
	private final Runnable onImported;
	private final Animation in = new Animation(Easing.CUBIC_OUT, 160, 0f);
	private final List<Profiles.ImportFile> files;
	private int selected = -1;
	private int scroll;
	private Profiles.@Nullable Preview preview;
	private boolean useTheme = true;
	private @Nullable Button primary;
	private @Nullable Button cancel;
	private @Nullable Button folder;
	private float x;
	private float y;
	private float w;
	private float h;

	public ProfileFilesDialog(boolean export, Runnable onClose, Runnable onImported) {
		this.export = export;
		this.onClose = onClose;
		this.onImported = onImported;
		this.files = export ? List.of() : Profiles.importable();
		if (!files.isEmpty()) {
			pick(0);
		}
	}

	private void pick(int index) {
		selected = index;
		preview = Profiles.preview(files.get(index).path());
		useTheme = preview.theme();
	}

	@Override
	public void render(GuiGraphics g, int screenW, int screenH, double mx, double my) {
		float t = in.animateTo(1f).get();
		w = export ? 300f : Math.min(screenW - 20f, 360f);
		h = export ? PAD + 22f + 12f + PARTS.length * 14f + 10f + 18f + PAD : Math.min(screenH - 20f, 230f);
		x = (screenW - w) / 2f;
		y = (screenH - h) / 2f + (1f - t) * 6f;
		float by = y + h - PAD - 18f;
		if (primary == null) {
			cancel = new Button(0, 0, 70, 18, "Cancel", false, onClose);
			primary = export ? new Button(0, 0, 90, 18, Icons.UPLOAD, "Export", true, this::doExport)
				: new Button(0, 0, 130, 18, Icons.DOWNLOAD, "Import as new profile", true, this::doImport);
			folder = new Button(0, 0, 84, 18, Icons.LAYERS, "Open folder", false, () -> {
				try {
					Files.createDirectories(Profiles.EXPORTS);
				} catch (IOException ignored) {
					// The platform opener reports a missing folder itself.
				}
				Util.getPlatform().openPath(Profiles.EXPORTS);
			});
		}
		primary.setPosition(x + w - PAD - primary.width(), by);
		cancel.setPosition(primary.x() - 6 - cancel.width(), by);
		folder.setPosition(x + PAD, by);
		g.nextStratum();
		Draw.rect(g, 0, 0, screenW, screenH, 0, Colors.fade(0x80000000, t));
		Draw.panel(g, x, y, w, h, t);
		int accent = JacConfig.accent();
		JacText.icon(g, export ? Icons.UPLOAD : Icons.DOWNLOAD, x + PAD, y + PAD + 1f, 10f, Colors.fade(accent, t));
		JacText.draw(g, export ? "Export " + Profiles.active() : "Import a profile", x + PAD + 16f, y + PAD, Colors.fade(Theme.TEXT, t), Theme.TITLE,
			JacText.Weight.SEMIBOLD);
		if (export) {
			drawExport(g, mx, my, t, accent);
		} else {
			drawImport(g, mx, my, t, accent);
		}
		boolean ready = export ? partsChosen() : preview != null && preview.root() != null;
		cancel.render(g, mx, my, t);
		primary.render(g, ready ? mx : -1e4, ready ? my : -1e4, ready ? t : t * 0.45f);
		folder.render(g, mx, my, t);
	}

	private static boolean partsChosen() {
		for (boolean p : parts) {
			if (p) {
				return true;
			}
		}
		return false;
	}

	private void drawExport(GuiGraphics g, double mx, double my, float t, int accent) {
		JacText.draw(g, "Choose what goes in the file. It is saved in config/justaclient/exports.", x + PAD, y + PAD + 20f, Colors.fade(Theme.TEXT_DIM, t),
			Theme.SMALL, JacText.Weight.REGULAR);
		float cy = y + PAD + 34f;
		for (int i = 0; i < PARTS.length; i++) {
			checkbox(g, x + PAD, cy + i * 14f, PARTS[i], parts[i], t, accent, mx, my);
		}
	}

	private void checkbox(GuiGraphics g, float cx, float cy, String label, boolean on, float t, int accent, double mx, double my) {
		boolean hover = mx >= cx && mx < cx + 160 && my >= cy - 2 && my < cy + 11;
		Draw.rect(g, cx, cy, 9, 9, 2f, Colors.fade(on ? accent : hover ? Theme.CONTROL_HOVER : Theme.CONTROL, t));
		if (on) {
			JacText.icon(g, Icons.CHECK, cx + 1.5f, cy + 1.5f, 6f, Colors.fade(0xFFFFFFFF, t));
		}
		JacText.draw(g, label, cx + 14, cy + 1, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.REGULAR);
	}

	private void drawImport(GuiGraphics g, double mx, double my, float t, int accent) {
		float top = y + PAD + 20f;
		float bottom = y + h - PAD - 26f;
		float listW = w * 0.46f;
		if (files.isEmpty()) {
			JacText.draw(g, "No profile files found.", x + PAD, top + 4, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.REGULAR);
			JacText.draw(g, "Put a .json profile in config/justaclient/exports or your Downloads folder.", x + PAD, top + 17,
				Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
			return;
		}
		SimpleDateFormat when = new SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH);
		g.enableScissor((int) x, (int) top, (int) (x + PAD + listW), (int) bottom);
		int rows = (int) ((bottom - top) / ITEM);
		scroll = Math.max(0, Math.min(scroll, files.size() - rows));
		for (int i = scroll; i < files.size() && i < scroll + rows + 1; i++) {
			Profiles.ImportFile f = files.get(i);
			float ry = top + (i - scroll) * ITEM;
			boolean hover = mx >= x + PAD && mx < x + PAD + listW && my >= ry && my < ry + ITEM - 2 && my < bottom;
			Draw.rect(g, x + PAD, ry, listW, ITEM - 2, 4f, Colors.fade(i == selected ? Colors.withAlpha(accent, 0x40) : hover ? 0x14FFFFFF : 0x0AFFFFFF, t));
			String name = f.path().getFileName().toString().replace(".json", "");
			JacText.draw(g, fit(name, listW - 10, Theme.LABEL), x + PAD + 5, ry + 3, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.REGULAR);
			JacText.draw(g, f.source() + "  ·  " + when.format(new Date(f.path().toFile().lastModified())), x + PAD + 5, ry + 12,
				Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL - 0.5f, JacText.Weight.REGULAR);
		}
		g.disableScissor();

		// Preview.
		float px = x + PAD + listW + 10;
		float pw = w - PAD * 2 - listW - 10;
		Draw.rect(g, px, top, pw, bottom - top, 5f, Colors.fade(0x0AFFFFFF, t));
		Profiles.Preview p = preview;
		if (p == null) {
			return;
		}
		float ly = top + 6;
		if (p.root() == null) {
			JacText.draw(g, "This file is not a profile.", px + 6, ly, Colors.fade(0xFFF87171, t), Theme.LABEL, JacText.Weight.REGULAR);
			return;
		}
		JacText.draw(g, fit(p.name(), pw - 12, Theme.LABEL), px + 6, ly, Colors.fade(Theme.TEXT, t), Theme.LABEL, JacText.Weight.SEMIBOLD);
		ly += 12;
		String[] facts = {
			p.features() + " features in the file",
			p.toggled() + " switched on or off",
			p.settingsChanged() + " settings differ from yours",
		};
		for (String fact : facts) {
			JacText.draw(g, fact, px + 6, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
			ly += 8.5f;
		}
		ly += 2;
		float chipX = px + 6;
		String[][] chips = {{"Theme", String.valueOf(p.theme())}, {"HUD layout", String.valueOf(p.hud())}, {"Keys", String.valueOf(p.keys())}};
		for (String[] chip : chips) {
			if (!Boolean.parseBoolean(chip[1])) {
				continue;
			}
			float cw = JacText.width(chip[0], Theme.SMALL - 0.5f, JacText.Weight.SEMIBOLD) + 8;
			Draw.rect(g, chipX, ly, cw, 9, 4.5f, Colors.fade(Colors.withAlpha(accent, 0x50), t));
			JacText.draw(g, chip[0], chipX + 4, ly + 2, Colors.fade(Theme.TEXT, t), Theme.SMALL - 0.5f, JacText.Weight.SEMIBOLD);
			chipX += cw + 3;
		}
		ly += 13;
		if (!p.changedNames().isEmpty()) {
			JacText.draw(g, "Changes", px + 6, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.SEMIBOLD);
			ly += 9;
			int shown = 0;
			for (String n : p.changedNames()) {
				if (ly > bottom - (p.theme() ? 24 : 10)) {
					JacText.draw(g, "and " + (p.changedNames().size() - shown) + " more", px + 6, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL,
						JacText.Weight.REGULAR);
					break;
				}
				JacText.draw(g, fit(n, pw - 12, Theme.SMALL), px + 6, ly, Colors.fade(Theme.TEXT, t), Theme.SMALL, JacText.Weight.REGULAR);
				ly += 8.5f;
				shown++;
			}
		} else {
			JacText.draw(g, "Same as your current setup", px + 6, ly, Colors.fade(Theme.TEXT_DIM, t), Theme.SMALL, JacText.Weight.REGULAR);
		}
		if (p.theme()) {
			checkbox(g, px + 6, bottom - 14, "Also use its theme", useTheme, t, accent, mx, my);
		}
	}

	private static String fit(String s, float max, float size) {
		if (JacText.width(s, size, JacText.Weight.REGULAR) <= max) {
			return s;
		}
		while (s.length() > 1 && JacText.width(s + "...", size, JacText.Weight.REGULAR) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

	private void doExport() {
		if (!partsChosen()) {
			return;
		}
		try {
			var file = Profiles.exportToFile(new Profiles.Parts(parts[0], parts[1], parts[2], parts[3], parts[4]));
			Notifications.push(Icons.UPLOAD, "Exported", file.getFileName().toString());
			Util.getPlatform().openPath(Profiles.EXPORTS);
		} catch (IOException e) {
			Notifications.push(Icons.INFO, "Export failed", e.getClass().getSimpleName());
		}
		onClose.run();
	}

	private void doImport() {
		if (preview == null || preview.root() == null) {
			return;
		}
		String name = Profiles.importJson(preview.root(), "Imported", useTheme);
		Notifications.push(Icons.DOWNLOAD, name, "imported");
		onImported.run();
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (primary == null) {
			return true;
		}
		if (primary.mouseClicked(mx, my, button) || cancel.mouseClicked(mx, my, button) || folder.mouseClicked(mx, my, button)) {
			return true;
		}
		if (mx < x || mx >= x + w || my < y || my >= y + h) {
			onClose.run();
			return true;
		}
		if (export) {
			float cy = y + PAD + 34f;
			for (int i = 0; i < PARTS.length; i++) {
				if (mx >= x + PAD && mx < x + PAD + 160 && my >= cy + i * 14f - 2 && my < cy + i * 14f + 11) {
					parts[i] = !parts[i];
				}
			}
			return true;
		}
		float top = y + PAD + 20f;
		float bottom = y + h - PAD - 26f;
		float listW = w * 0.46f;
		if (mx >= x + PAD && mx < x + PAD + listW && my >= top && my < bottom) {
			int i = scroll + (int) ((my - top) / ITEM);
			if (i >= 0 && i < files.size()) {
				pick(i);
			}
			return true;
		}
		float px = x + PAD + listW + 10;
		if (preview != null && preview.theme() && mx >= px + 6 && mx < px + 160 && my >= bottom - 16 && my < bottom - 3) {
			useTheme = !useTheme;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double amount) {
		scroll = Math.max(0, scroll - (int) Math.signum(amount));
		return true;
	}

	@Override
	public boolean keyPressed(int key) {
		if (key == InputConstants.KEY_ESCAPE) {
			onClose.run();
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (export) {
				doExport();
			} else {
				doImport();
			}
		} else if (!export && !files.isEmpty() && (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP)) {
			pick(Math.max(0, Math.min(files.size() - 1, selected + (key == InputConstants.KEY_DOWN ? 1 : -1))));
		}
		return true;
	}
}
