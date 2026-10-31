package dev.justaclient.feature.impl;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Replaces the vanilla crosshair. It only restyles what vanilla already shows: the target colour uses the same entity
 * vanilla's attack indicator reacts to, and the pulse follows hits the player makes.
 */
public final class CrosshairFeature extends Feature {
	public enum Style implements ChoiceSetting.Named {
		CROSS("Cross"), DOT("Dot"), CIRCLE("Circle"), PLUS("Plus");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Style> style = choice("style", "Style", Style.CROSS);
	private final NumberSetting size = number("size", "Size", 4, 1, 10, 0.5, NumberSetting.decimal(" px"));
	private final NumberSetting gap = number("gap", "Gap", 3, 0, 8, 0.5, NumberSetting.decimal(" px"));
	private final NumberSetting thickness = number("thickness", "Thickness", 1.5, 0.5, 4, 0.5, NumberSetting.decimal(" px"));
	private final ColorSetting color = color("color", "Colour", ColorSetting.ACCENT);
	private final BoolSetting outline = bool("outline", "Dark outline", true);
	private final BoolSetting hitPulse = bool("hit_pulse", "Hit pulse", true);
	private final BoolSetting targetColor = bool("target_color", "Change colour on target", true);
	private final ColorSetting targetColorValue = color("target_color_value", "Target colour", "#F43F5E").visibleWhen(targetColor::on);
	private final BoolSetting dynamicSpread = bool("dynamic_spread", "Dynamic spread", false);
	private final Animation pulse = new Animation(Easing.CUBIC_OUT, 260, 0f);
	private final Animation targetFade = new Animation(Easing.CUBIC_OUT, 140, 0f);
	private final Spring spread = Spring.ui(0f);

	public CrosshairFeature() {
		super("crosshair", "Custom crosshair", "Styles, hit pulse, target colour", Category.COMBAT, Icons.CROSSHAIR, true);
	}

	public void onAttack() {
		if (isEnabled() && hitPulse.on()) {
			pulse.snap(1f).animateTo(0f);
		}
	}

	/** Defers to vanilla while the F3 3D crosshair is showing. */
	public boolean shouldReplaceVanilla() {
		Minecraft mc = Minecraft.getInstance();
		return !mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
	}

	public void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.gameMode == null || !mc.options.getCameraType().isFirstPerson()
			|| mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return;
		}
		g.nextStratum();
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		boolean onTarget = targetColor.on() && mc.crosshairPickEntity != null;
		targetFade.animateTo(onTarget ? 1f : 0f);
		float extra = 0f;
		if (dynamicSpread.on()) {
			double speed = player.getDeltaMovement().horizontalDistance();
			spread.setTarget((float) Math.min(4.0, speed * 12.0 + (player.onGround() ? 0 : 1.5)));
			extra = spread.update();
		}
		draw(g, cx, cy, 1f, extra);
		if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
			float strength = player.getAttackStrengthScale(0f);
			if (strength < 1f) {
				float w = 12f;
				float y = cy + gap.f() + size.f() + 4f;
				Draw.rect(g, cx - w / 2f, y, w, 1.5f, 0.75f, 0x60000000);
				Draw.rect(g, cx - w / 2f, y, w * strength, 1.5f, 0.75f, Colors.fade(color.argb(), 0.9f));
			}
		}
	}

	/** Draws the crosshair centred on cx, cy. Also used for the live preview in the menu. */
	public void draw(GuiGraphics g, float cx, float cy, float scale, float extraGap) {
		float p = pulse.get();
		int base = Colors.lerp(color.argb(), targetColorValue.argb(), targetFade.get());
		int c = Colors.lerp(base, 0xFFFFFFFF, p * 0.35f);
		float t = thickness.f() * scale;
		float len = size.f() * scale;
		float gp = (gap.f() + extraGap + p * 2f) * scale;
		int shade = outline.on() ? 0x90000000 : 0;
		switch (style.get()) {
			case DOT -> {
				float r = Math.max(1f, t);
				if (shade != 0) {
					Draw.circle(g, cx, cy, r + 0.75f * scale, shade);
				}
				Draw.circle(g, cx, cy, r, c);
			}
			case CIRCLE -> {
				float r = gp + len / 2f + t;
				if (shade != 0) {
					Draw.arc(g, cx, cy, r + 0.5f * scale, t + scale, 0f, 1f, shade);
					Draw.circle(g, cx, cy, t * 0.6f + 0.5f * scale, shade);
				}
				Draw.arc(g, cx, cy, r, t, 0f, 1f, c);
				Draw.circle(g, cx, cy, t * 0.6f, c);
			}
			case PLUS -> {
				float half = gp + len;
				bar(g, cx - half, cy - t / 2f, half * 2, t, c, shade, scale);
				bar(g, cx - t / 2f, cy - half, t, half * 2, c, shade, scale);
			}
			default -> {
				bar(g, cx - t / 2f, cy - gp - len, t, len, c, shade, scale);
				bar(g, cx - t / 2f, cy + gp, t, len, c, shade, scale);
				bar(g, cx - gp - len, cy - t / 2f, len, t, c, shade, scale);
				bar(g, cx + gp, cy - t / 2f, len, t, c, shade, scale);
			}
		}
	}

	/** A patch of sky and grass with the crosshair on it, so changes show as you make them. */
	@Override
	public void drawPreview(GuiGraphics g, float x, float y, float w, float h) {
		float radius = Math.min(6f, JacConfig.get().style.cornerRadius);
		Draw.verticalGradient(g, x, y, w, h, radius, 0xFF7FB2F0, 0xFFC4DCF5);
		// Clip a full-size rounded rectangle so only the bottom corners of the grass are rounded.
		g.enableScissor((int) x, Math.round(y + h * 0.64f), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
		Draw.verticalGradient(g, x, y, w, h, radius, 0xFF5E9E3E, 0xFF4A7F31);
		g.disableScissor();
		draw(g, x + w / 2f, y + h / 2f, 1.6f, 0f);
	}

	private static void bar(GuiGraphics g, float x, float y, float w, float h, int color, int shade, float scale) {
		float r = Math.min(w, h) / 2f;
		if (shade != 0) {
			float o = 0.6f * scale;
			Draw.rect(g, x - o, y - o, w + o * 2, h + o * 2, r + o, shade);
		}
		Draw.rect(g, x, y, w, h, r, color);
	}
}
