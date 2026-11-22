package dev.justaclient.ui;

import dev.justaclient.anim.Colors;
import dev.justaclient.common.kits.Kit;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.KitsFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.inventory.InvItems;
import dev.justaclient.ui.widget.InputBox;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Kits: saved layouts on the left, the chosen one as an inventory grid on the right. A kit made from your inventory
 * ({@code /ck save}) can be trimmed first: click a slot to leave it free. Use gives it in singleplayer with cheats, or
 * checks it over your inventory anywhere else.
 */
public final class KitsScreen extends JacScreen {
	private static final float PAD = 12f;
	private static final float ROW = 20f;
	private static final float CELL = 19f;

	private final InputBox name = new InputBox("", Kit.MAX_NAME, "Kit name", Icons.PENCIL);
	/** The kit on the right: a saved one, or a new one from your inventory that isn't saved yet. */
	private @Nullable Kit editing;
	private boolean unsaved;
	private boolean confirmDelete;
	private String status = "";
	private float px;
	private float py;
	private float pw;
	private float ph;
	private float lw;
	private float rx;
	private float rw;
	private float gx;
	private float gy;
	private float by;

	public KitsScreen(@Nullable Screen parent) {
		super("Kits", parent);
		List<Kit> kits = KitsFeature.get().kits();
		if (!kits.isEmpty()) {
			pick(kits.get(0));
		}
	}

	/** Opens with a new kit made from your inventory, named {@code name}, ready to trim and save. */
	public static KitsScreen fromInventory(String name) {
		KitsScreen s = new KitsScreen(null);
		s.editing = KitsFeature.capture(name);
		s.unsaved = true;
		s.name.set(name);
		s.status = Kit.find(KitsFeature.get().kits(), name) != null ? "Saving replaces the kit called " + name : "Click a slot to leave it free";
		return s;
	}

	/** Opens with a saved kit shown. */
	public static KitsScreen preview(Kit kit) {
		KitsScreen s = new KitsScreen(null);
		s.pick(kit);
		return s;
	}

	private void pick(Kit kit) {
		editing = new Kit(kit.name, kit.created, kit.slots);
		unsaved = false;
		confirmDelete = false;
		name.set(kit.name);
		status = "";
	}

	private void layout() {
		pw = Math.min(520f, width - 24f);
		ph = Math.min(300f, height - 24f);
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		lw = Math.min(150f, pw * 0.3f);
		rx = px + PAD * 2 + lw;
		rw = px + pw - PAD - rx;
		name.place(rx, py + 38, Math.min(200, rw));
		gx = rx + Math.max(0, (rw - (CELL * 10 + 6)) / 2f);
		gy = py + 64;
		by = py + ph - PAD - 18;
	}

	/** Where a kit slot sits in the grid: armour and off hand in a column on the left, then main rows and the hotbar. */
	private float[] cell(int slot) {
		if (slot >= 36 && slot <= 39) {
			return new float[] {gx, gy + (39 - slot) * CELL};
		}
		if (slot == 40) {
			return new float[] {gx, gy + 3 * CELL + 6 + CELL};
		}
		float x0 = gx + CELL + 6;
		if (slot < 9) {
			return new float[] {x0 + slot * CELL, gy + 3 * CELL + 6 + CELL};
		}
		int r = (slot - 9) / 9;
		return new float[] {x0 + (slot - 9) % 9 * CELL, gy + r * CELL};
	}

