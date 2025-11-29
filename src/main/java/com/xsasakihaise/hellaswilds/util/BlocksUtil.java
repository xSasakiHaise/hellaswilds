package com.xsasakihaise.hellaswilds.util;

import com.google.common.collect.ImmutableList;
import com.xsasakihaise.hellaswilds.blocks.barrier.BarrierSegmentBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.PillarBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for managing the invisible column blocks that extend barriers to the world height.
 * The helpers place or remove {@link NonPlayerBarrierFieldBlock} instances for the different barrier
 * structures. Gate badges store the resulting block positions so that locking logic can toggle the
 * column collision behaviour.
 */
public final class BlocksUtil {
    private BlocksUtil() {
    }

    /**
     * Builds a vertical stack of {@link NonPlayerBarrierFieldBlock} instances above the provided
     * barrier base to ensure the gate line reaches world height.
     *
     * @param world    server world the barrier lives in
     * @param basePos  player-facing position used to determine the column footprint
     * @param state    barrier block state that carries the section metadata
     * @param height   number of barrier sections included in the multi-block structure
     */
    public static void placeBarrierColumns(final World world, final BlockPos basePos, final BlockState state, final int height) {
        if (world.isClientSide) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, basePos);
        fillColumn(world, actualBase, actualBase.getY() + height, false);
    }

    /**
     * Mirrors {@link #placeBarrierColumns(World, BlockPos, BlockState, int)} but tailored to the
     * pillar block which ships with a different height.
     */
    public static void placePillarColumns(final World world, final BlockPos basePos, final BlockState state, final int height) {
        if (world.isClientSide) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, basePos);
        fillColumn(world, actualBase, actualBase.getY() + height, false);
    }

    /**
     * Removes the invisible barrier column when the gate structure is dismantled.
     */
    public static void removeBarrierColumns(final World world, final BlockPos pos, final BlockState state, final int height) {
        if (world.isClientSide) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, pos);
        clearColumn(world, actualBase, actualBase.getY() + height);
    }

    /**
     * @return {@code true} if the supplied state belongs to the invisible barrier field block.
     */
    public static boolean isFieldBlock(final BlockState state) {
        return state.getBlock() instanceof NonPlayerBarrierFieldBlock;
    }

    /**
     * Resolves the "true" base position for multi-block pillars/segments by accounting for the
     * section property baked into each state.
     */
    public static BlockPos resolveBasePosition(final BlockState state, final BlockPos pos) {
        if (state.getBlock() instanceof BarrierSegmentBlock) {
            final int section = state.getValue(BarrierSegmentBlock.SECTION);
            return pos.below(section);
        }
        if (state.getBlock() instanceof PillarBlock) {
            final int section = state.getValue(PillarBlock.SECTION);
            return pos.below(section);
        }
        return pos;
    }

    /**
     * Populates air with barrier field blocks from {@code startY} up to world height.
     *
     * @return immutable list containing every placed block position which allows gate tiles to
     * toggle the locked state without rescanning chunks.
     */
    public static List<BlockPos> fillColumn(final World world, final BlockPos base, final int startY, final boolean locked) {
        final List<BlockPos> placed = new ArrayList<>();
        final int worldTop = world.getMaxBuildHeight();
        final BlockState fieldState = BlockRegistry.NON_PLAYER_FIELD.get().defaultBlockState().setValue(NonPlayerBarrierFieldBlock.LOCKED, locked);
        for (int y = Math.max(startY, 0); y < worldTop; y++) {
            final BlockPos target = new BlockPos(base.getX(), y, base.getZ());
            final BlockState existing = world.getBlockState(target);
            if (!existing.isAir(world, target) && !isFieldBlock(existing)) {
                continue;
            }
            world.setBlock(target, fieldState, 3);
            placed.add(target);
        }
        return ImmutableList.copyOf(placed);
    }

    /**
     * Deletes any barrier field blocks between {@code startY} and the world roof.
     */
    public static void clearColumn(final World world, final BlockPos base, final int startY) {
        final int worldTop = world.getMaxBuildHeight();
        for (int y = Math.max(startY, 0); y < worldTop; y++) {
            final BlockPos target = new BlockPos(base.getX(), y, base.getZ());
            final BlockState existing = world.getBlockState(target);
            if (isFieldBlock(existing)) {
                world.removeBlock(target, false);
            }
        }
    }
}
