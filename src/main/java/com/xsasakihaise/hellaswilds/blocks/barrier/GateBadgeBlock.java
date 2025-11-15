package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.xsasakihaise.hellaswilds.gate.GateLinker;
import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
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
        this.setDefaultState(this.stateContainer.getBaseState().with(COLOR, 0));
    }

    @Override
    protected void fillStateContainer(final StateContainer.Builder<Block, BlockState> builder) {
        super.fillStateContainer(builder);
        builder.add(COLOR);
    }

    /**
     * Captures the chosen dye colour from the held item and applies it to the placed badge block.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockItemUseContext context) {
        final int color = ColorVariantBlockItem.getColor(context.getItem());
        return this.getDefaultState().with(COLOR, color);
    }

    /**
     * Syncs the tile entity with the chosen colour and immediately attempts to link the gate to
     * nearby pillars.
     */
    @Override
    public void onBlockPlacedBy(final World world, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote) {
            final TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof GateBadgeTile) {
                ((GateBadgeTile) tile).setColor(state.get(COLOR));
            }
        }
        GateLinker.tryLinkGate(world, pos, state, placer);
    }

    /**
     * Clears previously linked geometry when the badge is removed.
     */
    @Override
    public void onReplaced(final BlockState state, final World world, final BlockPos pos, final BlockState newState, final boolean isMoving) {
        super.onReplaced(state, world, pos, newState, isMoving);
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
