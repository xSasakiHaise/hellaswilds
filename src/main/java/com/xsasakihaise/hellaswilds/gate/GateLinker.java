package com.xsasakihaise.hellaswilds.gate;

import com.google.common.collect.ImmutableList;
import com.xsasakihaise.hellaswilds.HellasWilds;
import com.google.common.collect.ImmutableList;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeTile;
import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.PillarBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import com.xsasakihaise.hellaswilds.util.BlocksUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Locates pillar endpoints for a gate badge. The production behaviour includes creating the gate
 * field and invisible columns; here we store the chosen pillars on the badge tile so that commands
 * can reference them while future patches flesh out the remainder of the feature.
 */
public final class GateLinker {
    private GateLinker() {
    }

    public static void tryLinkGate(final World world, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer) {
        if (world.isRemote) {
            return;
        }

        final List<BlockPos> nearbyPillars = findNearbyPillars(world, pos, 5);
        if (nearbyPillars.size() < 2) {
            HellasWilds.LOGGER.warn("Gate badge at {} failed to find two nearby pillars.", pos);
            return;
        }

        final List<BlockPos> closestTwo = nearbyPillars.stream()
                .sorted(Comparator.comparingDouble(pillar -> pillar.distanceSq(pos)))
                .limit(2)
                .collect(Collectors.toList());

        final TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof GateBadgeTile)) {
            HellasWilds.LOGGER.warn("Gate badge tile missing at {} while attempting to link gate.", pos);
            return;
        }

        final GateBadgeTile badgeTile = (GateBadgeTile) tileEntity;
        clearGeometry(world, badgeTile);
        badgeTile.setLinkedPillars(closestTwo);

        if (!buildGateGeometry(world, pos, badgeTile, closestTwo)) {
            HellasWilds.LOGGER.warn("Failed to construct gate geometry at {}. Clearing linkage.", pos);
            badgeTile.setLinkedPillars(ImmutableList.of());
            clearGeometry(world, badgeTile);
            return;
        }

        HellasWilds.LOGGER.info("Linked gate badge at {} with pillars {}", pos, closestTwo);
    }

    public static void clearGate(final World world, final BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        final TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof GateBadgeTile) {
            final GateBadgeTile badgeTile = (GateBadgeTile) tileEntity;
            clearGeometry(world, badgeTile);
            badgeTile.setLinkedPillars(ImmutableList.of());
        }
    }

    private static List<BlockPos> findNearbyPillars(final World world, final BlockPos pos, final int radius) {
        final List<BlockPos> result = new ArrayList<>();
        final BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    final BlockState state = world.getBlockState(mutable);
                    if (state.getBlock() instanceof PillarBlock) {
                        result.add(mutable.toImmutable());
                    }
                }
            }
        }
        return result;
    }

    private static boolean buildGateGeometry(final World world, final BlockPos badgePos, final GateBadgeTile tile, final List<BlockPos> pillars) {
        final BlockPos pillarA = pillars.get(0);
        final BlockPos pillarB = pillars.get(1);
        final int deltaX = pillarB.getX() - pillarA.getX();
        final int deltaZ = pillarB.getZ() - pillarA.getZ();

        final List<BlockPos> gateField = new ArrayList<>();
        final List<BlockPos> columnBlocks = new ArrayList<>();
        final boolean locked = tile.isLocked();
        final BlockState fieldState = BlockRegistry.NON_PLAYER_FIELD.get().getDefaultState()
                .with(NonPlayerBarrierFieldBlock.LOCKED, locked);
        final int baseY = badgePos.getY();

        if (deltaX == 0 && deltaZ != 0) {
            final int gateX = badgePos.getX();
            final int minZ = Math.min(pillarA.getZ(), pillarB.getZ());
            final int maxZ = Math.max(pillarA.getZ(), pillarB.getZ());
            if (maxZ - minZ <= 1) {
                return false;
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                final BlockPos columnBase = new BlockPos(gateX, baseY, z);
                if (!isColumnClear(world, columnBase, 3)) {
                    return false;
                }
                for (int dy = 0; dy < 3; dy++) {
                    final BlockPos fieldPos = columnBase.up(dy);
                    world.setBlockState(fieldPos, fieldState, 3);
                    gateField.add(fieldPos);
                }
                columnBlocks.addAll(BlocksUtil.fillColumn(world, columnBase, baseY + 3, locked));
            }
        } else if (deltaZ == 0 && deltaX != 0) {
            final int gateZ = badgePos.getZ();
            final int minX = Math.min(pillarA.getX(), pillarB.getX());
            final int maxX = Math.max(pillarA.getX(), pillarB.getX());
            if (maxX - minX <= 1) {
                return false;
            }
            for (int x = minX + 1; x < maxX; x++) {
                final BlockPos columnBase = new BlockPos(x, baseY, gateZ);
                if (!isColumnClear(world, columnBase, 3)) {
                    return false;
                }
                for (int dy = 0; dy < 3; dy++) {
                    final BlockPos fieldPos = columnBase.up(dy);
                    world.setBlockState(fieldPos, fieldState, 3);
                    gateField.add(fieldPos);
                }
                columnBlocks.addAll(BlocksUtil.fillColumn(world, columnBase, baseY + 3, locked));
            }
        } else {
            return false;
        }

        tile.setGateFieldBlocks(gateField);
        tile.setColumnBlocks(columnBlocks);
        tile.applyLockState();
        return true;
    }

    private static boolean isColumnClear(final World world, final BlockPos base, final int height) {
        for (int dy = 0; dy < height; dy++) {
            final BlockPos pos = base.up(dy);
            final BlockState state = world.getBlockState(pos);
            if (!state.isAir(world, pos) && !BlocksUtil.isFieldBlock(state)) {
                return false;
            }
        }
        return true;
    }

    private static void clearGeometry(final World world, final GateBadgeTile tile) {
        for (final BlockPos fieldPos : tile.getGateFieldBlocks()) {
            if (BlocksUtil.isFieldBlock(world.getBlockState(fieldPos))) {
                world.removeBlock(fieldPos, false);
            }
        }
        for (final BlockPos columnPos : tile.getColumnBlocks()) {
            if (BlocksUtil.isFieldBlock(world.getBlockState(columnPos))) {
                world.removeBlock(columnPos, false);
            }
        }
        tile.setGateFieldBlocks(ImmutableList.of());
        tile.setColumnBlocks(ImmutableList.of());
    }
}
