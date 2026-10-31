package dev.justaclient.hud.widgets;

import dev.justaclient.config.JacConfig;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.hud.HudRenderer;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Items on cooldown (pearls, shields, wind charges...) with a dial for the time left. */
public final class CooldownsHud extends HudFeature {
	private static final float SLOT = 18f;
	private final List<ItemStack> items = new ArrayList<>();
	private final List<Float> progress = new ArrayList<>();

	public CooldownsHud() {
		super("cooldowns", "Item cooldowns", "A tray of items on cooldown with a dial each", Icons.TIMER, true, 0.5f, 0.8f);
	}

	@Override
	public void layout(boolean preview) {
		items.clear();
		progress.clear();
		Player player = Minecraft.getInstance().player;
		if (preview) {
			items.add(new ItemStack(Items.ENDER_PEARL));
			progress.add(0.6f);
			items.add(new ItemStack(Items.SHIELD));
			progress.add(0.3f);
		} else if (player != null) {
			List<Item> seen = new ArrayList<>();
			float partial = HudRenderer.partialTick();
			for (int slot = 0; slot < 9; slot++) {
				consider(player, player.getInventory().getItem(slot), seen, partial);
			}
			consider(player, player.getOffhandItem(), seen, partial);
		}
		width = PAD * 2 + items.size() * (SLOT + 3f) - 3f;
		height = PAD * 2 + SLOT;
	}

	private void consider(Player player, ItemStack stack, List<Item> seen, float partial) {
		if (stack.isEmpty() || seen.contains(stack.getItem())) {
			return;
		}
		float pct = player.getCooldowns().getCooldownPercent(stack, partial);
		if (pct > 0f) {
			seen.add(stack.getItem());
			items.add(stack);
			progress.add(pct);
		}
	}

	@Override
	public boolean hasContent() {
		return !items.isEmpty();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		panel(g, width, height);
		for (int i = 0; i < items.size(); i++) {
			float x = PAD + i * (SLOT + 3f);
			float cx = x + SLOT / 2f;
			float cy = PAD + SLOT / 2f;
			Draw.arc(g, cx, cy, SLOT / 2f, 1.5f, 0f, 1f, Theme.CONTROL);
			// The dial empties clockwise as the cooldown runs out.
			Draw.arc(g, cx, cy, SLOT / 2f, 1.5f, 0f, progress.get(i), JacConfig.accent());
			float icon = 11f;
			g.pose().pushMatrix();
			g.pose().translate(cx - icon / 2f, cy - icon / 2f);
			g.pose().scale(icon / 16f, icon / 16f);
			g.item(items.get(i), 0, 0);
			g.pose().popMatrix();
		}
	}
}
