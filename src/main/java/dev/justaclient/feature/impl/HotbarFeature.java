package dev.justaclient.feature.impl;

import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * A restyled hotbar: glass slots with a selection that slides between them, and smooth bars for health, hunger, armor,
 * air and experience in place of the icon rows. It shows exactly what vanilla shows (no saturation, nothing hidden), in
 * the same footprint, so vanilla's jump and locator bars still fit when they take over the experience bar.
 */
public final class HotbarFeature extends Feature {
	private static final float SLOT = 18f;
	private static final float GAP = 2f;
	private static final float INSET = 3f;
	private static final float PANEL_W = 9 * SLOT + 8 * GAP + INSET * 2;
	private static final float PANEL_H = SLOT + INSET * 2 - 2f;

	private final BoolSetting slide = bool("slide", "Selection slides", true);
	private final BoolSetting bars = bool("bars", "Bar-style health and hunger", true);
	private final BoolSetting numbers = bool("numbers", "Show numbers", true).visibleWhen(bars::on);
	private final BoolSetting xpBar = bool("xp_bar", "Smooth experience bar", true);
	private final Spring selection = Spring.ui(0f);
	private final Spring healthShown = Spring.ui(20f);
	private final Spring healthGhost = new Spring(30f, 11f, 20f);
	private final Spring foodShown = Spring.ui(20f);
	private final Spring xpShown = Spring.ui(0f);
	private int lastSlot = -1;

	public HotbarFeature() {
		super("hotbar", "Hotbar", "Glass slots, sliding selection, bar-style health and hunger", Category.HUD, Icons.LAYERS, true);
	}

	public static void init() {
		HotbarFeature hotbar = Features.get(HotbarFeature.class);
		replace(VanillaHudElements.HOTBAR, hotbar::active, hotbar::render);
		for (Identifier id : new Identifier[] {VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.ARMOR_BAR,
			VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH}) {
			replace(id, () -> hotbar.active() && hotbar.bars.on(), null);
		}
		replace(VanillaHudElements.EXPERIENCE_LEVEL, hotbar::ownsXp, null);
		replace(VanillaHudElements.INFO_BAR, hotbar::ownsXp, null);
	}

	/** Swaps a vanilla element for {@code ours} (or nothing) while {@code when} holds. */
	private static void replace(Identifier id, java.util.function.BooleanSupplier when, @Nullable HudElement ours) {
		HudElementRegistry.replaceElement(id, vanilla -> (g, delta) -> {
			if (when.getAsBoolean()) {
				if (ours != null) {
					ours.render(g, delta);
				}
			} else {
				vanilla.render(g, delta);
			}
		});
	}

