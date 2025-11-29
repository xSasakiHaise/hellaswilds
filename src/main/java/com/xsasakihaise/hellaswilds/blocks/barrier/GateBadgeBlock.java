package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.xsasakihaise.hellaswilds.gate.GateLinker;
import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateDefinition;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Badge block which acts as the anchor for a gate. The actual networking and zone linkage is
 * performed through the {@link GateLinker} class.
 */
public class GateBadgeBlock extends Block {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);

    public GateBadgeBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    /**
     * Captures the chosen dye colour from the held item and applies it to the placed badge block.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockItemUseContext context) {
        final int color = ColorVariantBlockItem.getColor(context.getItemInHand());
        return this.defaultBlockState().setValue(COLOR, color);
    }

    /**
     * Syncs the tile entity with the chosen colour and immediately attempts to link the gate to
     * nearby pillars.
     */
    @Override
    public void setPlacedBy(final World world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (!world.isClientSide) {
            final TileEntity tile = world.getBlockEntity(pos);
            if (tile instanceof GateBadgeTile) {
                ((GateBadgeTile) tile).setColor(state.getValue(COLOR));
            }
        }
        GateLinker.tryLinkGate(world, pos, state, placer);
    }

    /**
     * Clears previously linked geometry when the badge is removed.
     */
    @Override
    public void onRemove(final BlockState state, final World world, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);
        if (state.getBlock() != newState.getBlock()) {
            GateLinker.clearGate(world, pos);
        }
    }

    @Override
    public boolean hasTileEntity(final BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(final BlockState state, final IBlockReader world) {
        return new GateBadgeTile();
    }
}
