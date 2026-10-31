package dev.justaclient.hud;

import dev.justaclient.Justaclient;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.BossBarFeature;
import dev.justaclient.feature.impl.CrosshairFeature;
import dev.justaclient.feature.impl.LowHealthFeature;
import dev.justaclient.feature.impl.ScoreboardFeature;
import dev.justaclient.hud.widgets.PotionsHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the HUD widgets each frame, under the chat, and swaps in the custom crosshair and effect list. */
public final class HudRenderer {
	/** Set by the HUD editor, which draws the widgets itself with outlines and previews. */
	public static boolean editing;
	private static float partialTick;

	private HudRenderer() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Justaclient.id("hud"), HudRenderer::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Justaclient.id("screen_glow"), (g, delta) -> {
			LowHealthFeature glow = Features.get(LowHealthFeature.class);
			if (glow.isEnabled() && !editing) {
				glow.cost.begin();
				glow.render(g);
				glow.cost.end();
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> (g, delta) -> {
			BossBarFeature bars = Features.get(BossBarFeature.class);
			bars.cost.begin();
			boolean drawn = bars.render(g);
			bars.cost.end();
			if (!drawn) {
				vanilla.render(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (g, delta) -> {
			ScoreboardFeature board = Features.get(ScoreboardFeature.class);
			board.cost.begin();
			boolean drawn = board.render(g);
			board.cost.end();
			if (!drawn) {
				vanilla.render(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			CrosshairFeature crosshair = Features.get(CrosshairFeature.class);
			if (crosshair.isEnabled() && crosshair.shouldReplaceVanilla()) {
				crosshair.cost.begin();
				crosshair.renderHud(g, delta);
				crosshair.cost.end();
			} else {
				vanilla.render(g, delta);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (g, delta) -> {
			PotionsHud potions = Features.get(PotionsHud.class);
			if (!potions.isEnabled() || !potions.hideVanilla.on()) {
				vanilla.render(g, delta);
			}
		});
	}

	public static float partialTick() {
		return partialTick;
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		partialTick = delta.getGameTimeDeltaPartialTick(false);
		FrameCost.frame();
		if (editing || Minecraft.getInstance().player == null) {
			return;
		}
		for (HudFeature hud : Features.hud()) {
			if (!hud.isEnabled()) {
				continue;
			}
			hud.cost.begin();
			hud.layout(false);
			if (hud.hasContent()) {
				draw(g, hud, false);
			}
			hud.cost.end();
		}
	}

	/** Draws one widget at its saved position. The caller has already run {@link HudFeature#layout}. */
	public static void draw(GuiGraphics g, HudFeature hud, boolean preview) {
		float scale = hud.scale.f();
		g.pose().pushMatrix();
		g.pose().translate(hud.screenX(g.guiWidth()), hud.screenY(g.guiHeight()));
		g.pose().scale(scale, scale);
		hud.draw(g, preview);
		g.pose().popMatrix();
	}
}
