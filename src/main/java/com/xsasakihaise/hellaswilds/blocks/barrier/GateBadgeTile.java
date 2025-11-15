package com.xsasakihaise.hellaswilds.blocks.barrier;

import com.google.common.collect.ImmutableList;
import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import com.xsasakihaise.hellaswilds.registry.TileRegistry;
import com.xsasakihaise.hellaswilds.zone.ZoneId;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores metadata about a gate badge. A minimal implementation is provided that tracks the core
 * details required by the command system and rendering cache. Actual rendering and non-player gate
 * collision still needs to be implemented in a follow-up pass.
 */
public class GateBadgeTile extends TileEntity {
    private UUID zoneId;
    private int displayNumber;
    private int color;
    private boolean locked;

    private List<BlockPos> linkedPillars = ImmutableList.of();
    private List<BlockPos> gateFieldBlocks = ImmutableList.of();
    private List<BlockPos> columnBlocks = ImmutableList.of();
    private AxisAlignedBB cachedBounds;

    public GateBadgeTile() {
        this(TileRegistry.GATE_BADGE_TILE.get());
    }

    public GateBadgeTile(final TileEntityType<?> type) {
        super(type);
    }

    @Override
    public void read(final BlockState state, final CompoundNBT tag) {
        super.read(state, tag);
        if (tag.hasUniqueId("ZoneId")) {
            this.zoneId = tag.getUniqueId("ZoneId");
        }
        this.displayNumber = tag.getInt("DisplayNumber");
        this.color = tag.getInt("Color");
        this.locked = tag.getBoolean("Locked");
        if (tag.contains("Bounds")) {
            this.cachedBounds = readAabb(tag.getCompound("Bounds"));
        }
        this.linkedPillars = readPosList(tag, "Pillars");
        this.gateFieldBlocks = readPosList(tag, "GateField");
        this.columnBlocks = readPosList(tag, "Columns");
    }

    @Override
    public CompoundNBT write(final CompoundNBT tag) {
        super.write(tag);
        if (zoneId != null) {
            tag.putUniqueId("ZoneId", zoneId);
        }
        tag.putInt("DisplayNumber", displayNumber);
        tag.putInt("Color", color);
        tag.putBoolean("Locked", locked);
        if (cachedBounds != null) {
            tag.put("Bounds", writeAabb(cachedBounds));
        }
        writePosList(tag, "Pillars", linkedPillars);
        writePosList(tag, "GateField", gateFieldBlocks);
        writePosList(tag, "Columns", columnBlocks);
        return tag;
    }

    /**
     * Assigns the managed zone that owns this gate. The UUID is stored instead of the full object so
     * the tile can survive world reloads.
     */
    public void setZoneId(@Nullable final ZoneId zoneId) {
        this.zoneId = zoneId == null ? null : zoneId.getUuid();
        markDirty();
    }

    @Nullable
    public UUID getZoneId() {
        return zoneId;
    }

    /**
     * Updates the number rendered on the badge.
     */
    public void setDisplayNumber(final int displayNumber) {
        this.displayNumber = displayNumber;
        markDirty();
    }

    public int getDisplayNumber() {
        return displayNumber;
    }

    /**
     * Stores the dye index for client renderers.
     */
    public void setColor(final int color) {
        this.color = color;
        markDirty();
    }

    public int getColor() {
        return color;
    }

    /**
     * Toggles whether the associated gate fields collide with players.
     */
    public void setLocked(final boolean locked) {
        this.locked = locked;
        markDirty();
        applyLockState();
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * Caches the pillar endpoints detected during {@link com.xsasakihaise.hellaswilds.gate.GateLinker}.
     */
    public void setLinkedPillars(final List<BlockPos> linkedPillars) {
        this.linkedPillars = ImmutableList.copyOf(linkedPillars);
        markDirty();
    }

    public List<BlockPos> getLinkedPillars() {
        return linkedPillars;
    }

    /**
     * Stores the exact non-player field locations so they can be toggled or removed without a chunk
     * scan.
     */
    public void setGateFieldBlocks(final List<BlockPos> gateFieldBlocks) {
        this.gateFieldBlocks = ImmutableList.copyOf(gateFieldBlocks);
        markDirty();
    }

    public List<BlockPos> getGateFieldBlocks() {
        return gateFieldBlocks;
    }

    /**
     * Records the invisible column extensions belonging to this gate.
     */
    public void setColumnBlocks(final List<BlockPos> columnBlocks) {
        this.columnBlocks = ImmutableList.copyOf(columnBlocks);
        markDirty();
    }

    public List<BlockPos> getColumnBlocks() {
        return columnBlocks;
    }

    /**
     * Stores the last detected zone bounds for use by rendering/debug commands.
     */
    public void cacheBounds(@Nullable final AxisAlignedBB bounds) {
        this.cachedBounds = bounds;
        markDirty();
    }

    @Nullable
    public AxisAlignedBB getCachedBounds() {
        return cachedBounds;
    }

    public ITextComponent createDebugComponent() {
        return new StringTextComponent("GateBadgeTile{" +
                "zone=" + zoneId +
                ", display=" + displayNumber +
                ", color=" + color +
                ", locked=" + locked +
                "}");
    }

    /**
     * Iterates over cached field/column blocks and updates their LOCKED property. Doing this here
     * keeps the state consistent even if tiles reload while the gate is locked.
     */
    public void applyLockState() {
        if (world == null || world.isRemote) {
            return;
        }
        final List<BlockPos> positions = new ArrayList<>(gateFieldBlocks.size() + columnBlocks.size());
        positions.addAll(gateFieldBlocks);
        positions.addAll(columnBlocks);
        for (final BlockPos fieldPos : positions) {
            final BlockState state = world.getBlockState(fieldPos);
            if (state.getBlock() == BlockRegistry.NON_PLAYER_FIELD.get()) {
                world.setBlockState(fieldPos, state.with(NonPlayerBarrierFieldBlock.LOCKED, locked), 2 | 16);
            }
        }
    }

    private static AxisAlignedBB readAabb(final CompoundNBT tag) {
        final Vector3d min = new Vector3d(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"));
        final Vector3d max = new Vector3d(tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
        return new AxisAlignedBB(min, max);
    }

    private static CompoundNBT writeAabb(final AxisAlignedBB box) {
        final CompoundNBT tag = new CompoundNBT();
        tag.putDouble("minX", box.minX);
        tag.putDouble("minY", box.minY);
        tag.putDouble("minZ", box.minZ);
        tag.putDouble("maxX", box.maxX);
        tag.putDouble("maxY", box.maxY);
        tag.putDouble("maxZ", box.maxZ);
        return tag;
    }

    private static List<BlockPos> readPosList(final CompoundNBT tag, final String key) {
        final List<BlockPos> positions = new ArrayList<>();
        if (tag.contains(key, 9)) {
            final ListNBT list = tag.getList(key, 10);
            for (int i = 0; i < list.size(); i++) {
                final CompoundNBT entry = list.getCompound(i);
                positions.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
            }
        }
        return ImmutableList.copyOf(positions);
    }

    private static void writePosList(final CompoundNBT tag, final String key, final List<BlockPos> positions) {
        final ListNBT list = new ListNBT();
        for (final BlockPos pos : positions) {
            final CompoundNBT entry = new CompoundNBT();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            list.add(entry);
        }
        tag.put(key, list);
    }
}
