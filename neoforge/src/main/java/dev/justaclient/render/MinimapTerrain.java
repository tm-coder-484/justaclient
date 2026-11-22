package dev.justaclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/** A sliding window of world-aligned tiles. The extra border covers rotation and motion within the centre tile. */
public final class MinimapTerrain implements AutoCloseable {
	private static final int TILE = 16;
	private static final int ACROSS = 9;
	private static final int RADIUS = ACROSS / 2;
	public static final int SIZE = TILE * ACROSS;
	private static final int MAX_TILES_PER_TICK = 4;
	private static final int REFRESH_TICKS = 60;
	private static final int MAX_REFRESHES_PER_TICK = 2;

	private record Offset(int x, int z) {}
	private static final List<Offset> NEAREST_FIRST = offsets();

	private static List<Offset> offsets() {
		List<Offset> offsets = new ArrayList<>(ACROSS * ACROSS);
		for (int z = -RADIUS; z <= RADIUS; z++) {
			for (int x = -RADIUS; x <= RADIUS; x++) {
				offsets.add(new Offset(x, z));
			}
		}
		offsets.sort(Comparator.comparingInt(o -> o.x * o.x + o.z * o.z));
		return List.copyOf(offsets);
	}

	private static final class Tile {
		final NativeImage pixels = new NativeImage(TILE, TILE, false);
		int x;
		int z;
		long updated = -1;
	}

	private final Tile[] tiles = new Tile[ACROSS * ACROSS];
	private @Nullable NativeImage refresh;
	private @Nullable ClientLevel level;
	private int tileX;
	private int tileZ;
	private int blocks;
	private int layerY;
	private boolean cave;
	private long tick;

	public int centerX() {
		return (tileX * TILE + TILE / 2) * blocks;
	}

	public int centerZ() {
		return (tileZ * TILE + TILE / 2) * blocks;
	}

	public int blocksPerPixel() {
		return blocks;
	}

	public boolean ready(@Nullable ClientLevel current) {
		return level == current && blocks != 0;
	}

	/** Updates the CPU image and returns true only when the GPU texture needs uploading. */
	public boolean update(NativeImage image, ClientLevel world, int x, int y, int z, int zoom, boolean underground) {
		int cx = Math.floorDiv(x, TILE * zoom);
		int cz = Math.floorDiv(z, TILE * zoom);
		boolean heightMatters = underground || world.dimensionType().hasCeiling();
		boolean reset = level != world || blocks != zoom || cave != underground || heightMatters && Math.abs(y - layerY) >= 2;
		boolean recenter = reset || cx != tileX || cz != tileZ;
		if (reset) {
			for (Tile tile : tiles) {
				if (tile != null) {
					tile.updated = -1;
				}
			}
			level = world;
			blocks = zoom;
			cave = underground;
			layerY = y;
		}
		tileX = cx;
		tileZ = cz;
		tick++;
		boolean changed = recenter;
		int budget = MAX_TILES_PER_TICK;
		int refreshes = 0;
		for (Offset offset : NEAREST_FIRST) {
			int tx = cx + offset.x;
			int tz = cz + offset.z;
			int slot = Math.floorMod(tz, ACROSS) * ACROSS + Math.floorMod(tx, ACROSS);
			Tile tile = tiles[slot];
			boolean valid = tile != null && tile.updated >= 0 && tile.x == tx && tile.z == tz;
			int destX = (offset.x + RADIUS) * TILE;
			int destZ = (offset.z + RADIUS) * TILE;
			// Leave capacity for newly exposed terrain when interior tiles are also due.
			boolean sample = budget > 0 && (!valid || refreshes < MAX_REFRESHES_PER_TICK && tick - tile.updated >= REFRESH_TICKS);
			boolean paint = recenter && valid;
			if (sample) {
				if (tile == null) {
					tile = new Tile();
					tiles[slot] = tile;
				}
				if (valid && refresh == null) {
					refresh = new NativeImage(TILE, TILE, false);
				}
				NativeImage sampleImage = valid ? refresh : tile.pixels;
				int midX = (tx * TILE + TILE / 2) * zoom;
				int midZ = (tz * TILE + TILE / 2) * zoom;
				if (cave) {
					TerrainMap.fillCave(sampleImage, TILE, world, layerY, midX, midZ, zoom);
				} else {
					TerrainMap.fill(sampleImage, TILE, world, layerY + 1, midX, midZ, zoom);
				}
				tile.x = tx;
				tile.z = tz;
				tile.updated = tick;
				budget--;
				if (valid) {
					refreshes++;
					if (!samePixels(tile.pixels, sampleImage)) {
						tile.pixels.copyFrom(sampleImage);
						paint = true;
					}
				} else {
					paint = true;
				}
			}
			if (paint) {
				changed = true;
				tile.pixels.copyRect(image, 0, 0, destX, destZ, TILE, TILE, false, false);
			} else if (recenter) {
				image.fillRect(destX, destZ, TILE, TILE, 0);
			}
		}
		return changed;
	}

	private static boolean samePixels(NativeImage a, NativeImage b) {
		for (int z = 0; z < TILE; z++) {
			for (int x = 0; x < TILE; x++) {
				if (a.getPixelRGBA(x, z) != b.getPixelRGBA(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void close() {
		for (int i = 0; i < tiles.length; i++) {
			if (tiles[i] != null) {
				tiles[i].pixels.close();
				tiles[i] = null;
			}
		}
		if (refresh != null) {
			refresh.close();
			refresh = null;
		}
		level = null;
		blocks = 0;
	}
}
