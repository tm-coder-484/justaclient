package dev.justaclient.ui.widget;

import dev.justaclient.anim.Colors;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.FileSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.JacScreen;
import dev.justaclient.ui.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Picks a file from the setting's folder. Click the box to open the list under it: "Automatic", each file, and a row
 * that opens the folder. The list is re-read every time it opens, so new files show up without a restart.
 */
public final class FilePicker extends Widget {
	private static final float LABEL_GAP = 11f;
	private static final float BOX_H = 15f;
	private static final float ROW = 13f;
	private static final int MAX_ROWS = 10;

	private final FileSetting setting;
	private List<String> files = List.of();
	private boolean open;

	public FilePicker(float x, float y, float width, FileSetting setting) {
		super(x, y, width, LABEL_GAP + BOX_H);
		this.setting = setting;
	}

	private int rows() {
		// Automatic, the files (up to the limit), and Open folder.
		return open ? 2 + Math.min(files.size(), MAX_ROWS) : 0;
	}

	@Override
	public float height() {
		height = LABEL_GAP + BOX_H + (open ? rows() * ROW + 4f : 0f);
		return height;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, double mouseX, double mouseY, float alpha) {
		height();
		JacText.draw(g, setting.name, x, y, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
		float by = y + LABEL_GAP;
		float radius = Math.min(4f, JacConfig.get().style.cornerRadius);
		boolean boxHot = mouseX >= x && mouseX < x + width && mouseY >= by && mouseY < by + BOX_H;
		Draw.rect(g, x, by, width, BOX_H, radius, Colors.fade(Colors.lerp(Theme.CONTROL, Theme.CONTROL_HOVER, open || boxHot ? 1f : 0f), alpha));
		if (open) {
			Draw.outline(g, x, by, width, BOX_H, radius, 1f, Colors.fade(JacConfig.accent(), alpha));
		}
		String value = setting.get();
		boolean auto = value.isEmpty();
		boolean missing = !auto && setting.resolve() == null;
		String shown = auto ? setting.placeholder : missing ? value + " (missing)" : value;
		shown = fit(shown, width - 30f);
		float ty = by + (BOX_H - JacText.lineHeight(Theme.LABEL)) / 2f;
		JacText.icon(g, Icons.IMAGE, x + 5f, by + 4f, 7f, Colors.fade(Theme.TEXT_DIM, alpha));
		JacText.draw(g, shown, x + 16f, ty, Colors.fade(missing ? 0xFFF87171 : auto ? Theme.TEXT_DIM : Theme.TEXT, alpha), Theme.LABEL,
			JacText.Weight.REGULAR);
		JacText.icon(g, open ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT, x + width - 11f, by + 4f, 7f, Colors.fade(Theme.TEXT_DIM, alpha));
		if (!open) {
			return;
		}
		float ry = by + BOX_H + 2f;
		for (int i = 0; i < rows(); i++) {
			boolean last = i == rows() - 1;
			String label = i == 0 ? "Automatic (first file)" : last ? files.isEmpty() ? "Open folder (it is empty)" : "Open folder"
				: files.get(i - 1);
			boolean chosen = i == 0 ? auto : !last && files.get(i - 1).equals(value);
			boolean hot = mouseX >= x && mouseX < x + width && mouseY >= ry && mouseY < ry + ROW;
			if (hot || chosen) {
				Draw.rect(g, x, ry, width, ROW, 3f, Colors.fade(hot ? Theme.CONTROL_HOVER : Colors.withAlpha(JacConfig.accent(), 0x30), alpha));
			}
			Icons icon = last ? Icons.FOLDER_OPEN : chosen ? Icons.CHECK : null;
			if (icon != null) {
				JacText.icon(g, icon, x + 5f, ry + 3f, 6.5f, Colors.fade(chosen ? JacConfig.accent() : Theme.TEXT_DIM, alpha));
			}
			JacText.draw(g, fit(label, width - 22f), x + 16f, ry + (ROW - JacText.lineHeight(Theme.SMALL + 0.5f)) / 2f,
				Colors.fade(last || i == 0 ? Theme.TEXT_DIM : Theme.TEXT, alpha), Theme.SMALL + 0.5f, JacText.Weight.REGULAR);
			ry += ROW;
		}
		if (files.size() > MAX_ROWS) {
			JacText.draw(g, "+" + (files.size() - MAX_ROWS) + " more, type the name in the folder", x + 16f, ry, Colors.fade(Theme.TEXT_DIM, alpha),
				Theme.SMALL, JacText.Weight.REGULAR);
		}
	}

	private static String fit(String text, float max) {
		String s = text;
		while (s.length() > 1 && JacText.width(s, Theme.LABEL, JacText.Weight.REGULAR) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s.length() < text.length() ? s + "..." : s;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!isMouseOver(mouseX, mouseY)) {
			open = false;
			return false;
		}
		if (button != 0) {
			return true;
		}
		float by = y + LABEL_GAP;
		if (mouseY < by + BOX_H) {
			if (mouseY >= by) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				open = !open;
				if (open) {
					files = setting.choices();
				}
			}
			return true;
		}
		int i = (int) ((mouseY - by - BOX_H - 2f) / ROW);
		if (i < 0 || i >= rows()) {
			return true;
		}
		dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
		if (i == rows() - 1) {
			JacScreen.openPath(setting.folder());
			return true;
		}
		setting.set(i == 0 ? "" : files.get(i - 1));
		open = false;
		return true;
	}
}
