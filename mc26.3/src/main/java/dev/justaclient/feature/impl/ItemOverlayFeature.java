package dev.justaclient.feature.impl;

import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.hud.widgets.ArmorHud;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Restyles the small things drawn on item slots everywhere (hotbar, inventories, chests): rounded durability bars,
 * cooldown dials and sharp stack counts. Same information as vanilla, only drawn differently.
 */
public final class ItemOverlayFeature extends Feature {
	public enum BarColor implements ChoiceSetting.Named {
		VANILLA("Vanilla"), HEALTH("Smooth"), ACCENT("Accent");

		private final String label;

		BarColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Cooldown implements ChoiceSetting.Named {
		RING("Ring"), FILL("Fill");

		private final String label;

		Cooldown(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<BarColor> barColor = choice("bar_color", "Durability colour", BarColor.HEALTH);
	private final ChoiceSetting<Cooldown> cooldown = choice("cooldown", "Cooldown style", Cooldown.RING);
	private final BoolSetting sharpCount = bool("sharp_count", "Sharp stack count", true);

	public ItemOverlayFeature() {
		super("item_overlay", "Item overlay", "Rounded durability bars, cooldown dials, sharp counts", Category.HUD, Icons.LAYOUT_DASHBOARD, true);
	}

	/** Returns true if the durability bar was drawn here instead of by vanilla. */
	public boolean drawBar(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (!isEnabled()) {
			return false;
		}
		if (stack.isBarVisible()) {
			float fraction = stack.getBarWidth() / 13f;
			int color = switch (barColor.get()) {
				case VANILLA -> ARGB.opaque(stack.getBarColor());
				case HEALTH -> stack.isDamageableItem() ? ArmorHud.healthColor(fraction) : ARGB.opaque(stack.getBarColor());
				case ACCENT -> JacConfig.accent();
			};
			Draw.rect(g, x + 2, y + 13, 13, 2, 1f, 0xC0000000);
			Draw.rect(g, x + 2, y + 13, Math.max(2f, 13 * fraction), 2, 1f, color);
		}
		return true;
	}

	public boolean drawCooldown(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (!isEnabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		float f = player == null ? 0f : player.getCooldowns().getCooldownPercent(stack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		if (f > 0f) {
			if (cooldown.get() == Cooldown.RING) {
				Draw.circle(g, x + 8, y + 8, 8f, 0x50000000);
				Draw.arc(g, x + 8, y + 8, 7f, 1.5f, 0f, f, JacConfig.accent());
			} else {
				float top = y + 16f * (1f - f);
				Draw.rect(g, x, top, 16, y + 16 - top, 2f, 0x70FFFFFF);
			}
		}
		return true;
	}

	public boolean drawCount(GuiGraphicsExtractor g, ItemStack stack, int x, int y, @Nullable String text) {
		if (!isEnabled() || !sharpCount.on()) {
			return false;
		}
		if (stack.getCount() != 1 || text != null) {
			String s = text == null ? String.valueOf(stack.getCount()) : text;
			float size = 7f;
			float w = JacText.width(s, size, JacText.Weight.SEMIBOLD);
			float ty = y + 17f - JacText.lineHeight(size);
			JacText.draw(g, s, x + 17.5f - w + 0.6f, ty + 0.6f, 0xC0000000, size, JacText.Weight.SEMIBOLD);
			JacText.draw(g, s, x + 17.5f - w, ty, Theme.TEXT | 0xFF000000, size, JacText.Weight.SEMIBOLD);
		}
		return true;
	}
}
