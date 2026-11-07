package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** How many totems, pearls, golden apples, food and arrows you carry, counted across your whole inventory. */
public final class ConsumablesHud extends HudFeature {
	private static final float SLOT = 16f;
	private static final float GAP = 5f;

	private record Kind(BoolSetting on, ItemStack icon, Predicate<ItemStack> matches) {
	}

	private record Count(ItemStack icon, int count) {
	}

	private final List<Kind> kinds = List.of(
		new Kind(bool("totems", "Totems", true), new ItemStack(Items.TOTEM_OF_UNDYING), s -> s.is(Items.TOTEM_OF_UNDYING)),
		new Kind(bool("pearls", "Ender pearls", true), new ItemStack(Items.ENDER_PEARL), s -> s.is(Items.ENDER_PEARL)),
		new Kind(bool("gapples", "Golden apples", true), new ItemStack(Items.GOLDEN_APPLE),
			s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)),
		new Kind(bool("food", "Other food", false), new ItemStack(Items.COOKED_BEEF),
			s -> s.has(DataComponents.FOOD) && !s.is(Items.GOLDEN_APPLE) && !s.is(Items.ENCHANTED_GOLDEN_APPLE)),
		new Kind(bool("arrows", "Arrows", false), new ItemStack(Items.ARROW), s -> s.is(Items.ARROW) || s.is(Items.SPECTRAL_ARROW)
			|| s.is(Items.TIPPED_ARROW)),
		new Kind(bool("wind_charges", "Wind charges", false), new ItemStack(Items.WIND_CHARGE), s -> s.is(Items.WIND_CHARGE))
	);
	private final BoolSetting hideZero = bool("hide_zero", "Hide items you have none of", true);
	private final List<Count> counts = new ArrayList<>();

	public ConsumablesHud() {
		super("consumables", "Consumables", "Counts of totems, pearls, gapples and food", Icons.HEART, false, 0.5f, 0.72f);
	}

	@Override
	public void layout(boolean preview) {
		counts.clear();
		Player player = Minecraft.getInstance().player;
		int sample = 2;
		for (Kind k : kinds) {
			if (!k.on.on()) {
				continue;
			}
			int n = preview ? sample++ * 3 : player == null ? 0 : count(player.getInventory(), k.matches);
			if (n > 0 || !hideZero.on() || preview) {
				counts.add(new Count(k.icon, n));
			}
		}
		float w = PAD * 2 - GAP;
		for (Count c : counts) {
			w += SLOT + 2f + JacText.width(String.valueOf(c.count), TEXT, JacText.Weight.SEMIBOLD) + GAP;
		}
		width = Math.max(30f, w);
		height = PAD * 2 + SLOT;
	}

	private static int count(Inventory inventory, Predicate<ItemStack> matches) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (!s.isEmpty() && matches.test(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@Override
	public boolean hasContent() {
		return !counts.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float x = PAD;
		for (Count c : counts) {
			g.renderItem(c.icon, (int) x, (int) PAD);
			x += SLOT + 2f;
			String n = String.valueOf(c.count);
			text(g, n, x, PAD + (SLOT - JacText.lineHeight(TEXT)) / 2f, c.count == 0 ? Theme.TEXT_DIM : Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
			x += JacText.width(n, TEXT, JacText.Weight.SEMIBOLD) + GAP;
		}
	}
}
