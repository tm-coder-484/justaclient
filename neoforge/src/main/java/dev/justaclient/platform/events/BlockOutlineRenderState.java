package dev.justaclient.platform.events;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
public record BlockOutlineRenderState(BlockPos pos, VoxelShape shape) {}
