package dev.justaclient.render;

import dev.justaclient.Justaclient;
import dev.justaclient.waypoint.Waypoints;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Remembers the terrain colour of every chunk the player has had loaded, so the minimap and the full map keep showing
 * places after their chunks unload, across sessions. One file per 256 by 256 block region, per world and dimension, in
 * {@code config/justaclient/maps/}. It records the same colours a map item would show and nothing the player has not
 * had loaded, so it reveals nothing hidden.
 */
public final class ExploredMap {
	private static final int SHIFT = 8;
	private static final int SIZE = 1 << SHIFT;
	private static final int MAGIC = 0x4A41434D;
	private static final int VERSION = 1;
	/** Regions kept in memory (256 KB each); older ones are saved and dropped. */
	private static final int MAX_LOADED = 96;
	/** Chunks recorded per tick, so joining a world does not stall a frame. */
	private static final int CHUNKS_PER_TICK = 24;
	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("justaclient").resolve("maps");
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Justaclient map saver");
		t.setDaemon(true);
		return t;
	});

	private static final class Region {
		final int[] pixels = new int[SIZE * SIZE];
		boolean dirty;
	}

	private static final LinkedHashMap<Long, Region> regions = new LinkedHashMap<>(16, 0.75f, true);
	/** Regions with no file on disk yet, so lookups do not keep checking. */
	private static final Set<Long> missing = new HashSet<>();
	private static final ArrayDeque<Long> queue = new ArrayDeque<>();
	private static final Set<Long> queued = new HashSet<>();
	private static @Nullable String key;
	private static @Nullable Path dir;
	private static long lastKey = Long.MIN_VALUE;
	private static @Nullable Region lastRegion;
	private static long lastNearby;
	private static long lastSave;

	private ExploredMap() {
	}

	public static void init() {
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> enqueue(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			saveDirty();
			IO.shutdown();
			try {
				IO.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	private static void enqueue(int cx, int cz) {
		long k = pack(cx, cz);
		if (queued.add(k)) {
			queue.add(k);
		}
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		String world = level == null ? null : Waypoints.worldKey();
		String now = world == null ? null : world + "|" + Waypoints.dimension();
		if (!Objects.equals(now, key)) {
			switchTo(now, world);
			if (level != null && player != null) {
				// Chunks that loaded before the switch was seen.
				int pcx = player.getBlockX() >> 4;
				int pcz = player.getBlockZ() >> 4;
				for (int r = 0; r <= 32; r++) {
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							if ((Math.abs(dx) == r || Math.abs(dz) == r) && level.getChunkSource().hasChunk(pcx + dx, pcz + dz)) {
								enqueue(pcx + dx, pcz + dz);
							}
						}
					}
				}
			}
		}
		if (level == null || player == null) {
			return;
		}
		long ms = System.currentTimeMillis();
		if (ms - lastNearby > 2000) {
			// Blocks change: refresh the chunks right around the player now and then.
			lastNearby = ms;
			int pcx = player.getBlockX() >> 4;
			int pcz = player.getBlockZ() >> 4;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					enqueue(pcx + dx, pcz + dz);
				}
			}
		}
		int floorFrom = player.getBlockY() + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int budget = CHUNKS_PER_TICK; budget > 0 && !queue.isEmpty(); budget--) {
			long k = queue.poll();
			queued.remove(k);
			int cx = (int) (k >> 32);
			int cz = (int) k;
			if (level.getChunkSource().hasChunk(cx, cz)) {
				record(level, pos, floorFrom, cx, cz);
			}
		}
		if (ms - lastSave > 30_000) {
			lastSave = ms;
			saveDirty();
		}
	}

	private static void switchTo(@Nullable String newKey, @Nullable String world) {
		saveDirty();
		regions.clear();
		missing.clear();
		queue.clear();
		queued.clear();
		lastRegion = null;
		lastKey = Long.MIN_VALUE;
		key = newKey;
		dir = newKey == null || world == null ? null : ROOT.resolve(safe(world)).resolve(safe(Waypoints.dimension()));
	}

	private static String safe(String s) {
		return s.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static void record(ClientLevel level, BlockPos.MutableBlockPos pos, int floorFrom, int cx, int cz) {
		int x0 = cx << 4;
		int z0 = cz << 4;
		Region region = region(x0 >> SHIFT, z0 >> SHIFT, true);
		if (region == null) {
			return;
		}
		boolean northLoaded = level.getChunkSource().hasChunk(cx, cz - 1);
		for (int dx = 0; dx < 16; dx++) {
			int x = x0 + dx;
			int north = northLoaded ? TerrainMap.surfaceY(level, pos, x, z0 - 1, floorFrom) : Integer.MIN_VALUE;
			for (int dz = 0; dz < 16; dz++) {
				int z = z0 + dz;
				int y = TerrainMap.surfaceY(level, pos, x, z, floorFrom);
				int color = TerrainMap.color(level, pos, x, y, z, north);
				north = y;
				int i = ((z & (SIZE - 1)) << SHIFT) | (x & (SIZE - 1));
				if (region.pixels[i] != color) {
					region.pixels[i] = color;
					region.dirty = true;
				}
			}
		}
	}

	/** The remembered colour of a block column in the current dimension, or 0 if it was never seen. */
	public static int get(int x, int z) {
		Region r = region(x >> SHIFT, z >> SHIFT, false);
		return r == null ? 0 : r.pixels[((z & (SIZE - 1)) << SHIFT) | (x & (SIZE - 1))];
	}

	private static @Nullable Region region(int rx, int rz, boolean create) {
		long k = pack(rx, rz);
		if (k == lastKey && lastRegion != null) {
			return lastRegion;
		}
		Path d = dir;
		if (d == null) {
			return null;
		}
		Region r = regions.get(k);
		if (r == null) {
			if (!missing.contains(k)) {
				r = load(d.resolve(fileName(rx, rz)));
				if (r == null) {
					missing.add(k);
				}
			}
			if (r == null) {
				if (!create) {
					return null;
				}
				r = new Region();
				missing.remove(k);
			}
			regions.put(k, r);
			evict();
		}
		lastKey = k;
		lastRegion = r;
		return r;
	}

	private static void evict() {
		Iterator<Map.Entry<Long, Region>> it = regions.entrySet().iterator();
		while (regions.size() > MAX_LOADED && it.hasNext()) {
			Map.Entry<Long, Region> eldest = it.next();
			if (eldest.getValue().dirty) {
				save(eldest.getKey(), eldest.getValue());
			}
			if (eldest.getValue() == lastRegion) {
				lastRegion = null;
				lastKey = Long.MIN_VALUE;
			}
			it.remove();
		}
	}

	private static String fileName(int rx, int rz) {
		return "r." + rx + "." + rz + ".jacmap";
	}

	private static @Nullable Region load(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (InputStream raw = Files.newInputStream(file); DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16))) {
			if (in.readInt() != MAGIC || in.readInt() != VERSION) {
				return null;
			}
			byte[] bytes = in.readNBytes(SIZE * SIZE * 4);
			if (bytes.length != SIZE * SIZE * 4) {
				return null;
			}
			Region r = new Region();
			ByteBuffer.wrap(bytes).asIntBuffer().get(r.pixels);
			return r;
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not read map region {}", file, e);
			return null;
		}
	}

	private static void saveDirty() {
		for (Map.Entry<Long, Region> e : regions.entrySet()) {
			if (e.getValue().dirty) {
				save(e.getKey(), e.getValue());
			}
		}
	}

	private static void save(long k, Region region) {
		Path d = dir;
		if (d == null) {
			return;
		}
		region.dirty = false;
		ByteBuffer buffer = ByteBuffer.allocate(SIZE * SIZE * 4);
		buffer.asIntBuffer().put(region.pixels);
		byte[] bytes = buffer.array();
		Path file = d.resolve(fileName((int) (k >> 32), (int) k));
		try {
			IO.execute(() -> write(file, bytes));
		} catch (RuntimeException e) {
			// Shutting down: write on this thread instead.
			write(file, bytes);
		}
	}

	private static void write(Path file, byte[] bytes) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (OutputStream raw = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw, 1 << 16))) {
				out.writeInt(MAGIC);
				out.writeInt(VERSION);
				out.write(bytes);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Justaclient.LOGGER.warn("Could not save map region {}", file, e);
		}
	}

	private static long pack(int a, int b) {
		return ((long) a << 32) | (b & 0xFFFFFFFFL);
	}
}
