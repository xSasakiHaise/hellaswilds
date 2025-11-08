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

    public static void placeBarrierColumns(final World world, final BlockPos basePos, final BlockState state, final int height) {
        if (world.isRemote) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, basePos);
        fillColumn(world, actualBase, actualBase.getY() + height, false);
    }

    public static void placePillarColumns(final World world, final BlockPos basePos, final BlockState state, final int height) {
        if (world.isRemote) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, basePos);
        fillColumn(world, actualBase, actualBase.getY() + height, false);
    }

    public static void removeBarrierColumns(final World world, final BlockPos pos, final BlockState state, final int height) {
        if (world.isRemote) {
            return;
        }
        final BlockPos actualBase = resolveBasePosition(state, pos);
        clearColumn(world, actualBase, actualBase.getY() + height);
    }

    public static boolean isFieldBlock(final BlockState state) {
        return state.getBlock() instanceof NonPlayerBarrierFieldBlock;
    }

    public static BlockPos resolveBasePosition(final BlockState state, final BlockPos pos) {
        if (state.getBlock() instanceof BarrierSegmentBlock) {
            final int section = state.get(BarrierSegmentBlock.SECTION);
            return pos.down(section);
        }
        if (state.getBlock() instanceof PillarBlock) {
            final int section = state.get(PillarBlock.SECTION);
            return pos.down(section);
        }
        return pos;
    }

    public static List<BlockPos> fillColumn(final World world, final BlockPos base, final int startY, final boolean locked) {
        final List<BlockPos> placed = new ArrayList<>();
        final int worldTop = world.getHeight();
        final BlockState fieldState = BlockRegistry.NON_PLAYER_FIELD.get().getDefaultState().with(NonPlayerBarrierFieldBlock.LOCKED, locked);
        for (int y = Math.max(startY, 0); y < worldTop; y++) {
            final BlockPos target = new BlockPos(base.getX(), y, base.getZ());
            final BlockState existing = world.getBlockState(target);
            if (!existing.isAir(world, target) && !isFieldBlock(existing)) {
                continue;
            }
            world.setBlockState(target, fieldState, 3);
            placed.add(target);
        }
        return ImmutableList.copyOf(placed);
    }

    public static void clearColumn(final World world, final BlockPos base, final int startY) {
        final int worldTop = world.getHeight();
        for (int y = Math.max(startY, 0); y < worldTop; y++) {
            final BlockPos target = new BlockPos(base.getX(), y, base.getZ());
            final BlockState existing = world.getBlockState(target);
            if (isFieldBlock(existing)) {
                world.removeBlock(target, false);
            }
        }
    }
}
