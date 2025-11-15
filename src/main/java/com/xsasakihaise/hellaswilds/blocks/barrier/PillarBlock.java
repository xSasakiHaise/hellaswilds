package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import com.xsasakihaise.hellaswilds.util.BlocksUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
 * Four block tall pillar, storing a colour index for tinting.
 */
public class PillarBlock extends Block {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);

    /**
     * @param properties base block traits for the pillar (stone hardness, etc.).
     */
    public PillarBlock(final Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(COLOR, 0).with(SECTION, 0));
    }

    @Override
    protected void fillStateContainer(final StateContainer.Builder<Block, BlockState> builder) {
        super.fillStateContainer(builder);
        builder.add(COLOR, SECTION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockItemUseContext context) {
        final World world = context.getWorld();
        final BlockPos pos = context.getPos();
        if (pos.getY() > world.getHeight() - 4) {
            return null;
        }
        for (int i = 1; i < 4; i++) {
            if (!world.isAirBlock(pos.up(i))) {
                return null;
            }
        }
        final int color = ColorVariantBlockItem.getColor(context.getItem());
        return this.getDefaultState().with(COLOR, color).with(SECTION, 0);
    }

    @Override
    /**
     * Extends the placed pillar to the configured height whenever the base is planted.
     */
    public void onBlockPlacedBy(final World world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) {
            return;
        }
        final int section = state.get(SECTION);
        if (section == 0) {
            for (int i = 1; i < 4; i++) {
                final BlockPos segmentPos = pos.up(i);
                final BlockState segmentState = state.with(SECTION, i);
                world.setBlockState(segmentPos, segmentState, 3);
            }
            BlocksUtil.placePillarColumns(world, pos, state, 4);
        }
    }

    @Override
    /**
     * Removes all linked pillar segments when the base is swapped out.
     */
    public void onReplaced(final BlockState state, final World world, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isRemote && state.get(SECTION) == 0) {
                for (int i = 1; i < 4; i++) {
                    final BlockPos segmentPos = pos.up(i);
                    if (world.getBlockState(segmentPos).getBlock() == this) {
                        world.removeBlock(segmentPos, false);
                    }
                }
                BlocksUtil.removeBarrierColumns(world, pos, state, 4);
            }
        }
        super.onReplaced(state, world, pos, newState, isMoving);
    }

    @Override
    /**
     * When any section is harvested we tear down the entire stack and barrier column for a clean
     * rebuild later.
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
                for (int i = 1; i < 4; i++) {
                    final BlockPos segmentPos = pos.up(i);
                    if (world.getBlockState(segmentPos).getBlock() == this) {
                        world.removeBlock(segmentPos, false);
                    }
                }
                BlocksUtil.removeBarrierColumns(world, pos, state, 4);
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }
}
