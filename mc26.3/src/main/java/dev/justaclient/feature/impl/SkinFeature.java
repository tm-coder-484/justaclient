package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.TextSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Wears a skin from a PNG file on your own player, on your screen only. Put 64 x 64 skin files in
 * {@code config/justaclient/skins/} and type the file name.
 */
public final class SkinFeature extends Feature {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("skins");
	private static final Identifier TEXTURE = Justaclient.id("skin/custom");

	public enum Model implements ChoiceSetting.Named {
		WIDE("Wide arms"), SLIM("Slim arms");

		private final String label;

		Model(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final TextSetting file = add(new TextSetting("file", "Skin file", "", 64, "name.png in config/justaclient/skins"));
	private final ChoiceSetting<Model> model = choice("model", "Model", Model.WIDE);
	private @Nullable DynamicTexture texture;
	private String loaded = "";
	private long loadedStamp;
	private boolean ready;

	public SkinFeature() {
		super("skin", "Skin swap", "Wear a skin from a PNG file, seen only by you", Category.WORLD, Icons.SHIRT, false);
	}

	@Override
	protected void onEnable() {
		try {
			Files.createDirectories(DIR);
		} catch (IOException e) {
			Justaclient.LOGGER.debug("Could not create {}", DIR, e);
		}
		loaded = "";
	}

	/** Reloads when the file name or the file itself changes, so editing the PNG shows straight away. */
	@Override
	public void tick() {
		String name = file.get().trim();
		if (name.isEmpty()) {
			ready = false;
			loaded = "";
			return;
		}
		if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
			name += ".png";
		}
		Path path = DIR.resolve(name).normalize();
		if (!path.startsWith(DIR) || !Files.isRegularFile(path)) {
			if (!name.equals(loaded)) {
				loaded = name;
				ready = false;
				Notifications.push(Icons.SHIRT, "Skin swap", "no " + name + " in config/justaclient/skins");
			}
			return;
		}
		long stamp;
		try {
			stamp = Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return;
		}
		if (name.equals(loaded) && stamp == loadedStamp) {
			return;
		}
		loaded = name;
		loadedStamp = stamp;
		try (InputStream in = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(in);
			if (image.getWidth() != 64 || image.getHeight() != 64) {
				image.close();
				ready = false;
				Notifications.push(Icons.SHIRT, "Skin swap", name + " must be 64 x 64");
				return;
			}
			if (texture != null) {
				texture.setPixels(image);
				texture.upload();
			} else {
				texture = new DynamicTexture(() -> "Justaclient skin", image);
				Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
			}
			ready = true;
		} catch (IOException | RuntimeException e) {
			ready = false;
			Notifications.push(Icons.SHIRT, "Skin swap", "could not read " + name);
		}
	}

	/** Your skin with the custom texture swapped in, or the real one. */
	public static PlayerSkin apply(PlayerSkin real) {
		SkinFeature f = Features.get(SkinFeature.class);
		if (f == null || !f.isEnabled() || !f.ready) {
			return real;
		}
		PlayerModelType type = f.model.get() == Model.SLIM ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		return new PlayerSkin(new ClientAsset.DownloadedTexture(TEXTURE, "justaclient:custom"), real.cape(), real.elytra(), type, false);
	}
}
