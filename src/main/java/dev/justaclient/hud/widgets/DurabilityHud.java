package dev.justaclient.hud.widgets;

import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every worn-down tool, weapon and armour piece you carry, most worn first, so nothing breaks by surprise. */
public final class DurabilityHud extends HudFeature {
	private static final float ICON = 10f;
	private static final float BAR_W = 34f;
	private final NumberSetting below = number("below", "Show items under", 50, 5, 100, 5, NumberSetting.unit("%"));
	private final NumberSetting most = number("most", "Most items", 5, 1, 12, 1, NumberSetting.unit(""));
	private final List<ItemStack> items = new ArrayList<>();

	public DurabilityHud() {
		super("durability", "Durability overview", "Your most worn items in one list", Icons.WRENCH, false, 1f, 0.4f);
	}

	private static float left(ItemStack s) {
		return 1f - (float) s.getDamageValue() / Math.max(1, s.getMaxDamage());
	}

	@Override
	public void layout(boolean preview) {
		items.clear();
		Player player = Minecraft.getInstance().player;
		if (preview) {
			ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
			sword.setDamageValue(sword.getMaxDamage() * 8 / 10);
			ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
			boots.setDamageValue(boots.getMaxDamage() * 6 / 10);
			items.add(sword);
			items.add(boots);
		} else if (player != null) {
			Inventory inv = player.getInventory();
			float threshold = below.f() / 100f;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isDamageableItem() && left(s) < threshold) {
					items.add(s);
				}
			}
			items.sort(Comparator.comparingDouble(DurabilityHud::left));
			while (items.size() > most.i()) {
				items.removeLast();
			}
		}
		width = PAD * 2 + ICON + 5f + BAR_W + 5f + JacText.width("100%", TEXT, JacText.Weight.SEMIBOLD);
		height = PAD * 2 + items.size() * (ICON + 2f) - 2f;
	}

	@Override
	public boolean hasContent() {
		return !items.isEmpty();
	}

	@Override
	public void draw(GuiGraphics g, boolean preview) {
		panel(g, width, height);
		float y = PAD;
		for (ItemStack s : items) {
			g.pose().pushMatrix();
			g.pose().translate(PAD, y);
			g.pose().scale(ICON / 16f, ICON / 16f);
			g.renderItem(s, 0, 0);
			g.pose().popMatrix();
			float l = left(s);
			int color = ArmorHud.healthColor(l);
			float x = PAD + ICON + 5f;
			float by = y + ICON / 2f - 1.25f;
			Draw.rect(g, x, by, BAR_W, 2.5f, 1.25f, Theme.CONTROL);
			Draw.rect(g, x, by, Math.max(2.5f, BAR_W * l), 2.5f, 1.25f, color);
			textRight(g, Math.round(l * 100) + "%", width - PAD, y + (ICON - JacText.lineHeight(TEXT)) / 2f, Theme.TEXT, TEXT, JacText.Weight.SEMIBOLD);
			y += ICON + 2f;
		}
	}
}
