package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ColorSetting;
import dev.justaclient.feature.setting.NumberSetting;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Recolours the enchantment glint and changes how fast it moves, with a separate colour for armor. The glint pattern
 * comes from the active resource pack and is only tinted.
 */
public final class GlintFeature extends Feature {
	private static final Identifier ITEM = Justaclient.id("textures/misc/glint_item");
	private static final Identifier ARMOR = Justaclient.id("textures/misc/glint_armor");

	private final ColorSetting itemColor = color("item_color", "Item colour", ColorSetting.ACCENT);
	private final BoolSetting sameForArmor = bool("same_for_armor", "Same colour on armor", true);
	private final ColorSetting armorColor = color("armor_color", "Armor colour", "#8B5CF6").visibleWhen(() -> !sameForArmor.on());
	private final NumberSetting brightness = number("brightness", "Brightness", 1.2, 0.4, 2.5, 0.05, NumberSetting.percent());
	private final NumberSetting speed = number("speed", "Speed", 1, 0.1, 4, 0.05, NumberSetting.decimal("x"));
	private final Tinted item = new Tinted(ITEM, ItemRenderer.ENCHANTED_GLINT_ITEM);
	private final Tinted armor = new Tinted(ARMOR, ItemRenderer.ENCHANTED_GLINT_ARMOR);

	public GlintFeature() {
		super("glint", "Enchant glint", "Recolour the enchantment shimmer and change its speed", Category.WORLD, Icons.STAR, false);
	}

	@Override
	protected void onEnable() {
		item.dirty = true;
		armor.dirty = true;
	}

	/** Swaps vanilla's glint texture for the tinted copy. Called whenever a render type binds its textures. */
	public Identifier texture(Identifier id) {
		if (!isEnabled()) {
			return id;
		}
		if (id.equals(ItemRenderer.ENCHANTED_GLINT_ITEM)) {
			return item.get(itemColor.argb(), brightness.f());
		}
		if (id.equals(ItemRenderer.ENCHANTED_GLINT_ARMOR)) {
			return armor.get(sameForArmor.on() ? itemColor.argb() : armorColor.argb(), brightness.f());
		}
		return id;
	}

	public double speed(double vanilla) {
		return isEnabled() ? vanilla * speed.get() : vanilla;
	}

	/** One tinted copy of a vanilla glint texture, rebuilt when its colour changes. */
	private static final class Tinted {
		final Identifier id;
		final Identifier source;
		@Nullable NativeImage original;
		@Nullable DynamicTexture texture;
		int lastColor;
		float lastBrightness = -1f;
		boolean dirty = true;
		boolean failed;

		Tinted(Identifier id, Identifier source) {
			this.id = id;
			this.source = source;
		}

		Identifier get(int color, float brightness) {
			if (failed) {
				return source;
			}
			if (dirty || original == null) {
				dirty = false;
				if (!load()) {
					return source;
				}
				lastBrightness = -1f;
			}
			if (texture == null || color != lastColor || brightness != lastBrightness) {
				tint(color, brightness);
			}
			return id;
		}

		private boolean load() {
			try (InputStream in = Minecraft.getInstance().getResourceManager().open(source)) {
				if (original != null) {
					original.close();
				}
				original = NativeImage.read(in);
				return true;
			} catch (IOException e) {
				Justaclient.LOGGER.warn("Could not read {} for the glint tint", source, e);
				failed = true;
				return false;
			}
		}

		private void tint(int color, float brightness) {
			lastColor = color;
			lastBrightness = brightness;
			NativeImage src = original;
			int w = src.getWidth();
			int h = src.getHeight();
			NativeImage out = new NativeImage(w, h, false);
			float cr = ((color >> 16) & 0xFF) / 255f, cg = ((color >> 8) & 0xFF) / 255f, cb = (color & 0xFF) / 255f;
			for (int py = 0; py < h; py++) {
				for (int px = 0; px < w; px++) {
					int p = src.getPixel(px, py);
					// The glint is blended additively by colour, so its brightness is all that matters.
					float lum = Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF)) / 255f * brightness;
					int r = Math.min(255, Math.round(cr * lum * 255f));
					int g = Math.min(255, Math.round(cg * lum * 255f));
					int b = Math.min(255, Math.round(cb * lum * 255f));
					out.setPixelABGR(px, py, 0xFF000000 | b << 16 | g << 8 | r);
				}
			}
			if (texture == null) {
				texture = new LinearTexture(out);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			} else {
				texture.setPixels(out);
				texture.upload();
			}
		}
	}

	/** Vanilla's glint is sampled smoothly and repeats; a plain dynamic texture would be pixelated. */
	private static final class LinearTexture extends DynamicTexture {
		LinearTexture(NativeImage image) {
			super(() -> "Justaclient glint", image);
			this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
		}
	}
}
