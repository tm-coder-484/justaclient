package dev.justaclient.render.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.justaclient.Justaclient;
import dev.justaclient.config.JacConfig;
import dev.justaclient.config.StyleTokens;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.impl.NameProtectFeature;
import dev.justaclient.render.Icons;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Text drawing for the whole client. Sizes are the font's em size in GUI pixels (vanilla text is roughly 8).
 * {@code y} is the top of the line box. When the player picks the vanilla font, this falls back to Minecraft's renderer.
 */
public final class JacText {
	public enum Weight {
		REGULAR("inter-regular"),
		SEMIBOLD("inter-semibold");

		final String atlas;

		Weight(String atlas) {
			this.atlas = atlas;
		}
	}

	private static final Map<Weight, MsdfFont> FONTS = new EnumMap<>(Weight.class);
	private static @Nullable MsdfFont icons;

	private JacText() {
	}

	public static void draw(GuiGraphics g, String text, float x, float y, int color, float size, Weight weight) {
		if ((color >>> 24) == 0 || text.isEmpty()) {
			return;
		}
		if (useVanilla()) {
			drawVanilla(g, text, x, y, color, size, weight);
			return;
		}
		submit(g, font(weight), protect(text), x, y, color, size);
	}

	/** Vanilla text goes through the font mixin; MSDF text gets name protect here. */
	private static String protect(String text) {
		NameProtectFeature names = Features.get(NameProtectFeature.class);
		return names == null ? text : names.apply(text);
	}

	/** Draws a Lucide icon in a {@code size} by {@code size} box with its top-left at x, y. Icons ignore the font setting. */
	public static void icon(GuiGraphics g, Icons icon, float x, float y, float size, int color) {
		if ((color >>> 24) == 0) {
			return;
		}
		if (icons == null) {
			icons = MsdfFont.load(Justaclient.id("lucide"));
		}
		submit(g, icons, icon.glyph, x, y, color, size);
	}

	private static void submit(GuiGraphics g, MsdfFont font, String text, float x, float y, int color, float size) {
		float scale = size / font.emSize;
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle area = new ScreenRectangle(
			(int) Math.floor(x), (int) Math.floor(y),
			(int) Math.ceil(font.width(text) * scale) + 2, (int) Math.ceil(font.lineHeight * scale) + 2
		).transformMaxBounds(pose);
		ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
		AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(font.texture);
		TextureSetup textures = TextureSetup.singleTexture(atlas.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		g.guiRenderState.submitGuiElement(new MsdfTextRenderState(font, text, textures, pose, x, y, scale, color, scissor, bounds));
	}

	public static void drawCentered(GuiGraphics g, String text, float centerX, float y, int color, float size, Weight weight) {
		draw(g, text, centerX - width(text, size, weight) / 2f, y, color, size, weight);
	}

	public static float width(String text, float size, Weight weight) {
		if (useVanilla()) {
			return Minecraft.getInstance().font.width(text) * size / 8f;
		}
		MsdfFont font = font(weight);
		return font.width(protect(text)) * size / font.emSize;
	}

	/** Height of one line of text at {@code size}. */
	public static float lineHeight(float size) {
		if (useVanilla()) {
			return Minecraft.getInstance().font.lineHeight * size / 8f;
		}
		MsdfFont font = font(Weight.REGULAR);
		return font.lineHeight * size / font.emSize;
	}

	private static boolean useVanilla() {
		return JacConfig.get().style.font == StyleTokens.FontChoice.VANILLA;
	}

	private static void drawVanilla(GuiGraphics g, String text, float x, float y, int color, float size, Weight weight) {
		Font font = Minecraft.getInstance().font;
		float scale = size / 8f;
		// Centre vanilla's 9px line inside the Inter line box so layouts line up when switching fonts.
		float offset = (lineHeight(size) - font.lineHeight * scale) / 2f;
		g.pose().pushMatrix();
		g.pose().translate(x, y + offset);
		g.pose().scale(scale, scale);
		Component line = weight == Weight.SEMIBOLD ? Component.literal(text).withStyle(ChatFormatting.BOLD) : Component.literal(text);
		g.drawString(font, line, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private static MsdfFont font(Weight weight) {
		return FONTS.computeIfAbsent(weight, w -> MsdfFont.load(Justaclient.id(w.atlas)));
	}

	/** Drops cached fonts so they reload from the current resource packs. */
	public static void clearCache() {
		FONTS.clear();
		icons = null;
	}
}
