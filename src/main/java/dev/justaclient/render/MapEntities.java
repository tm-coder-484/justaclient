package dev.justaclient.render;

import dev.justaclient.anim.Colors;
import dev.justaclient.render.text.JacText;
import dev.justaclient.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

/**
 * Players and mobs for the minimap and the full map. Only what the player could see from where they stand: an entity
 * shows when it is in direct line of sight and not invisible, so the maps never reveal anything behind walls. Worked out
 * once per tick and shared by both maps. Fish are left out; a sea of dots hides everything else.
 */
public final class MapEntities {
	/** Vanilla's line-of-sight check gives up past this many blocks anyway. */
	public static final double RANGE = 128.0;
	private static final int MAX = 96;
	private static final List<LivingEntity> visible = new ArrayList<>();
	private static long builtTick = Long.MIN_VALUE;
	private static @Nullable ClientLevel builtLevel;

	private MapEntities() {
	}

	/** Living entities the player can see, nearest first. */
	public static List<LivingEntity> visible() {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			visible.clear();
			return visible;
		}
		long tick = level.getGameTime();
		if (tick == builtTick && level == builtLevel) {
			return visible;
		}
		builtTick = tick;
		builtLevel = level;
		visible.clear();
		for (Entity e : level.entitiesForRendering()) {
			if (e == player || !(e instanceof LivingEntity living) || e instanceof ArmorStand || e instanceof AbstractFish || !living.isAlive() || e.isSpectator()
				|| e.isInvisibleTo(player)) {
				continue;
			}
			double dx = e.getX() - player.getX();
			double dz = e.getZ() - player.getZ();
			if (dx * dx + dz * dz > RANGE * RANGE) {
				continue;
			}
			visible.add(living);
		}
		visible.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		// Line of sight is a raycast, so only test the nearest few.
		List<LivingEntity> seen = new ArrayList<>();
		for (LivingEntity e : visible) {
			if (seen.size() >= MAX) {
				break;
			}
			if (player.hasLineOfSight(e)) {
				seen.add(e);
			}
		}
		visible.clear();
		visible.addAll(seen);
		return visible;
	}

	public static int color(Entity e) {
		if (e instanceof AbstractClientPlayer) {
			return 0xFFFFFFFF;
		}
		if (e instanceof Enemy) {
			return 0xFFF87171;
		}
		if (e instanceof NeutralMob) {
			return 0xFFFBBF24;
		}
		return 0xFF34D399;
	}

	/** A dot, or a player's face, centred on ({@code x}, {@code y}). */
	public static void draw(GuiGraphics g, LivingEntity e, float x, float y, float r, float alpha, boolean hovered) {
		float size = hovered ? r * 1.35f : r;
		if (e instanceof AbstractClientPlayer p) {
			float face = size * 2f;
			Draw.rect(g, x - face / 2f - 0.8f, y - face / 2f - 0.8f, face + 1.6f, face + 1.6f, 1.2f, Colors.fade(0xFF101218, alpha));
			g.pose().pushMatrix();
			g.pose().translate(x - face / 2f, y - face / 2f);
			g.pose().scale(face / 8f, face / 8f);
			PlayerFaceRenderer.draw(g, p.getSkin(), 0, 0, 8, Colors.withAlpha(0xFFFFFF, Math.round(alpha * 255)));
			g.pose().popMatrix();
		} else {
			Draw.circle(g, x, y, size + 0.8f, Colors.fade(0xFF101218, alpha));
			Draw.circle(g, x, y, size, Colors.fade(color(e), alpha));
		}
		if (hovered) {
			Draw.outline(g, x - size - 2f, y - size - 2f, size * 2 + 4f, size * 2 + 4f, size + 2f, 0.8f, Colors.fade(0xFFFFFFFF, alpha));
		}
	}

	/** Name, health and distance in a small tag above ({@code x}, {@code y}). */
	public static void tooltip(GuiGraphics g, LivingEntity e, float x, float y, float alpha) {
		LocalPlayer player = Minecraft.getInstance().player;
		String name = e.getDisplayName().getString();
		String detail = Math.round(e.getHealth()) + " / " + Math.round(e.getMaxHealth()) + " hp";
		if (player != null) {
			detail += "  ·  " + Math.round(Math.sqrt(e.distanceToSqr(player))) + " m";
		}
		float nw = JacText.width(name, Theme.SMALL, JacText.Weight.SEMIBOLD);
		float dw = JacText.width(detail, Theme.SMALL, JacText.Weight.REGULAR);
		float w = Math.max(nw, dw) + 10f;
		float h = 21f;
		float left = x - w / 2f;
		float top = y - h - 7f;
		Draw.rect(g, left, top, w, h, 4f, Colors.fade(0xE0101218, alpha));
		Draw.rect(g, left, top, 2f, h, 1f, Colors.fade(color(e), alpha));
		JacText.draw(g, name, left + (w - nw) / 2f, top + 2.5f, Colors.fade(Theme.TEXT, alpha), Theme.SMALL, JacText.Weight.SEMIBOLD);
		JacText.draw(g, detail, left + (w - dw) / 2f, top + 11.5f, Colors.fade(Theme.TEXT_DIM, alpha), Theme.SMALL, JacText.Weight.REGULAR);
	}
}
