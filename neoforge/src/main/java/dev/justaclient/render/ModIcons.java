package dev.justaclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.common.mods.ManagedMod;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import dev.justaclient.platform.PlatformLoader;
import dev.justaclient.platform.PlatformLoader.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Mod icons for the Mods panel, read from each mod's jar (loaded or switched off) and kept as textures until the panel
 * closes. Mods without an icon get a package glyph.
 */
public final class ModIcons {
	private static final Map<String, @Nullable ResourceLocation> LOADED = new HashMap<>();
	private static int next;

	private ModIcons() {
	}

	/** Draws {@code mod}'s icon in a {@code size} square. */
	public static void draw(GuiGraphics g, ManagedMod mod, float x, float y, float size, float alpha) {
		ResourceLocation id = LOADED.computeIfAbsent(mod.key(), k -> load(mod));
		if (id == null) {
			Draw.rect(g, x, y, size, size, Math.min(4f, size / 4f), dev.justaclient.anim.Colors.fade(0x1FFFFFFF, alpha));
			float s = size * 0.55f;
			dev.justaclient.render.text.JacText.icon(g, Icons.PACKAGE, x + (size - s) / 2f, y + (size - s) / 2f, s,
				dev.justaclient.anim.Colors.fade(0xFF9AA0AE, alpha));
			return;
		}
		int color = ((Math.round(alpha * 255) & 0xFF) << 24) | 0xFFFFFF;
		int s = Math.round(size);
		dev.justaclient.render.LegacyImages.blit(g, id, Math.round(x), Math.round(y), 0f, 0f, s, s, s, s, color);
	}

	private static @Nullable ResourceLocation load(ManagedMod mod) {
		if (mod.icon() == null) {
			return null;
		}
		try {
			BufferedImage image = read(mod);
			if (image == null) {
				return null;
			}
			int w = image.getWidth();
			int h = image.getHeight();
			SmoothTexture tex = new SmoothTexture(() -> "Mod icon " + mod.id(), w, h);
			NativeImage pixels = tex.getPixels();
			if (pixels == null) {
				return null;
			}
			for (int py = 0; py < h; py++) {
				for (int px = 0; px < w; px++) {
					dev.justaclient.render.LegacyImages.pixel(pixels, px, py, image.getRGB(px, py));
				}
			}
			tex.upload();
			ResourceLocation id = Justaclient.id("mod_icon/" + (next++) + "_" + mod.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
			Minecraft.getInstance().getTextureManager().register(id, tex);
			return id;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static @Nullable BufferedImage read(ManagedMod mod) throws IOException {
		String icon = mod.icon();
		if (mod.loaded()) {
			Optional<ModContainer> container = PlatformLoader.getInstance().getModContainer(mod.id());
			Optional<Path> path = container.flatMap(c -> c.findPath(icon));
			if (path.isPresent() && Files.isRegularFile(path.get())) {
				try (InputStream in = Files.newInputStream(path.get())) {
					return ImageIO.read(in);
				}
			}
			return null;
		}
		if (mod.file() == null) {
			return null;
		}
		try (ZipFile zip = new ZipFile(mod.file().toFile())) {
			ZipEntry entry = zip.getEntry(icon.startsWith("/") ? icon.substring(1) : icon);
			if (entry == null) {
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return ImageIO.read(in);
			}
		}
	}

	/** Frees every icon texture. */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (ResourceLocation id : LOADED.values()) {
			if (id != null) {
				mc.getTextureManager().release(id);
			}
		}
		LOADED.clear();
	}
}
