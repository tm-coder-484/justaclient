package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorHud extends HudFeature {
	public enum Style implements ChoiceSetting.Named {
		BAR("Bar"), PERCENT("Percent"), NUMBER("Durability");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum BarColor implements ChoiceSetting.Named {
		HEALTH("Health"), ACCENT("Accent");

		private final String label;

		BarColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final float ICON = 10f;
	private static final float BAR_W = 30f;
	private static List<ItemStack> preview;

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.BAR);
	private final ChoiceSetting<BarColor> barColor = choice("bar_color", "Bar colour", BarColor.HEALTH);
	private final BoolSetting heldItem = bool("held_item", "Include held item", false);
	private final List<ItemStack> items = new ArrayList<>();
	private final Spring[] fills = new Spring[5];

	public ArmorHud() {
		super("armor", "Armor", "Durability of what you are wearing", Icons.SHIELD, true, 0.86f, 1f);
		for (int i = 0; i < fills.length; i++) {
			fills[i] = Spring.ui(1f);
		}
	}

	private static List<ItemStack> previewItems() {
		if (preview == null) {
			preview = List.of(
				damaged(new ItemStack(Items.DIAMOND_HELMET), 0.92f), damaged(new ItemStack(Items.DIAMOND_CHESTPLATE), 0.74f),
				damaged(new ItemStack(Items.DIAMOND_LEGGINGS), 0.48f), damaged(new ItemStack(Items.DIAMOND_BOOTS), 0.18f)
			);
		}
		return preview;
	}

	private static ItemStack damaged(ItemStack stack, float left) {
		stack.setDamageValue(Math.round(stack.getMaxDamage() * (1f - left)));
		return stack;
	}

	@Override
	public void layout(boolean preview) {
		items.clear();
		Player player = Minecraft.getInstance().player;
		if (preview || player == null) {
			if (preview) {
				items.addAll(previewItems());
			}
		} else {
			for (EquipmentSlot slot : SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
			ItemStack held = player.getMainHandItem();
			if (heldItem.on() && held.isDamageableItem()) {
				items.add(held);
			}
		}
		float valueW = JacText.width(style.get() == Style.NUMBER ? "0000" : "100%", TEXT, JacText.Weight.SEMIBOLD);
		float barW = style.get() == Style.BAR ? BAR_W + 5f : 0f;
		width = PAD * 2 + ICON + 5f + barW + valueW;
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
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			g.pose().pushMatrix();
			g.pose().translate(PAD, y);
			g.pose().scale(ICON / 16f, ICON / 16f);
			g.renderItem(stack, 0, 0);
			g.pose().popMatrix();
			float textY = y + (ICON - JacText.lineHeight(TEXT)) / 2f;
			float x = PAD + ICON + 5f;
			if (stack.isDamageableItem()) {
				float left = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
				fills[i].setTarget(left);
				float shown = Math.max(0f, Math.min(1f, fills[i].update()));
				int color = barColor.get() == BarColor.ACCENT ? JacConfig.accent() : healthColor(shown);
				if (style.get() == Style.BAR) {
					float by = y + ICON / 2f - 1.25f;
					Draw.rect(g, x, by, BAR_W, 2.5f, 1.25f, Theme.CONTROL);
					Draw.rect(g, x, by, Math.max(2.5f, BAR_W * shown), 2.5f, 1.25f, color);
					x += BAR_W + 5f;
				}
				String value = style.get() == Style.NUMBER
					? String.valueOf(stack.getMaxDamage() - stack.getDamageValue())
					: Math.round(left * 100) + (style.get() == Style.PERCENT ? "%" : "");
				textRight(g, value, width - PAD, textY, style.get() == Style.BAR ? Theme.TEXT : Colors.lerp(color, Theme.TEXT, 0.35f), TEXT,
					JacText.Weight.SEMIBOLD);
			}
			y += ICON + 2f;
		}
	}

	/** Green when fresh, through amber, to red when nearly broken. */
	public static int healthColor(float left) {
		return left > 0.5f
			? Colors.lerp(0xFFF5C542, 0xFF4ADE80, (left - 0.5f) * 2f)
			: Colors.lerp(0xFFF43F5E, 0xFFF5C542, left * 2f);
	}
}
