package com.xsasakihaise.hellaswilds.blocks.barrier;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateDefinition;

/**
 * Invisible barrier used to block non-player entities. The collision shape is exposed for any
 * entity that is not a player, ensuring Pixelmon and other mobs cannot cross the field while
 * players can walk through it without resistance.
 */
public class NonPlayerBarrierFieldBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape NO_COLLISION = VoxelShapes.empty();
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public NonPlayerBarrierFieldBlock() {
        super(AbstractBlock.Properties.of(Material.BARRIER)
                .hardnessAndResistance(-1.0F, 3600000.0F)
                .noDrops()
                .doesNotBlockMovement()
                .sound(SoundType.WOOL));
        this.registerDefaultState(this.stateDefinition.any().setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOCKED);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final IBlockReader world, final BlockPos pos, final ISelectionContext context) {
        return SHAPE;
    }

    /**
     * Only exposes collision geometry to non-players (and locked players) so walking through an
     * unlocked gate feels seamless.
     */
    @Override
    public VoxelShape getCollisionShape(final BlockState state, final IBlockReader world, final BlockPos pos, final ISelectionContext context) {
        final Entity entity = context.getEntity();
        final boolean locked = state.getValue(LOCKED);
        if (entity instanceof PlayerEntity && !locked) {
            return NO_COLLISION;
        }
        return SHAPE;
    }

    @Override
    public VoxelShape getRaytraceShape(final BlockState state, final IBlockReader world, final BlockPos pos, final ISelectionContext context) {
        return SHAPE;
    }

    /**
     * Applies heavy drag to items/projectiles and freezes non-player movement so mobs cannot glitch
     * through the field while the gate is locked.
     */
    @Override
    public void onEntityCollision(final BlockState state, final World world, final BlockPos pos, final Entity entity) {
        final boolean locked = state.getValue(LOCKED);
        if (entity instanceof PlayerEntity && !locked) {
            return;
        }
        if (!(entity instanceof PlayerEntity)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 1, 0));
        }
        if (entity instanceof ItemEntity || entity instanceof ProjectileEntity) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 0, 0));
            entity.setPos(entity.getX(), Math.max(entity.getY(), pos.getY() + 0.5), entity.getZ());
        }
    }
}
