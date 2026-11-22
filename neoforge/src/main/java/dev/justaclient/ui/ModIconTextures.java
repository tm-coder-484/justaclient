package dev.justaclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.justaclient.Justaclient;
import dev.justaclient.common.modrinth.ModIcons;
import dev.justaclient.feature.impl.ModrinthFeature;
import dev.justaclient.render.SmoothTexture;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.justaclient.platform.PlatformLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Modrinth project icons as textures, shared by every visit to the browser while the game runs. Icons are asked for as
 * they are drawn, fetched and decoded off the render thread by {@link ModIcons}, and uploaded a few per frame. The least
 * recently drawn are freed once there are more than {@link #MAX} of them.
 */
public final class ModIconTextures {
	/** Icons kept as textures (96x96 each, about 36 KB of video memory apiece). */
	private static final int MAX = 240;
	/** How many decoded icons are uploaded per frame, so a page of results never stalls a frame. */
	private static final int UPLOADS_PER_FRAME = 6;
	/** How long an icon that could not be fetched waits before it is tried again. */
	private static final long RETRY_MS = 60_000;

	private static @Nullable ModIcons loader;
	/** By URL, in least recently drawn order. */
	private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>(64, 0.75f, true);
	private static final Queue<Decoded> READY = new ConcurrentLinkedQueue<>();
	private static int nextId;

	private ModIconTextures() {
	}

	/** One icon: still on its way, ready, or given up on (the letter placeholder stays). */
	private static final class Entry {
		@Nullable ResourceLocation texture;
		boolean failed;
		long failedAt;
		/** When the texture arrived, for the fade-in. */
		long readyAt;
	}

	private record Decoded(String url, int[] argb) {
	}

	private static ModIcons loader() {
		if (loader == null) {
			loader = new ModIcons(ModrinthFeature.get().api().userAgent(),
				PlatformLoader.getInstance().getConfigDir().resolve("justaclient").resolve("cache").resolve("modrinth-icons"));
		}
		return loader;
	}

	/** A drawn icon: its texture and how far it has faded in (0 to 1). */
	public record Icon(ResourceLocation texture, float fade) {
	}

	/** The icon for {@code url} if it is ready; asks for it otherwise. Null while loading, on failure, or for no URL. Render thread only. */
	public static @Nullable Icon get(String url) {
		if (url == null || url.isEmpty() || !ModIcons.allowed(url)) {
			return null;
		}
		Entry e = ENTRIES.get(url);
		if (e == null) {
			e = new Entry();
			ENTRIES.put(url, e);
			loader().load(url).whenComplete((argb, err) -> {
				if (err == null && argb != null) {
					READY.add(new Decoded(url, argb));
				} else {
					Minecraft.getInstance().execute(() -> {
						Entry gone = ENTRIES.get(url);
						if (gone != null) {
							gone.failed = true;
							gone.failedAt = System.currentTimeMillis();
						}
						Justaclient.LOGGER.debug("No Modrinth icon for {}: {}", url, err == null ? "empty" : err.toString());
					});
				}
			});
			return null;
		}
		if (e.failed && System.currentTimeMillis() - e.failedAt > RETRY_MS) {
			// Offline a minute ago is not offline forever: ask again.
			ENTRIES.remove(url);
			return null;
		}
		if (e.texture == null) {
			return null;
		}
		return new Icon(e.texture, Math.min(1f, (System.currentTimeMillis() - e.readyAt) / 180f));
	}

	/** Uploads icons that finished decoding and frees the oldest past the limit. Call once per frame from the browser. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
			Decoded d = READY.poll();
			if (d == null) {
				break;
			}
			Entry e = ENTRIES.get(d.url());
			if (e == null || e.texture != null) {
				continue;
			}
			int size = ModIcons.SIZE;
			SmoothTexture tex = new SmoothTexture(() -> "Modrinth icon " + d.url(), size, size);
			NativeImage pixels = tex.getPixels();
			if (pixels == null) {
				e.failed = true;
				e.failedAt = System.currentTimeMillis();
				continue;
			}
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					dev.justaclient.render.LegacyImages.pixel(pixels, x, y, d.argb()[y * size + x]);
				}
			}
			tex.upload();
			ResourceLocation id = Justaclient.id("modrinth_icon/" + (nextId++));
			mc.getTextureManager().register(id, tex);
			e.texture = id;
			e.readyAt = System.currentTimeMillis();
		}
		if (ENTRIES.size() > MAX) {
			Queue<String> drop = new ArrayDeque<>();
			Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
			int over = ENTRIES.size() - MAX;
			while (it.hasNext() && drop.size() < over) {
				Map.Entry<String, Entry> old = it.next();
				// Only finished ones: dropping one still loading would just ask for it again next frame.
				if (old.getValue().texture != null || old.getValue().failed) {
					drop.add(old.getKey());
				}
			}
			for (String url : drop) {
				Entry old = ENTRIES.remove(url);
				if (old != null && old.texture != null) {
					mc.getTextureManager().release(old.texture);
				}
			}
		}
	}
}
