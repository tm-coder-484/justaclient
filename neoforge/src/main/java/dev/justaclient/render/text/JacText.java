package dev.justaclient.render.text;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.justaclient.render.JacPipelines;
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

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
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
        g.flush();
        float scale = size / font.emSize;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> JacPipelines.MSDF_TEXT);
        var atlas=Minecraft.getInstance().getTextureManager().getTexture(font.texture);
        atlas.setFilter(true,false);
        RenderSystem.setShaderTexture(0,font.texture);
        var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f pose=g.pose().last().pose();
        float penX=x; int previous=-1, vertices=0;
        for (int i=0;i<text.length();) {
            int cp=text.codePointAt(i); i+=Character.charCount(cp);
            if (previous>=0) penX+=font.kerning(previous,cp)*scale;
            var glyph=font.glyph(cp);
            if (glyph.width()>0 && glyph.height()>0) {
                float x0=penX+glyph.xOffset()*scale, y0=y+glyph.yOffset()*scale;
                float x1=x0+glyph.width()*scale, y1=y0+glyph.height()*scale;
                float u0=glyph.x()/font.atlasWidth,v0=glyph.y()/font.atlasHeight;
                float u1=(glyph.x()+glyph.width())/font.atlasWidth,v1=(glyph.y()+glyph.height())/font.atlasHeight;
                b.addVertex(pose,x0,y0,0).setUv(u0,v0).setColor(color);
                b.addVertex(pose,x0,y1,0).setUv(u0,v1).setColor(color);
                b.addVertex(pose,x1,y1,0).setUv(u1,v1).setColor(color);
                b.addVertex(pose,x1,y0,0).setUv(u1,v0).setColor(color);
                vertices+=4;
            }
            penX+=glyph.advance()*scale; previous=cp;
        }
        var mesh=b.build(); if (mesh!=null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableDepthTest();
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
		g.pose().pushPose();
		g.pose().translate(x, y + offset, 0);
		g.pose().scale(scale, scale, 1);
		Component line = weight == Weight.SEMIBOLD ? Component.literal(text).withStyle(ChatFormatting.BOLD) : Component.literal(text);
		g.drawString(font, line, 0, 0, color, false);
		g.pose().popPose();
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
