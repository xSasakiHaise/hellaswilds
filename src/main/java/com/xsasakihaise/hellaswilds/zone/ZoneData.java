package com.xsasakihaise.hellaswilds.zone;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

/**
 * Serializable zone metadata including cached bounds, overlay geometry and spawn configuration.
 */
public final class ZoneData {
    private final ZoneId id;
    private final BlockPos gatePos;
    private final AxisAlignedBB bounds;
    private final List<AxisAlignedBB> overlay;
    private final String spawnMode;
    private final int spawnCap;

    public ZoneData(final ZoneId id,
                    final BlockPos gatePos,
                    final AxisAlignedBB bounds,
                    final List<AxisAlignedBB> overlay,
                    final String spawnMode,
                    final int spawnCap) {
        this.id = id;
        this.gatePos = gatePos;
        this.bounds = bounds;
        this.overlay = Collections.unmodifiableList(overlay);
        this.spawnMode = spawnMode;
        this.spawnCap = spawnCap;
    }

    public ZoneId getId() {
        return id;
    }

    public BlockPos getGatePos() {
        return gatePos;
    }

    public AxisAlignedBB getBounds() {
        return bounds;
    }

    public List<AxisAlignedBB> getOverlay() {
        return overlay;
    }

    public String getSpawnMode() {
        return spawnMode;
    }

    public int getSpawnCap() {
        return spawnCap;
    }

    public ZoneData withSpawnMode(final String mode) {
        return new ZoneData(id, gatePos, bounds, overlay, mode, spawnCap);
    }

    public ZoneData withSpawnCap(final int cap) {
        return new ZoneData(id, gatePos, bounds, overlay, spawnMode, cap);
    }

    public ZoneData withOverlay(final List<AxisAlignedBB> newOverlay, final AxisAlignedBB newBounds) {
        return new ZoneData(id, gatePos, newBounds, newOverlay, spawnMode, spawnCap);
    }
}
