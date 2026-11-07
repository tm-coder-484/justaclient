package dev.justaclient.feature.impl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.common.cosmetics.CapeLayout;
import dev.justaclient.config.JacConfig;
import dev.justaclient.feature.Category;
import dev.justaclient.feature.Feature;
import dev.justaclient.feature.Features;
import dev.justaclient.feature.setting.BoolSetting;
import dev.justaclient.feature.setting.ChoiceSetting;
import dev.justaclient.feature.setting.FileSetting;
import dev.justaclient.hud.Notifications;
import dev.justaclient.render.Icons;
import dev.justaclient.render.ImageBackground;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Your own cape from a PNG or GIF (animated GIFs play), drawn on your player on your screen only; nobody else sees it.
 * Cape textures (64 x 32, or OptiFine's 46 x 22) are used as they are, and any other picture is fitted onto the cape.
 * It can also go on your elytra.
 */
public final class CapeFeature extends Feature {
	public static final Path DIR = PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("capes");
	private static final int MAX_FRAMES = 120;

	public enum Source implements ChoiceSetting.Named {
		THEME("Theme colours"), FILE("Picture file");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final ChoiceSetting<Source> source = choice("source", "Cape", Source.THEME);
	private final FileSetting file = add(new FileSetting("file", "Picture", () -> DIR, List.of(".png", ".gif", ".jpg", ".jpeg"),
		"First file in config/justaclient/capes")).visibleWhen(() -> source.get() == Source.FILE);
	private final BoolSetting elytra = bool("elytra", "Also on elytra", true);
	private final List<ResourceLocation> ids = new ArrayList<>();
	private final List<DynamicTexture> textures = new ArrayList<>();
	private int[] delays = new int[0];
	private int totalMs;
	private String loadedKey = "";
	private @Nullable CompletableFuture<List<CapeLayout.Texture>> pending;
	private int[] pendingDelays = new int[0];
	private int checkIn;

	public CapeFeature() {
		super("custom_cape", "Custom cape", "Your own PNG or GIF cape, seen only by you", Category.WORLD, Icons.FLAG, false);
	}

	public static CapeFeature get() {
		return Features.get(CapeFeature.class);
	}

	@Override
	protected void onEnable() {
		try {
			Files.createDirectories(DIR);
		} catch (IOException e) {
			Justaclient.LOGGER.debug("Could not create {}", DIR, e);
		}
		loadedKey = "";
	}

	@Override
	protected void onDisable() {
		release();
		loadedKey = "";
		pending = null;
	}

	/** What should be on the cape now: the theme, or the file and when it last changed. */
	private String wantedKey(@Nullable Path path) {
		if (source.get() == Source.THEME) {
			return "theme:" + Integer.toHexString(JacConfig.accentAt(0f)) + Integer.toHexString(JacConfig.accentAt(1f));
		}
		if (path == null) {
			return "none";
		}
		try {
			return path + "@" + Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return "none";
		}
	}

	@Override
	public void tick() {
		poll();
		if (--checkIn > 0) {
			return;
		}
		checkIn = 20;
		Path path = source.get() == Source.FILE ? file.resolve() : null;
		String key = wantedKey(path);
		if (key.equals(loadedKey)) {
			return;
		}
		loadedKey = key;
		if (source.get() == Source.THEME) {
			int[] pic = themePicture();
			pendingDelays = new int[] {1000};
			pending = CompletableFuture.completedFuture(List.of(CapeLayout.build(pic, 40, 64)));
			return;
		}
		if (path == null) {
			release();
			Notifications.push(Icons.FLAG, "Custom cape", "put a PNG or GIF in config/justaclient/capes");
			return;
		}
		Path chosen = path;
		pending = CompletableFuture.supplyAsync(() -> {
			try {
				ImageBackground.Frames frames = ImageBackground.frames(chosen, 1024);
				List<CapeLayout.Texture> out = new ArrayList<>();
				int n = Math.min(MAX_FRAMES, frames.argb().size());
				int[] d = new int[n];
				for (int i = 0; i < n; i++) {
					out.add(CapeLayout.build(frames.argb().get(i), frames.width(), frames.height()));
					d[i] = frames.delays()[i];
				}
				pendingDelays = d;
				return out;
			} catch (IOException e) {
				throw new IllegalStateException(e.getMessage() == null ? "unreadable" : e.getMessage(), e);
			}
		});
	}

	/** A vertical gradient in the theme accent, with a soft band of light across it. */
	private static int[] themePicture() {
		int w = 40;
		int h = 64;
		int[] out = new int[w * h];
		for (int y = 0; y < h; y++) {
			int base = JacConfig.accentAt(y / (float) (h - 1));
			for (int x = 0; x < w; x++) {
				float band = (float) Math.max(0, 1 - Math.abs((y - h * 0.35f) - (x - w / 2f) * 0.6f) / 6f) * 0.25f;
				int r = Math.min(255, (int) ((base >> 16 & 0xFF) * (1 - band) + 255 * band));
				int g = Math.min(255, (int) ((base >> 8 & 0xFF) * (1 - band) + 255 * band));
				int b = Math.min(255, (int) ((base & 0xFF) * (1 - band) + 255 * band));
				out[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
			}
		}
		return out;
	}

	private void poll() {
		if (pending == null || !pending.isDone()) {
			return;
		}
		CompletableFuture<List<CapeLayout.Texture>> done = pending;
		pending = null;
		List<CapeLayout.Texture> frames;
		try {
			frames = done.join();
		} catch (RuntimeException e) {
			release();
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			Notifications.push(Icons.FLAG, "Custom cape", "could not read the picture: " + cause.getMessage());
			return;
		}
		release();
		Minecraft mc = Minecraft.getInstance();
		delays = pendingDelays;
		totalMs = 0;
		for (int i = 0; i < frames.size(); i++) {
			CapeLayout.Texture t = frames.get(i);
			NativeImage image = new NativeImage(t.width(), t.height(), true);
			for (int y = 0; y < t.height(); y++) {
				for (int x = 0; x < t.width(); x++) {
					dev.justaclient.render.LegacyImages.pixel(image, x, y, t.at(x, y));
				}
			}
			DynamicTexture tex = new DynamicTexture(image);
			ResourceLocation id = Justaclient.id("cape/" + i);
			mc.getTextureManager().register(id, tex);
			textures.add(tex);
			ids.add(id);
			totalMs += i < delays.length ? delays[i] : 100;
		}
	}

	private void release() {
		Minecraft mc = Minecraft.getInstance();
		for (ResourceLocation id : ids) {
			mc.getTextureManager().release(id);
		}
		ids.clear();
		textures.clear();
	}

	private @Nullable ResourceLocation current() {
		if (!isEnabled() || ids.isEmpty()) {
			return null;
		}
		if (ids.size() == 1 || totalMs <= 0) {
			return ids.getFirst();
		}
		long t = System.currentTimeMillis() % totalMs;
		for (int i = 0; i < ids.size(); i++) {
			t -= i < delays.length ? delays[i] : 100;
			if (t < 0) {
				return ids.get(i);
			}
		}
		return ids.getFirst();
	}

	/** Your skin with the cape (and elytra, if set) swapped in, or the real one. */
	public static PlayerSkin apply(PlayerSkin real) {
		CapeFeature f = get();
		ResourceLocation id = f == null ? null : f.current();
		if (id == null) {
			return real;
		}
		ResourceLocation cape = id;
		return new PlayerSkin(real.texture(), real.textureUrl(), cape, f.elytra.on() ? cape : real.elytraTexture(), real.model(), real.secure());
	}

	/** Vanilla puts the cape on elytra when there is no elytra texture; with "Also on elytra" off, ours stays off it. */
	public static @Nullable ResourceLocation elytraTexture(@Nullable ResourceLocation chosen) {
		CapeFeature f = get();
		if (chosen != null && f != null && !f.elytra.on() && f.ids.contains(chosen)) {
			return null;
		}
		return chosen;
	}
}
