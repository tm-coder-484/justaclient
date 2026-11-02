package dev.justaclient.hud.widgets;

import dev.justaclient.anim.Animation;
import dev.justaclient.anim.Colors;
import dev.justaclient.anim.Easing;
import dev.justaclient.anim.Spring;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.impl.CombatTracker;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.hud.HudFeature;
import dev.justaclient.render.Draw;
import dev.justaclient.render.Icons;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * The entity you are fighting: face, name and health. Health of a mob or player you hit is already shown by vanilla's hurt
 * flash and death; this only shows it more clearly, and only for the entity you hit yourself.
 */
public final class TargetHud extends HudFeature {
	private static final float FACE = 26f;
	private static final float W = 132f;

	private final BoolSetting showModel = bool("model", "3D model for mobs", true);
	private final Animation visible = new Animation(Easing.CUBIC_OUT, 200, 0f);
	private final Spring health = Spring.ui(1f);
	/** Lags behind the health bar so a hit shows as a white chunk that drains away. */
	private final Spring ghost = new Spring(40f, 12f, 1f);
	private @Nullable LivingEntity shown;

	public TargetHud() {
		super("target_hud", "Target HUD", "Face, name and health of what you are fighting", Icons.SWORDS, true, 0.66f, 0.6f);
	}

	@Override
	public void layout(boolean preview) {
		width = W;
		height = PAD * 2 + FACE;
		if (preview) {
			return;
		}
		LivingEntity target = CombatTracker.target();
		if (target != null && target != shown) {
			float pct = pct(target);
			health.snap(pct);
			ghost.snap(pct);
		}
		if (target != null) {
			shown = target;
		}
		visible.animateTo(target != null ? 1f : 0f);
		if (target == null && visible.isDone()) {
			shown = null;
		}
	}

	@Override
	public boolean hasContent() {
		return shown != null;
	}

	private static float pct(LivingEntity e) {
		return Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
	}

	@Override
	public void draw(GuiGraphicsExtractor g, boolean preview) {
		LivingEntity e = preview ? null : shown;
		float a = preview ? 1f : visible.get();
		if (a <= 0.01f) {
			return;
		}
		g.pose().pushMatrix();
		// Slight scale-in on appear.
		float s = 0.94f + 0.06f * a;
		g.pose().translate(width / 2f * (1 - s), height / 2f * (1 - s));
		g.pose().scale(s, s);
		if (background.on()) {
			Draw.panel(g, 0, 0, width, height, a);
		}
		String name = preview ? "Target name" : e.getName().getString();
		float hp = preview ? 16f : e.getHealth();
		float max = preview ? 20f : e.getMaxHealth();
		float pct;
		if (preview) {
			pct = 0.8f;
		} else {
			health.setTarget(pct(e));
			pct = Math.max(0f, Math.min(1f, health.update()));
		}
		ghost.setTarget(pct);
		float ghostPct = preview ? 0.9f : Math.max(pct, ghost.update());

		float radius = Math.min(5f, JacConfig.get().style.cornerRadius);
		Draw.rect(g, PAD, PAD, FACE, FACE, radius, Colors.fade(Theme.CONTROL, a));
		drawFace(g, e, PAD, PAD, a);
		if (e != null && e.hurtTime > 0) {
			Draw.rect(g, PAD, PAD, FACE, FACE, radius, Colors.fade(0x60F43F5E, e.hurtTime / 10f * a));
		}

		float x = PAD + FACE + 6f;
		float right = width - PAD;
		String value = String.format("%.1f", hp);
		float valueW = JacText.width(value, TEXT + 0.5f, JacText.Weight.SEMIBOLD);
		text(g, fit(name, right - x - valueW - 6f), x, PAD + 1f, Colors.fade(Theme.TEXT, a), TEXT + 0.5f, JacText.Weight.SEMIBOLD);
		int hpColor = ArmorHud.healthColor(pct);
		textRight(g, value, right, PAD + 1f, Colors.fade(Colors.lerp(hpColor, Theme.TEXT, 0.25f), a), TEXT + 0.5f, JacText.Weight.SEMIBOLD);

		float barY = PAD + 12f;
		float barW = right - x;
		Draw.rect(g, x, barY, barW, 3.5f, 1.75f, Colors.fade(Theme.CONTROL, a));
		Draw.rect(g, x, barY, Math.max(3.5f, barW * ghostPct), 3.5f, 1.75f, Colors.fade(0x90FFFFFF, a));
		Draw.horizontalGradient(g, x, barY, Math.max(3.5f, barW * pct), 3.5f, 1.75f, Colors.fade(JacConfig.accentAt(0f), a),
			Colors.fade(JacConfig.accentAt(1f), a));
		String detail = "Health " + Math.round(hp) + " / " + Math.round(max);
		text(g, detail, x, PAD + 19f, Colors.fade(Theme.TEXT_DIM, a), TEXT - 0.5f, JacText.Weight.REGULAR);
		g.pose().popMatrix();
	}

	private void drawFace(GuiGraphicsExtractor g, @Nullable LivingEntity e, float x, float y, float a) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity who = e != null ? e : mc.player;
		if (who instanceof AbstractClientPlayer player) {
			float inset = 3f;
			g.pose().pushMatrix();
			g.pose().translate(x + inset, y + inset);
			float size = FACE - inset * 2;
			g.pose().scale(size / 8f, size / 8f);
			PlayerFaceExtractor.extractRenderState(g, player.getSkin(), 0, 0, 8, Colors.withAlpha(0xFFFFFF, Math.round(a * 255)));
			g.pose().popMatrix();
		} else if (who != null && showModel.on() && a > 0.95f) {
			// Entity models are drawn picture-in-picture at absolute screen coordinates, so map the box through the pose.
			Vector2f tl = g.pose().transformPosition(x, y, new Vector2f());
			Vector2f br = g.pose().transformPosition(x + FACE, y + FACE, new Vector2f());
			float boxH = br.y - tl.y;
			float size = Math.max(who.getBbHeight(), who.getBbWidth());
			int scale = Math.max(1, Math.round(boxH * 0.8f / Math.max(0.5f, size)));
			int x0 = Math.round(tl.x), y0 = Math.round(tl.y), x1 = Math.round(br.x), y1 = Math.round(br.y);
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0625f, (x0 + x1) / 2f, (y0 + y1) / 2f, who);
		} else {
			JacText.icon(g, Icons.USER, x + FACE / 2f - 6f, y + FACE / 2f - 6f, 12f, Colors.fade(Theme.TEXT_DIM, a));
		}
	}

	private static String fit(String text, float maxWidth) {
		if (JacText.width(text, TEXT + 0.5f, JacText.Weight.SEMIBOLD) <= maxWidth) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && JacText.width(s + "...", TEXT + 0.5f, JacText.Weight.SEMIBOLD) > maxWidth) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "...";
	}
}
