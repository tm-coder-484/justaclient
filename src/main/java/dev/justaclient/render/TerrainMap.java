package dev.justaclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Paints loaded terrain into an image the way map items do: one map colour per block column, shaded against the column
 * to the north. Shared by the minimap and the full map screen. Unloaded chunks are left transparent.
 */
public final class TerrainMap {
	/** Colour for columns that have no map colour, such as the void. */
	public static final int EMPTY = 0xFF101218;

	private TerrainMap() {
	}

	/**
	 * Fills a {@code size} by {@code size} image centred on block ({@code cx}, {@code cz}), each pixel covering
	 * {@code blocks} blocks. In dimensions with a roof it maps the floor at or below {@code floorFrom}.
	 */
	public static void fill(NativeImage image, int size, ClientLevel level, int floorFrom, int cx, int cz, int blocks) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int half = size / 2;
		// One extra row to the north so every pixel can be shaded against its northern neighbour, like map items.
		int[] previous = new int[size];
		for (int row = -1; row < size; row++) {
			int z = cz + (row - half) * blocks;
			for (int col = 0; col < size; col++) {
				int x = cx + (col - half) * blocks;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					if (row >= 0) {
						image.setPixel(col, row, 0);
					}
					previous[col] = Integer.MIN_VALUE;
					continue;
				}
				int y = surfaceY(level, pos, x, z, floorFrom);
				if (row >= 0) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					MapColor color = state.getMapColor(level, pos);
					MapColor.Brightness shade = MapColor.Brightness.NORMAL;
					if (previous[col] != Integer.MIN_VALUE && color != MapColor.WATER) {
						shade = y > previous[col] ? MapColor.Brightness.HIGH : y < previous[col] ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
					}
					image.setPixel(col, row, color == MapColor.NONE ? EMPTY : color.calculateARGBColor(shade));
				}
				previous[col] = y;
			}
		}
	}

	/** The top block of a column, or in dimensions with a roof the first solid block at or below {@code floorFrom}. */
	public static int surfaceY(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int floorFrom) {
		if (!level.dimensionType().hasCeiling()) {
			return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		}
		int y = floorFrom;
		for (int i = 0; i < 48 && y > level.getMinY(); i++, y--) {
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				return y;
			}
		}
		return y;
	}
}
