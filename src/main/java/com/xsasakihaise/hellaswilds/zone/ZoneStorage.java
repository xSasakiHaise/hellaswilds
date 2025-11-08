package com.xsasakihaise.hellaswilds.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xsasakihaise.hellaswilds.HellasWilds;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles reading and writing zone JSON files. The storage layout mirrors the production
 * specification and now persists complete metadata used throughout the mod.
 */
public final class ZoneStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ZoneRecord>>() {}.getType();

    private ZoneStorage() {
    }

    public static List<ZoneRecord> load(final Path worldPath, final RegistryKey<World> dimension) {
        final Path file = resolveFile(worldPath, dimension);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            final List<ZoneRecord> records = GSON.fromJson(reader, LIST_TYPE);
            return records == null ? new ArrayList<>() : records;
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Failed to read zones from {}", file, e);
            return new ArrayList<>();
        }
    }

    public static void save(final Path worldPath, final RegistryKey<World> dimension, final List<ZoneRecord> records) {
        final Path file = resolveFile(worldPath, dimension);
        try {
            Files.createDirectories(file.getParent());
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Unable to create zone directory for {}", file, e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(records, LIST_TYPE, writer);
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Failed to save zones to {}", file, e);
        }
    }

    public static ZoneRecord fromZone(final ZoneData zone) {
        final ZoneRecord record = new ZoneRecord();
        record.uuid = zone.getId().getUuid();
        record.displayNumber = zone.getId().getDisplayNumber();
        record.color = zone.getId().getColor();
        record.ownerType = zone.getId().getOwnerType();
        record.region = zone.getId().getRegion();
        record.dimension = zone.getId().getDimension().getLocation().toString();
        record.gateX = zone.getGatePos().getX();
        record.gateY = zone.getGatePos().getY();
        record.gateZ = zone.getGatePos().getZ();
        record.bounds = BoxRecord.from(zone.getBounds());
        record.overlay = zone.getOverlay().stream().map(BoxRecord::from).collect(Collectors.toList());
        record.spawnMode = zone.getSpawnMode();
        record.spawnCap = zone.getSpawnCap();
        return record;
    }

    @Nullable
    public static ZoneData toZone(final ZoneRecord record) {
        if (record.dimension == null || record.dimension.isEmpty()) {
            HellasWilds.LOGGER.error("Skipping zone {} with missing dimension id", record.uuid);
            return null;
        }
        final ResourceLocation dimensionId = ResourceLocation.tryCreate(record.dimension);
        if (dimensionId == null) {
            HellasWilds.LOGGER.error("Skipping zone {} with invalid dimension id {}", record.uuid, record.dimension);
            return null;
        }

        final RegistryKey<World> dimension = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, dimensionId);
        final ZoneId id = new ZoneId(record.uuid, record.displayNumber, record.color, record.region, record.ownerType, dimension);
        final AxisAlignedBB bounds = record.bounds != null ? record.bounds.toBox() : new AxisAlignedBB(record.gateX, record.gateY, record.gateZ, record.gateX + 1, record.gateY + 3, record.gateZ + 1);
        final List<AxisAlignedBB> overlay = record.overlay == null ? new ArrayList<>() : record.overlay.stream().map(BoxRecord::toBox).collect(Collectors.toList());
        final BlockPos gatePos = new BlockPos(record.gateX, record.gateY, record.gateZ);
        return new ZoneData(id, gatePos, bounds, overlay, record.spawnMode == null ? "additive" : record.spawnMode, record.spawnCap <= 0 ? 20 : record.spawnCap);
    }

    private static Path resolveFile(final Path worldPath, final RegistryKey<World> dimension) {
        final String dimensionFolder = dimension.getLocation().toString().replace(':', '_');
        return worldPath.resolve("data/hellaswilds").resolve(dimensionFolder).resolve("zones.json");
    }

    public static final class ZoneRecord {
        public UUID uuid;
        public int displayNumber;
        public int color;
        public String ownerType;
        public String region;
        public String dimension;
        public int gateX;
        public int gateY;
        public int gateZ;
        public BoxRecord bounds;
        public List<BoxRecord> overlay = new ArrayList<>();
        public String spawnMode = "additive";
        public int spawnCap = 20;
    }

    public static final class BoxRecord {
        public double minX;
        public double minY;
        public double minZ;
        public double maxX;
        public double maxY;
        public double maxZ;

        public static BoxRecord from(final AxisAlignedBB box) {
            final BoxRecord record = new BoxRecord();
            record.minX = box.minX;
            record.minY = box.minY;
            record.minZ = box.minZ;
            record.maxX = box.maxX;
            record.maxY = box.maxY;
            record.maxZ = box.maxZ;
            return record;
        }

        public AxisAlignedBB toBox() {
            return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
