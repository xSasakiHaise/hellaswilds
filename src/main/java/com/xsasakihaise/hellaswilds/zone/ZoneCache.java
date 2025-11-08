package com.xsasakihaise.hellaswilds.zone;

import com.google.common.collect.Maps;
import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.spawns.SpawnStorage;
import com.xsasakihaise.hellaswilds.spawns.ZoneSpawnController;
import net.minecraft.entity.Entity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.FolderName;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory zone cache backed by JSON persistence. Handles loading on world start and saving during
 * world flushes so zone data remains consistent across restarts.
 */
public final class ZoneCache {
    private static final ZoneCache INSTANCE = new ZoneCache();

    private final Map<RegistryKey<World>, Map<UUID, ZoneData>> zones = Maps.newHashMap();

    private ZoneCache() {
    }

    public static ZoneCache get() {
        return INSTANCE;
    }

    public synchronized void put(final ZoneData data) {
        zones.computeIfAbsent(data.getId().getDimension(), key -> Maps.newHashMap()).put(data.getId().getUuid(), data);
        ZoneSpawnController.ensureZone(data);
    }

    public synchronized void remove(final ZoneData data) {
        final Map<UUID, ZoneData> map = zones.get(data.getId().getDimension());
        if (map != null) {
            map.remove(data.getId().getUuid());
        }
        ZoneSpawnController.removeZone(data);
    }

    public synchronized ZoneData get(final UUID id) {
        for (final Map<UUID, ZoneData> map : zones.values()) {
            final ZoneData data = map.get(id);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    public synchronized Collection<ZoneData> values(final RegistryKey<World> dimension) {
        final Map<UUID, ZoneData> map = zones.get(dimension);
        if (map == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(map.values());
    }

    public synchronized ZoneData findByDisplayNumber(final RegistryKey<World> dimension, final int displayNumber) {
        final Map<UUID, ZoneData> map = zones.get(dimension);
        if (map == null) {
            return null;
        }
        return map.values().stream()
                .filter(zone -> zone.getId().getDisplayNumber() == displayNumber)
                .findFirst()
                .orElse(null);
    }

    public synchronized ZoneData findZone(final RegistryKey<World> dimension, final Vector3d position) {
        final Map<UUID, ZoneData> map = zones.get(dimension);
        if (map == null) {
            return null;
        }
        return map.values().stream()
                .filter(zone -> zone.getBounds().contains(position))
                .findFirst()
                .orElse(null);
    }

    public ZoneData findZone(final ServerWorld world, final BlockPos pos) {
        return findZone(world.getDimensionKey(), new Vector3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    public ZoneData findZone(final ServerWorld world, final Entity entity) {
        return findZone(world.getDimensionKey(), entity.getPositionVec());
    }

    public synchronized void invalidateAll() {
        zones.clear();
    }

    public synchronized void invalidateDimension(final RegistryKey<World> dimension) {
        zones.remove(dimension);
    }

    public synchronized void load(final ServerWorld world) {
        final RegistryKey<World> dimension = world.getDimensionKey();
        final Path worldPath = world.getServer().getWorldPath(FolderName.ROOT);
        final List<ZoneStorage.ZoneRecord> records = ZoneStorage.load(worldPath, dimension);
        final Map<UUID, ZoneData> loaded = Maps.newHashMap();
        for (final ZoneStorage.ZoneRecord record : records) {
            if (record.uuid == null) {
                continue;
            }
            final ZoneData zone = ZoneStorage.toZone(record);
            if (zone == null) {
                continue;
            }
            if (!zone.getId().getDimension().equals(dimension)) {
                continue;
            }
            loaded.put(zone.getId().getUuid(), zone);
            ZoneSpawnController.ensureZone(zone);
        }

        zones.put(dimension, loaded);

        final List<SpawnStorage.SpawnRecord> spawnRecords = SpawnStorage.load(worldPath, dimension);
        for (final SpawnStorage.SpawnRecord record : spawnRecords) {
            final ZoneData zone = loaded.get(record.zoneId);
            if (zone != null) {
                ZoneSpawnController.setRules(zone, record.rules);
            }
        }

        HellasWilds.LOGGER.info("Loaded {} HellasWilds zones for {}", loaded.size(), dimension.getLocation());
    }

    public synchronized void save(final ServerWorld world) {
        final RegistryKey<World> dimension = world.getDimensionKey();
        final Map<UUID, ZoneData> map = zones.get(dimension);
        final List<ZoneStorage.ZoneRecord> records = map == null ? new ArrayList<>() : map.values().stream()
                .map(ZoneStorage::fromZone)
                .collect(Collectors.toList());

        final Path worldPath = world.getServer().getWorldPath(FolderName.ROOT);
        ZoneStorage.save(worldPath, dimension, records);

        final List<SpawnStorage.SpawnRecord> spawnRecords = new ArrayList<>();
        if (map != null) {
            for (final ZoneData zone : map.values()) {
                final SpawnStorage.SpawnRecord record = new SpawnStorage.SpawnRecord();
                record.zoneId = zone.getId().getUuid();
                record.rules = new ArrayList<>(ZoneSpawnController.getRules(zone));
                spawnRecords.add(record);
            }
        }
        SpawnStorage.save(worldPath, dimension, spawnRecords);
    }
}
