package dev.justaclient.feature.impl;

import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.render.Icons;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Adds a line to weapon tooltips comparing their attack damage and speed with what you are holding, using the same
 * attribute numbers the tooltip already lists. Enchantments are not counted.
 */
public final class WeaponCompareFeature extends Feature {
	public WeaponCompareFeature() {
		super("weapon_compare", "Weapon comparison", "Tooltips compare damage and speed with your held weapon", Category.COMBAT, Icons.SWORDS, true);
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			WeaponCompareFeature feature = Features.get(WeaponCompareFeature.class);
			Player player = Minecraft.getInstance().player;
			if (!feature.isEnabled() || player == null || lines.isEmpty()) {
				return;
			}
			ItemStack held = player.getMainHandItem();
			if (held == stack || held.isEmpty() || !isWeapon(stack) || !isWeapon(held) || ItemStack.isSameItemSameComponents(held, stack)) {
				return;
			}
			double damage = damage(stack) - damage(held);
			double speed = speed(stack) - speed(held);
			MutableComponent line = Component.literal("vs held: ").withStyle(ChatFormatting.GRAY);
			line.append(delta(damage, " damage"));
			line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			line.append(delta(speed, " speed"));
			lines.add(Math.min(1, lines.size()), line);
		});
	}

	private static boolean isWeapon(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
			.anyMatch(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE));
	}

	/** Attack damage with this item in the main hand: the player's base 1, plus the item's modifiers. */
	static double damage(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_DAMAGE, 1.0,
			EquipmentSlot.MAINHAND);
	}

	/** Attacks per second with this item in the main hand: the player's base 4, plus the item's modifiers. */
	static double speed(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ATTACK_SPEED, 4.0,
			EquipmentSlot.MAINHAND);
	}

	private static Component delta(double value, String what) {
		if (Math.abs(value) < 0.005) {
			return Component.literal("same" + what).withStyle(ChatFormatting.GRAY);
		}
		String text = (value > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", value) + what;
		return Component.literal(text).withStyle(value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

}
