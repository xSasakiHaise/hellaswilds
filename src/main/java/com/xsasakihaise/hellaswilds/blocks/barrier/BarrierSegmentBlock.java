package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import com.xsasakihaise.hellaswilds.util.BlocksUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Barrier segment that behaves similarly to a glass pane while tracking a colour variant. The
 * multi-block column management is stubbed out through {@link BlocksUtil} helpers – ready to be
 * replaced with the full implementation.
 */
public class BarrierSegmentBlock extends PaneBlock {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 2);

    /**
     * @param properties pane-like block properties (hardness, sound, etc.).
     */
    public BarrierSegmentBlock(final Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState()
                .with(NORTH, false)
                .with(EAST, false)
                .with(SOUTH, false)
                .with(WEST, false)
                .with(COLOR, 0)
                .with(SECTION, 0));
    }

    @Override
    protected void fillStateContainer(final StateContainer.Builder<net.minecraft.block.Block, BlockState> builder) {
        super.fillStateContainer(builder);
        builder.add(COLOR, SECTION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockItemUseContext context) {
        final World world = context.getWorld();
        final BlockPos pos = context.getPos();
        if (pos.getY() > world.getHeight() - 3) {
            return null;
        }
        for (int i = 1; i < 3; i++) {
            if (!world.isAirBlock(pos.up(i))) {
                return null;
            }
        }
        final BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        final int color = ColorVariantBlockItem.getColor(context.getItem());
        return state.with(COLOR, color).with(SECTION, 0);
    }

    @Override
    /**
     * When the base segment (section 0) is placed we immediately materialise the two upper segments
     * and extend the invisible barrier column upwards. This mimics vanilla multi-block placement so
     * players cannot accidentally leave the gate half constructed.
     */
    public void onBlockPlacedBy(final World world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) {
            return;
        }
        final int section = state.get(SECTION);
        if (section == 0) {
            for (int i = 1; i < 3; i++) {
                final BlockPos segmentPos = pos.up(i);
                final BlockState segmentState = state.with(SECTION, i);
                world.setBlockState(segmentPos, segmentState, 3);
            }
            BlocksUtil.placeBarrierColumns(world, pos, state, 3);
        }
    }

    @Override
    /**
     * Tears down the companion segments plus the vertical field column whenever the base is broken
     * or replaced with another block.
     */
    public void onReplaced(final BlockState state, final World world, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isRemote) {
                final int section = state.get(SECTION);
                if (section == 0) {
                    for (int i = 1; i < 3; i++) {
                        final BlockPos segmentPos = pos.up(i);
                        if (world.getBlockState(segmentPos).getBlock() == this) {
                            world.removeBlock(segmentPos, false);
                        }
                    }
                    BlocksUtil.removeBarrierColumns(world, pos, state, 3);
                }
            }
        }
        super.onReplaced(state, world, pos, newState, isMoving);
    }

    @Override
    /**
     * Ensures harvesting any segment deletes the entire three block tall stack and frees the column.
     */
    public void onBlockHarvested(final World world, final BlockPos pos, final BlockState state, final PlayerEntity player) {
        if (!world.isRemote) {
            final int section = state.get(SECTION);
            if (section > 0) {
                final BlockPos basePos = pos.down(section);
                final BlockState baseState = world.getBlockState(basePos);
                if (baseState.getBlock() == this) {
                    world.destroyBlock(basePos, !player.isCreative());
                }
            } else {
                for (int i = 1; i < 3; i++) {
                    final BlockPos segmentPos = pos.up(i);
                    if (world.getBlockState(segmentPos).getBlock() == this) {
                        world.removeBlock(segmentPos, false);
                    }
                }
                BlocksUtil.removeBarrierColumns(world, pos, state, 3);
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }
}