	@Override
	protected void draw(GuiGraphics g, int mx, int my, float t) {
		layout();
		float a = t;
		int accent = JacConfig.accent();
		Draw.panel(g, px, py + (1f - t) * 8f, pw, ph, a);
		JacText.icon(g, Icons.BACKPACK, px + PAD, py + PAD + 1, 10f, Colors.fade(accent, a));
		JacText.draw(g, "Kits", px + PAD + 15, py + PAD, Colors.fade(Theme.TEXT, a), Theme.TITLE, JacText.Weight.SEMIBOLD);
		JacText.draw(g, "Saved inventory layouts", px + PAD + 15, py + PAD + 12, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
		JacText.icon(g, Icons.X, px + pw - PAD - 11, py + 14, 8f, Colors.fade(closeHit(mx, my) ? Theme.TEXT : Theme.TEXT_DIM, a));

		// Saved kits, and a row to start one from your inventory.
		float ly = py + 38;
		boolean newHot = mx >= px + PAD && mx < px + PAD + lw && my >= ly && my < ly + ROW - 2;
		Draw.rect(g, px + PAD, ly, lw, ROW - 2, 4f, Colors.fade(newHot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent, a));
		JacText.icon(g, Icons.PLUS, px + PAD + 6, ly + 5.5f, 7f, Colors.fade(0xFFFFFFFF, a));
		JacText.draw(g, "From my inventory", px + PAD + 17, ly + 5.5f, Colors.fade(0xFFFFFFFF, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		ly += ROW + 4;
		Kit checking = KitsFeature.get().checking();
		for (Kit k : KitsFeature.get().kits()) {
			if (ly + ROW > py + ph - PAD) {
				break;
			}
			boolean chosen = editing != null && !unsaved && k.name.equalsIgnoreCase(editing.name);
			boolean hot = mx >= px + PAD && mx < px + PAD + lw && my >= ly && my < ly + ROW - 2;
			if (chosen || hot) {
				Draw.rect(g, px + PAD, ly, lw, ROW - 2, 4f, Colors.fade(chosen ? Colors.withAlpha(accent, 0x38) : Theme.CONTROL_HOVER, a));
			}
			JacText.draw(g, fit(k.name, Theme.LABEL, JacText.Weight.SEMIBOLD, lw - 40), px + PAD + 6, ly + 5.5f, Colors.fade(Theme.TEXT, a), Theme.LABEL,
				JacText.Weight.SEMIBOLD);
			String n = k == checking ? "checking" : k.slots.size() + "";
			JacText.draw(g, n, px + PAD + lw - 6 - JacText.width(n, Theme.SMALL, JacText.Weight.REGULAR), ly + 6, Colors.fade(k == checking ? accent
				: Theme.TEXT_DIM, a), Theme.SMALL, JacText.Weight.REGULAR);
			ly += ROW;
		}
		Draw.rect(g, rx - PAD / 2f, py + 38, 0.75f, ph - 50, 0, Colors.fade(Theme.DIVIDER, a));

		if (editing == null) {
			JacText.draw(g, "No kits yet: start one from your inventory", rx, py + 42, Colors.fade(Theme.TEXT_DIM, a), Theme.LABEL, JacText.Weight.REGULAR);
			return;
		}
		name.draw(g, mx, my, a);
		for (int slot = 0; slot < Kit.SLOTS; slot++) {
			float[] c = cell(slot);
			Kit.Slot s = editing.at(slot);
			boolean hot = mx >= c[0] && mx < c[0] + CELL - 1 && my >= c[1] && my < c[1] + CELL - 1;
			Draw.rect(g, c[0], c[1], CELL - 1, CELL - 1, 3f, Colors.fade(hot ? Theme.CONTROL_HOVER : Theme.CONTROL, a));
			if (s != null) {
				ItemStack stack = KitsFeature.stack(s);
				InvItems.draw(g, stack, c[0] + 1, c[1] + 1, 16);
				if (s.count() > 1) {
					String cnt = String.valueOf(s.count());
					JacText.draw(g, cnt, c[0] + CELL - 2 - JacText.width(cnt, Theme.SMALL, JacText.Weight.SEMIBOLD), c[1] + CELL - 8,
						Colors.fade(0xFFFFFFFF, a), Theme.SMALL, JacText.Weight.SEMIBOLD);
				}
			} else if (slot >= 36) {
				JacText.icon(g, Icons.SHIELD, c[0] + 5, c[1] + 5, 8f, Colors.fade(Theme.TEXT_DIM & 0x60FFFFFF, a));
			}
		}
		// The item under the mouse, by name.
		String line = status;
		for (int slot = 0; slot < Kit.SLOTS; slot++) {
			float[] c = cell(slot);
			Kit.Slot s = editing.at(slot);
			if (s != null && mx >= c[0] && mx < c[0] + CELL - 1 && my >= c[1] && my < c[1] + CELL - 1) {
				line = s.count() + "x " + s.name() + " · click to leave this slot free";
			}
		}
		JacText.draw(g, fit(line, Theme.SMALL + 0.5f, JacText.Weight.REGULAR, rw), rx, by - 14, Colors.fade(Theme.TEXT_DIM, a), Theme.SMALL + 0.5f,
			JacText.Weight.REGULAR);

		float bx = rx;
		bx = button(g, bx, unsaved ? "Save kit" : "Save name", Icons.CHECK, true, mx, my, a);
		if (!unsaved) {
			var server = Minecraft.getInstance().getSingleplayerServer();
			boolean gives = server != null && server.getWorldData().isAllowCommands();
			bx = button(g, bx, gives ? "Give me this kit" : "Check my inventory", Icons.BACKPACK, false, mx, my, a);
			button(g, bx, confirmDelete ? "Really delete?" : "Delete", Icons.TRASH, false, mx, my, a);
		}
	}

	private float button(GuiGraphics g, float x, String label, Icons icon, boolean primary, int mx, int my, float a) {
		int accent = JacConfig.accent();
		float w = JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 26;
		boolean hot = mx >= x && mx < x + w && my >= by && my < by + 18;
		int fill = primary ? (hot ? Colors.lerp(accent, 0xFFFFFFFF, 0.15f) : accent) : hot ? Theme.CONTROL_HOVER : Theme.CONTROL;
		Draw.rect(g, x, by, w, 18, 5f, Colors.fade(fill, a));
		JacText.icon(g, icon, x + 7, by + 5.5f, 7f, Colors.fade(primary ? 0xFFFFFFFF : Theme.TEXT, a));
		JacText.draw(g, label, x + 19, by + 5.5f, Colors.fade(primary ? 0xFFFFFFFF : Theme.TEXT, a), Theme.LABEL, JacText.Weight.SEMIBOLD);
		return x + w + 6;
	}

	private float buttonWidth(String label) {
		return JacText.width(label, Theme.LABEL, JacText.Weight.SEMIBOLD) + 26;
	}

	private boolean closeHit(double mx, double my) {
		return mx >= px + pw - PAD - 14 && mx < px + pw - PAD && my >= py + 10 && my < py + 26;
	}

	@Override
	protected boolean click(double mx, double my, int button, boolean doubleClick) {
		if (button != 0) {
			return false;
		}
		name.click(mx, my);
		if (closeHit(mx, my)) {
			onClose();
			return true;
		}
		KitsFeature kits = KitsFeature.get();
		float ly = py + 38;
		if (mx >= px + PAD && mx < px + PAD + lw) {
			if (my >= ly && my < ly + ROW - 2) {
				dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
				String n = "Kit " + (kits.kits().size() + 1);
				editing = KitsFeature.capture(n);
				unsaved = true;
				name.set(n);
				status = "Click a slot to leave it free";
				return true;
			}
			ly += ROW + 4;
			for (Kit k : kits.kits()) {
				if (my >= ly && my < ly + ROW - 2) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					pick(k);
					return true;
				}
				ly += ROW;
			}
			return true;
		}
		if (editing == null) {
			return false;
		}
		for (int slot = 0; slot < Kit.SLOTS; slot++) {
			float[] c = cell(slot);
			if (mx >= c[0] && mx < c[0] + CELL - 1 && my >= c[1] && my < c[1] + CELL - 1) {
				if (editing.at(slot) != null) {
					dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
					editing.clear(slot);
					if (!unsaved) {
						unsaved = true;
						status = "Save to keep the change";
					}
				}
				return true;
			}
		}
		if (my < by || my >= by + 18) {
			return false;
		}
		float bx = rx;
		String saveLabel = unsaved ? "Save kit" : "Save name";
		if (mx >= bx && mx < bx + buttonWidth(saveLabel)) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			String n = Kit.cleanName(name.get());
			if (n == null) {
				status = "Give the kit a name first";
				return true;
			}
			Kit old = editing.name.equalsIgnoreCase(n) ? null : Kit.find(kits.kits(), editing.name);
			if (old != null && !unsaved) {
				// Renaming a saved kit.
				kits.kits().remove(old);
			}
			Kit kit = new Kit(n, editing.created, editing.slots);
			kits.put(kit);
			pick(kit);
			status = "Saved";
			return true;
		}
		bx += buttonWidth(saveLabel) + 6;
		if (unsaved) {
			return true;
		}
		var server = Minecraft.getInstance().getSingleplayerServer();
		String useLabel = server != null && server.getWorldData().isAllowCommands() ? "Give me this kit" : "Check my inventory";
		if (mx >= bx && mx < bx + buttonWidth(useLabel)) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			Kit saved = Kit.find(kits.kits(), editing.name);
			if (saved != null) {
				kits.use(saved);
				onClose();
			}
			return true;
		}
		bx += buttonWidth(useLabel) + 6;
		String delLabel = confirmDelete ? "Really delete?" : "Delete";
		if (mx >= bx && mx < bx + buttonWidth(delLabel)) {
			dev.justaclient.feature.impl.GuiSoundsFeature.menuClick();
			if (!confirmDelete) {
				confirmDelete = true;
				return true;
			}
			Kit saved = Kit.find(kits.kits(), editing.name);
			if (saved != null) {
				kits.delete(saved);
			}
			editing = kits.kits().isEmpty() ? null : kits.kits().get(0);
			if (editing != null) {
				pick(editing);
			}
			status = "Deleted";
			return true;
		}
		return true;
	}

	@Override
	protected boolean key(int key, boolean ctrl, boolean shift) {
		return name.focused && name.key(key, ctrl);
	}

	@Override
	protected boolean typed(String text) {
		if (name.focused) {
			name.typed(text);
			return true;
		}
		return false;
	}
}