	private boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
	}

	/** True when vanilla would show the experience bar (not the jump or locator bar) and we draw it instead. */
	private boolean ownsXp() {
		Minecraft mc = Minecraft.getInstance();
		return active() && xpBar.on() && mc.gameMode.hasExperience() && mc.player.jumpableVehicle() == null
			&& !mc.player.connection.getWaypointManager().hasWaypoints();
	}

	private void render(GuiGraphics g, DeltaTracker delta) {
		cost.begin();
		try {
			draw(g, delta);
		} finally {
			cost.end();
		}
	}

	private void draw(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		float cx = g.guiWidth() / 2f;
		float bottom = g.guiHeight();
		float px = cx - PANEL_W / 2f;
		float py = bottom - PANEL_H - 2f;
		int accent = JacConfig.accent();
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);

		Draw.hudPanel(g, px, py, PANEL_W, PANEL_H);
		int selected = player.getInventory().getSelectedSlot();
		if (lastSlot < 0 || !slide.on()) {
			selection.snap(selected);
		}
		lastSlot = selected;
		selection.setTarget(selected);
		float sel = selection.update();
		float sx = px + INSET + sel * (SLOT + GAP);
		float sy = py + INSET - 1f;
		for (int i = 0; i < 9; i++) {
			Draw.rect(g, px + INSET + i * (SLOT + GAP), sy, SLOT, SLOT, Math.min(4f, radius), 0x14FFFFFF);
		}
		Draw.shadow(g, sx, sy, SLOT, SLOT, Math.min(4f, radius), 5f, Colors.withAlpha(accent, 0x50));
		Draw.rect(g, sx, sy, SLOT, SLOT, Math.min(4f, radius), Colors.withAlpha(accent, 0x40));
		Draw.outline(g, sx, sy, SLOT, SLOT, Math.min(4f, radius), 1f, Colors.withAlpha(accent, 0xE0));

		int seed = 1;
		for (int i = 0; i < 9; i++) {
			slot(g, mc, player, player.getInventory().getItem(i), px + INSET + i * (SLOT + GAP) + 1f, sy + 1f, delta, seed++);
		}
		ItemStack offhand = player.getOffhandItem();
		boolean offLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
		if (!offhand.isEmpty()) {
			float ox = offLeft ? px - SLOT - INSET * 2 - 4f : px + PANEL_W + 4f;
			Draw.hudPanel(g, ox, py, SLOT + INSET * 2 - 2f, PANEL_H);
			slot(g, mc, player, offhand, ox + INSET, sy + 1f, delta, seed);
		}
		if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
			float strength = player.getAttackStrengthScale(0f);
			if (strength < 1f) {
				float ax = offLeft ? px + PANEL_W + 13f : px - 13f;
				Draw.arc(g, ax, py + PANEL_H / 2f, 7f, 2f, 0f, 1f, Theme.CONTROL);
				Draw.arc(g, ax, py + PANEL_H / 2f, 7f, 2f, 0f, strength, accent);
			}
		}

		float barTop = py;
		if (ownsXp()) {
			barTop = drawXp(g, player, px, py);
		}
		if (bars.on() && mc.gameMode.canHurtPlayer()) {
			drawVitals(g, player, px, Math.min(barTop, bottom - 31f) - 8f);
		}
	}

	private static void slot(GuiGraphics g, Minecraft mc, Player player, ItemStack stack, float x, float y, DeltaTracker delta, int seed) {
		if (stack.isEmpty()) {
			return;
		}
		int ix = Math.round(x);
		int iy = Math.round(y);
		// Vanilla's pickup "pop": squash and stretch for a few ticks.
		float pop = stack.getPopTime() - delta.getGameTimeDeltaPartialTick(false);
		if (pop > 0f) {
			float s = 1f + pop / 5f;
			g.pose().pushMatrix();
			g.pose().translate(ix + 8, iy + 12);
			g.pose().scale(1f / s, (s + 1f) / 2f);
			g.pose().translate(-(ix + 8), -(iy + 12));
		}
		g.renderItem(player, stack, ix, iy, seed);
		if (pop > 0f) {
			g.pose().popMatrix();
		}
		g.renderItemDecorations(mc.font, stack, ix, iy);
	}

	/** The experience bar just above the slots, with the level at its right. Returns the bar's top. */
	private float drawXp(GuiGraphics g, Player player, float px, float py) {
		int level = player.experienceLevel;
		String text = level > 0 ? "Lv " + level : "";
		float textW = text.isEmpty() ? 0f : JacText.width(text, 6f, JacText.Weight.SEMIBOLD) + 4f;
		float y = py - 6f;
		float w = PANEL_W - textW;
		xpShown.setTarget(player.experienceProgress);
		if (Math.abs(xpShown.get() - player.experienceProgress) > 0.5f) {
			xpShown.snap(player.experienceProgress);
		}
		float p = Math.max(0f, Math.min(1f, xpShown.update()));
		Draw.rect(g, px, y, w, 3f, 1.5f, 0x80000000);
		if (p > 0f) {
			Draw.horizontalGradient(g, px, y, Math.max(3f, w * p), 3f, 1.5f, 0xFF7EE36B, 0xFFB8F27C);
		}
		if (!text.isEmpty()) {
			shadowed(g, text, px + PANEL_W - textW + 4f, y + 1.5f - JacText.lineHeight(6f) / 2f, 0xFFB8F27C, 6f);
		}
		return y;
	}

	/** Health on the left half, hunger (or mount health) on the right, with armor and air as thin bars above them. */
	private void drawVitals(GuiGraphics g, Player player, float px, float y) {
		float half = (PANEL_W - 6f) / 2f;
		float right = px + PANEL_W - half;
		float h = 4f;

		float max = Math.max(1f, player.getMaxHealth());
		float absorb = player.getAbsorptionAmount();
		float hp = player.getHealth();
		healthShown.setTarget(hp);
		healthGhost.setTarget(hp);
		float shown = Math.max(0f, healthShown.update());
		float ghost = Math.max(shown, healthGhost.update());
		if (ghost - shown < 0.01f) {
			healthGhost.snap(shown);
		}
		float total = max + absorb;
		Draw.rect(g, px, y, half, h, h / 2f, 0x80000000);
		Draw.rect(g, px, y, Math.max(h, half * Math.min(1f, ghost / total)), h, h / 2f, 0xB0FFFFFF);
		int[] heart = heartColors(player);
		float low = hp <= 4f ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0) : 0f;
		Draw.horizontalGradient(g, px, y, Math.max(h, half * Math.min(1f, shown / total)), h, h / 2f,
			Colors.lerp(heart[0], 0xFFFFFFFF, low * 0.3f), heart[1]);
		if (absorb > 0f) {
			float ax = px + half * (shown / total);
			Draw.rect(g, ax, y, half * (absorb / total), h, h / 2f, 0xFFF5C542);
		}
		if (numbers.on()) {
			String v = String.valueOf((int) Math.ceil(hp + absorb));
			shadowed(g, v, px - 3f - JacText.width(v, 6.5f, JacText.Weight.SEMIBOLD), y + h / 2f - JacText.lineHeight(6.5f) / 2f, 0xFFFF8A9A, 6.5f);
		}
		int armor = player.getArmorValue();
		if (armor > 0) {
			Draw.rect(g, px, y - 3.5f, half, 2f, 1f, 0x60000000);
			Draw.rect(g, px, y - 3.5f, Math.max(2f, half * Math.min(1f, armor / 20f)), 2f, 1f, 0xFFB7C6DE);
		}

		LivingEntity mount = player.getVehicle() instanceof LivingEntity living && living.showVehicleHealth() ? living : null;
		float value;
		float maxValue;
		int c0;
		int c1;
		if (mount != null) {
			value = mount.getHealth();
			maxValue = Math.max(1f, mount.getMaxHealth());
			c0 = 0xFFFF9EB5;
			c1 = 0xFFE8577A;
		} else {
			value = player.getFoodData().getFoodLevel();
			maxValue = 20f;
			boolean hunger = player.hasEffect(MobEffects.HUNGER);
			c0 = hunger ? 0xFF9CCB4A : 0xFFF5B94A;
			c1 = hunger ? 0xFF6E9A2C : 0xFFF08A2E;
		}
		foodShown.setTarget(value);
		float food = Math.max(0f, Math.min(maxValue, foodShown.update()));
		Draw.rect(g, right, y, half, h, h / 2f, 0x80000000);
		// Hunger drains from the right like vanilla's drumsticks, so the bar is anchored on the right edge.
		float fw = Math.max(h, half * food / maxValue);
		Draw.horizontalGradient(g, right + half - fw, y, fw, h, h / 2f, c1, c0);
		if (numbers.on()) {
			shadowed(g, String.valueOf((int) Math.ceil(value)), right + half + 3f, y + h / 2f - JacText.lineHeight(6.5f) / 2f, mount != null ? c0 : 0xFFFFC56B, 6.5f);
		}
		int air = player.getAirSupply();
		int maxAir = player.getMaxAirSupply();
		if (air < maxAir || player.isUnderWater()) {
			float a = Math.max(0f, Math.min(1f, air / (float) maxAir));
			Draw.rect(g, right, y - 3.5f, half, 2f, 1f, 0x60000000);
			float aw = Math.max(2f, half * a);
			Draw.rect(g, right + half - aw, y - 3.5f, aw, 2f, 1f, 0xFF7DD3FC);
		}
	}

	/** Bar colours that follow vanilla's heart types: poison, wither and freezing change the colour, as hearts do. */
	private static int[] heartColors(Player player) {
		if (player.hasEffect(MobEffects.POISON)) {
			return new int[] {0xFFB5D334, 0xFF7FA51E};
		}
		if (player.hasEffect(MobEffects.WITHER)) {
			return new int[] {0xFF6B6B6B, 0xFF3A3A3A};
		}
		if (player.isFullyFrozen()) {
			return new int[] {0xFFBEEBFF, 0xFF7CC8F0};
		}
		return new int[] {0xFFFF5A6E, 0xFFE8344E};
	}

	private static void shadowed(GuiGraphics g, String s, float x, float y, int color, float size) {
		JacText.draw(g, s, x + 0.5f, y + 0.5f, 0xA0000000, size, JacText.Weight.SEMIBOLD);
		JacText.draw(g, s, x, y, color, size, JacText.Weight.SEMIBOLD);
	}
}
