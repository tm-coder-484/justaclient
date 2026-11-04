package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.mixin.FoodDataAccessor;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Food details on the hunger bar and in tooltips: how full your saturation is (a gold line under each shank), what the
 * food in your hand would add (the shanks it fills blink), and food and saturation values on food tooltips. Exhaustion is
 * never sent to the client, so its bar only shows in singleplayer, read from your own world.
 */
public final class FoodInfoFeature extends Feature {
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
	private static final Identifier HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");
	private static final int GOLD = 0xFFFACC15;

	private final BoolSetting saturation = bool("saturation", "Saturation under the shanks", true);
	private final BoolSetting preview = bool("preview", "Preview the food in your hand", true);
	private final BoolSetting exhaustion = bool("exhaustion", "Exhaustion bar (singleplayer)", true);
	private final BoolSetting tooltip = bool("tooltip", "Food values in tooltips", true);

	public FoodInfoFeature() {
		super("food_info", "Food info", "Saturation, a preview of what you're holding, and food values in tooltips", Category.INVENTORY,
			Icons.UTENSILS, true);
	}

	public static void init() {
		FoodInfoFeature feature = Features.get(FoodInfoFeature.class);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			FoodProperties food = feature.isEnabled() && feature.tooltip.on() ? stack.get(DataComponents.FOOD) : null;
			if (food == null || lines.isEmpty()) {
				return;
			}
			String shanks = food.nutrition() % 2 == 0 ? String.valueOf(food.nutrition() / 2) : String.format("%.1f", food.nutrition() / 2f);
			lines.add(Math.min(1, lines.size()), Component.literal("+" + food.nutrition() + " food (" + shanks + " shanks), +"
				+ String.format("%.1f", food.saturation()) + " saturation").withStyle(ChatFormatting.GRAY));
		});
	}

	private static @Nullable FoodProperties held(Player player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food != null) {
				return food;
			}
		}
		return null;
	}

	/** Called after the game draws the hunger bar. {@code right} is its right edge and {@code y} its top. */
	public void drawHungerBar(GuiGraphics g, Player player, int y, int right) {
		if (!isEnabled()) {
			return;
		}
		FoodData data = player.getFoodData();
		int food = data.getFoodLevel();
		float sat = data.getSaturationLevel();
		FoodProperties holding = preview.on() ? held(player) : null;
		if (holding != null && (food < 20 || holding.canAlwaysEat())) {
			int newFood = Math.min(20, food + holding.nutrition());
			// A slow fade in and out, so the new shanks read as "would be".
			float pulse = 0.35f + 0.45f * (0.5f + 0.5f * Mth.sin((float) (System.currentTimeMillis() % 2000L) / 2000f * Mth.TWO_PI));
			boolean hunger = player.hasEffect(MobEffects.HUNGER);
			for (int i = 0; i < 10; i++) {
				int lo = i * 2 + 1;
				int x = right - i * 8 - 9;
				// Only the half-shanks that are new get drawn.
				if (lo < newFood && lo >= food) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? FULL_HUNGER : FULL, x, y, 9, 9, pulse);
				} else if (lo == newFood && lo > food) {
					g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? HALF_HUNGER : HALF, x, y, 9, 9, pulse);
				}
			}
			if (saturation.on()) {
				float newSat = Math.min(newFood, sat + holding.saturation());
				underline(g, newSat, right, y, 0x80FACC15);
			}
		}
		if (saturation.on()) {
			underline(g, sat, right, y, GOLD);
		}
		if (exhaustion.on()) {
			float ex = singleplayerExhaustion(player);
			if (ex >= 0) {
				float w = 79 * Math.min(1f, ex / 4f);
				Draw.rect(g, right - w - 1, y - 2, w, 1, 0f, 0xB0FFFFFF);
			}
		}
	}

	/** The same details on the client's bar-style hunger bar ({@code x} to {@code x + w}, draining from the right). */
	public void drawBar(GuiGraphics g, Player player, float x, float y, float w, float h, int color) {
		if (!isEnabled()) {
			return;
		}
		FoodData data = player.getFoodData();
		int food = data.getFoodLevel();
		float sat = data.getSaturationLevel();
		FoodProperties holding = preview.on() ? held(player) : null;
		if (holding != null && (food < 20 || holding.canAlwaysEat())) {
			int newFood = Math.min(20, food + holding.nutrition());
			float pulse = 0.35f + 0.4f * (0.5f + 0.5f * Mth.sin((float) (System.currentTimeMillis() % 2000L) / 2000f * Mth.TWO_PI));
			float from = w * food / 20f;
			float to = w * newFood / 20f;
			if (to > from) {
				Draw.rect(g, x + w - to, y, to - from, h, h / 2f, Colors.fade(color, pulse));
			}
			if (saturation.on()) {
				float sw = w * Math.min(newFood, sat + holding.saturation()) / 20f;
				Draw.rect(g, x + w - sw, y + h - 1f, sw, 1f, 0f, 0x80FACC15);
			}
		}
		if (saturation.on() && sat > 0f) {
			float sw = w * sat / 20f;
			Draw.rect(g, x + w - sw, y + h - 1f, sw, 1f, 0f, GOLD);
		}
		if (exhaustion.on()) {
			float ex = singleplayerExhaustion(player);
			if (ex > 0f) {
				float ew = w * Math.min(1f, ex / 4f);
				Draw.rect(g, x + w - ew, y + h + 1f, ew, 1f, 0f, 0x90FFFFFF);
			}
		}
	}

	/** A line under each shank, as long as the saturation it holds (2 points per shank). */
	private static void underline(GuiGraphics g, float sat, int right, int y, int color) {
		for (int i = 0; i < 10; i++) {
			float part = Math.max(0f, Math.min(2f, sat - i * 2)) / 2f;
			if (part <= 0f) {
				break;
			}
			int x = right - i * 8 - 9;
			float w = 7f * part;
			// Shanks fill from the right, so the line grows from the right edge of each one.
			Draw.rect(g, x + 1 + (7f - w), y + 9, w, 1, 0f, color);
		}
	}

	/** Exhaustion (0 to 4) from the singleplayer world, or -1 on a server where the client is never told it. */
	private static float singleplayerExhaustion(Player player) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return -1f;
		}
		ServerPlayer own = server.getPlayerList().getPlayer(player.getUUID());
		return own == null ? -1f : ((FoodDataAccessor) own.getFoodData()).jac$exhaustion();
	}
}
