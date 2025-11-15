package com.xsasakihaise.hellaswilds.zone;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeTile;
import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the dual flood-fill zone detection described in the design brief. Two searches start
 * on either side of the gate plane and explore up to a fixed tile budget. The enclosed region is the
 * finite search result; if both sides are finite the smaller region wins.
 */
public final class ZoneDetector {
    private ZoneDetector() {
    }

    private static final int MAX_TILES = 50_000;

    /**
     * Executes the dual flood-fill heuristic and returns the winning region. Any overflow or invalid
     * geometry results in a degenerate bounds box around the gate badge.
     */
    public static Result detect(final World world, final BlockPos gatePos, final GateBadgeTile tile) {
        if (tile.getLinkedPillars().size() < 2) {
            HellasWilds.LOGGER.warn("Attempted zone detection without linked pillars at {}", gatePos);
            return Result.error(new AxisAlignedBB(gatePos));
        }

        final BlockPos pillarA = tile.getLinkedPillars().get(0);
        final BlockPos pillarB = tile.getLinkedPillars().get(1);
        final int deltaX = pillarB.getX() - pillarA.getX();
        final int deltaZ = pillarB.getZ() - pillarA.getZ();
        Direction normalA;
        Direction normalB;

        if (deltaX == 0 && deltaZ != 0) {
            normalA = Direction.EAST;
            normalB = Direction.WEST;
        } else if (deltaZ == 0 && deltaX != 0) {
            normalA = Direction.NORTH;
            normalB = Direction.SOUTH;
        } else {
            HellasWilds.LOGGER.warn("Unsupported pillar layout for gate at {}. Returning degenerate bounds.", gatePos);
            return Result.error(new AxisAlignedBB(gatePos));
        }

        final BlockPos startA = gatePos.offset(normalA);
        final BlockPos startB = gatePos.offset(normalB);
        final FloodResult resultA = flood(world, startA, gatePos.getY());
        final FloodResult resultB = flood(world, startB, gatePos.getY());

        if (resultA.overflow && resultB.overflow) {
            HellasWilds.LOGGER.error("Both flood fills overflowed at {}. Returning badge-local bounds.", gatePos);
            return Result.error(new AxisAlignedBB(gatePos));
        }

        final FloodResult winner;
        if (resultA.overflow) {
            winner = resultB;
        } else if (resultB.overflow) {
            winner = resultA;
        } else {
            winner = resultA.visitedCount <= resultB.visitedCount ? resultA : resultB;
        }

        final List<AxisAlignedBB> overlay = new ArrayList<>(winner.visited.size());
        for (final BlockPos pos : winner.visited) {
            overlay.add(new AxisAlignedBB(pos.getX(), gatePos.getY(), pos.getZ(), pos.getX() + 1, gatePos.getY() + 0.01, pos.getZ() + 1));
        }

        return new Result(winner.bounds, overlay, winner.overflow);
    }

    private static FloodResult flood(final World world, final BlockPos start, final int baseY) {
        final Set<BlockPos> visited = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(new BlockPos(start.getX(), baseY, start.getZ()));

        int minX = start.getX();
        int maxX = start.getX();
        int minZ = start.getZ();
        int maxZ = start.getZ();

        while (!queue.isEmpty() && visited.size() <= MAX_TILES) {
            final BlockPos current = queue.poll();
            if (visited.contains(current)) {
                continue;
            }
            if (isBlocked(world, current)) {
                continue;
            }
            visited.add(current);
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());

            queue.add(current.north());
            queue.add(current.south());
            queue.add(current.east());
            queue.add(current.west());
        }

        final boolean overflow = visited.size() > MAX_TILES;
        final AxisAlignedBB bounds;
        if (visited.isEmpty()) {
            bounds = new AxisAlignedBB(start.getX(), baseY, start.getZ(), start.getX() + 1, world.getHeight(), start.getZ() + 1);
        } else {
            bounds = new AxisAlignedBB(minX, baseY, minZ, maxX + 1, world.getHeight(), maxZ + 1);
        }
        return new FloodResult(bounds, visited.size(), overflow, visited);
    }

    private static boolean isBlocked(final World world, final BlockPos pos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
        for (int dy = 0; dy < 3; dy++) {
            final BlockState state = world.getBlockState(mutable);
            final Block block = state.getBlock();
            if (block instanceof NonPlayerBarrierFieldBlock
                    || block == BlockRegistry.BARRIER_SEGMENT.get()
                    || block == BlockRegistry.PILLAR.get()) {
                return true;
            }
            mutable.move(Direction.UP);
        }
        return false;
    }

    private static final class FloodResult {
        final AxisAlignedBB bounds;
        final int visitedCount;
        final boolean overflow;
        final Set<BlockPos> visited;

        FloodResult(final AxisAlignedBB bounds, final int visitedCount, final boolean overflow, final Set<BlockPos> visited) {
            this.bounds = bounds;
            this.visitedCount = visitedCount;
            this.overflow = overflow;
            this.visited = visited;
        }
    }

    public static final class Result {
        private final AxisAlignedBB bounds;
        private final List<AxisAlignedBB> overlay;
        private final boolean overflow;

        private Result(final AxisAlignedBB bounds, final List<AxisAlignedBB> overlay, final boolean overflow) {
            this.bounds = bounds;
            this.overlay = overlay;
            this.overflow = overflow;
        }

        public AxisAlignedBB getBounds() {
            return bounds;
        }

        public List<AxisAlignedBB> getOverlay() {
            return overlay;
        }

        public boolean isOverflow() {
            return overflow;
        }

        private static Result error(final AxisAlignedBB bounds) {
            return new Result(bounds, new ArrayList<>(), true);
        }
    }
}
