package com.xsasakihaise.hellaswilds.zone;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a managed zone. The UUID acts as the persistence key while displayNumber and colour are
 * used for user-friendly rendering.
 */
public final class ZoneId {
    private final UUID uuid;
    private final int displayNumber;
    private final int color;
    private final String region;
    private final String ownerType;
    private final RegistryKey<World> dimension;

    public ZoneId(final UUID uuid, final int displayNumber, final int color, final String region, final String ownerType, final RegistryKey<World> dimension) {
        this.uuid = uuid;
        this.displayNumber = displayNumber;
        this.color = color;
        this.region = region;
        this.ownerType = ownerType;
        this.dimension = dimension;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getDisplayNumber() {
        return displayNumber;
    }

    public int getColor() {
        return color;
    }

    public String getRegion() {
        return region;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public RegistryKey<World> getDimension() {
        return dimension;
    }

    public ITextComponent createNameComponent() {
        return new StringTextComponent("Zone " + displayNumber);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ZoneId)) {
            return false;
        }
        final ZoneId other = (ZoneId) obj;
        return uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
