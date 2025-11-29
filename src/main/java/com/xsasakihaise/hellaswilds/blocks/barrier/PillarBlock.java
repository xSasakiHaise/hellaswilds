package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import com.xsasakihaise.hellaswilds.util.BlocksUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateDefinition;
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
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, 0).setValue(SECTION, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR, SECTION);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockItemUseContext context) {
        final World world = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (pos.getY() > world.getMaxBuildHeight() - 4) {
            return null;
        }
        for (int i = 1; i < 4; i++) {
            if (!world.isEmptyBlock(pos.above(i))) {
                return null;
            }
        }
        final int color = ColorVariantBlockItem.getColor(context.getItemInHand());
        return this.defaultBlockState().setValue(COLOR, color).setValue(SECTION, 0);
    }

    @Override
    /**
     * Extends the placed pillar to the configured height whenever the base is planted.
     */
    public void setPlacedBy(final World world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide) {
            return;
        }
        final int section = state.getValue(SECTION);
        if (section == 0) {
            for (int i = 1; i < 4; i++) {
                final BlockPos segmentPos = pos.above(i);
                final BlockState segmentState = state.setValue(SECTION, i);
                world.setBlock(segmentPos, segmentState, 3);
            }
            BlocksUtil.placePillarColumns(world, pos, state, 4);
        }
    }

    @Override
    /**
     * Removes all linked pillar segments when the base is swapped out.
     */
    public void onRemove(final BlockState state, final World world, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            if (!world.isClientSide && state.getValue(SECTION) == 0) {
                for (int i = 1; i < 4; i++) {
                    final BlockPos segmentPos = pos.above(i);
                    if (world.getBlockState(segmentPos).getBlock() == this) {
                        world.removeBlock(segmentPos, false);
                    }
                }
                BlocksUtil.removeBarrierColumns(world, pos, state, 4);
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    /**
     * When any section is harvested we tear down the entire stack and barrier column for a clean
     * rebuild later.
     */
    public void playerWillDestroy(final World world, final BlockPos pos, final BlockState state, final PlayerEntity player) {
        if (!world.isClientSide) {
            final int section = state.getValue(SECTION);
            if (section > 0) {
                final BlockPos basePos = pos.below(section);
                final BlockState baseState = world.getBlockState(basePos);
                if (baseState.getBlock() == this) {
                    world.destroyBlock(basePos, !player.isCreative());
                }
            } else {
                for (int i = 1; i < 4; i++) {
                    final BlockPos segmentPos = pos.above(i);
                    if (world.getBlockState(segmentPos).getBlock() == this) {
                        world.removeBlock(segmentPos, false);
                    }
                }
                BlocksUtil.removeBarrierColumns(world, pos, state, 4);
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }
}
