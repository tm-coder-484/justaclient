package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Your main inventory (the 27 slots above the hotbar), so you can see what you carry without opening it. */
public final class InventoryHud extends HudFeature {
	private static final float SLOT = 16f;
	private static final float GAP = 2f;
	private final BoolSetting slots = bool("slots", "Slot backgrounds", true);
	private final BoolSetting hideEmpty = bool("hide_empty", "Hide when empty", true);
	private boolean empty;

	public InventoryHud() {
		super("inventory", "Inventory", "A preview of your inventory", Icons.LAYOUT_DASHBOARD, false, 1f, 1f);
	}

	@Override
	public void layout(boolean preview) {
		width = PAD * 2 + 9 * SLOT + 8 * GAP;
		height = PAD * 2 + 3 * SLOT + 2 * GAP;
		empty = true;
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			for (int i = 9; i < 36; i++) {
				if (!player.getInventory().getItem(i).isEmpty()) {
					empty = false;
					break;
				}
			}
		}
	}

	@Override
	public boolean hasContent() {
		return !(empty && hideEmpty.on());
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		panel(g, width, height);
		Player player = Minecraft.getInstance().player;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				float x = PAD + col * (SLOT + GAP);
				float y = PAD + row * (SLOT + GAP);
				if (slots.on()) {
					Draw.rect(g, x, y, SLOT, SLOT, 2f, Theme.CONTROL);
				}
				ItemStack stack = preview ? sample(row * 9 + col) : player == null ? ItemStack.EMPTY : player.getInventory().getItem(9 + row * 9 + col);
				if (!stack.isEmpty()) {
					g.item(stack, (int) x, (int) y);
					g.itemDecorations(Minecraft.getInstance().font, stack, (int) x, (int) y);
				}
			}
		}
	}

	private static ItemStack sample(int i) {
		return switch (i) {
			case 0 -> new ItemStack(Items.GOLDEN_APPLE, 12);
			case 1 -> new ItemStack(Items.ENDER_PEARL, 16);
			case 4 -> new ItemStack(Items.COOKED_BEEF, 48);
			case 9 -> new ItemStack(Items.ARROW, 64);
			case 13 -> new ItemStack(Items.TOTEM_OF_UNDYING);
			case 20 -> new ItemStack(Items.OAK_PLANKS, 37);
			default -> ItemStack.EMPTY;
		};
	}
}
