package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The item in your hand: its name, how many you carry in total, durability and enchantments. */
public final class HeldItemHud extends HudFeature {
	private static final float ICON = 16f;
	private final BoolSetting total = bool("total", "Total carried", true);
	private final BoolSetting durability = bool("durability", "Durability", true);
	private final BoolSetting enchants = bool("enchants", "Enchantments", true);
	private ItemStack stack = ItemStack.EMPTY;
	private String name = "";
	private final List<String> lines = new ArrayList<>();

	public HeldItemHud() {
		super("held_item", "Held item", "Name, total carried, durability and enchantments of what you hold", Icons.BOX, false, 0.5f, 0.86f);
	}

	@Override
	public void layout(boolean preview) {
		lines.clear();
		Player player = Minecraft.getInstance().player;
		if (preview) {
			stack = new ItemStack(Items.DIAMOND_PICKAXE);
			name = "Diamond Pickaxe";
			if (durability.on()) {
				lines.add("1402 / 1561 durability");
			}
			if (enchants.on()) {
				lines.add("Efficiency V, Unbreaking III");
			}
		} else if (player == null || player.getMainHandItem().isEmpty()) {
			stack = ItemStack.EMPTY;
		} else {
			stack = player.getMainHandItem();
			name = stack.getHoverName().getString();
			if (total.on() && stack.getMaxStackSize() > 1) {
				lines.add(count(player.getInventory(), stack) + " carried");
			}
			if (durability.on() && stack.isDamageableItem()) {
				lines.add((stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage() + " durability");
			}
			if (enchants.on()) {
				ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
				List<String> names = new ArrayList<>();
				for (Holder<Enchantment> e : ench.keySet()) {
					names.add(Enchantment.getFullname(e, ench.getLevel(e)).getString());
				}
				if (!names.isEmpty()) {
					lines.add(String.join(", ", names));
				}
			}
		}
		float w = JacText.width(name, TEXT, JacText.Weight.SEMIBOLD);
		for (String line : lines) {
			w = Math.max(w, JacText.width(line, TEXT - 0.5f, JacText.Weight.REGULAR));
		}
		width = PAD * 2 + ICON + 6f + Math.min(w, 180f);
		height = PAD * 2 + Math.max(ICON, JacText.lineHeight(TEXT) + lines.size() * ROW);
	}

	private static int count(Inventory inventory, ItemStack like) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (ItemStack.isSameItemSameComponents(s, like)) {
				n += s.getCount();
			}
		}
		return n;
	}

	@Override
	public boolean hasContent() {
		return !stack.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		g.renderItem(stack, (int) PAD, (int) PAD);
		float x = PAD + ICON + 6f;
		float y = PAD;
		text(g, fit(name, 180f, TEXT, JacText.Weight.SEMIBOLD), x, y, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
		y += JacText.lineHeight(TEXT) + 2f;
		for (String line : lines) {
			text(g, fit(line, 180f, TEXT - 0.5f, JacText.Weight.REGULAR), x, y, Theme.TEXT_DIM, TEXT - 0.5f, JacText.Weight.REGULAR);
			y += ROW;
		}
	}

	private static String fit(String s, float max, float size, JacText.Weight weight) {
		if (JacText.width(s, size, weight) <= max) {
			return s;
		}
		while (s.length() > 1 && JacText.width(s + "...", size, weight) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}

}
