package com.xsasakihaise.hellaswilds.spawns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xsasakihaise.hellaswilds.HellasWilds;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JSON storage for per-zone spawn rule sets.
 */
public final class SpawnStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<SpawnRecord>>() {}.getType();

    private SpawnStorage() {
    }

    public static List<SpawnRecord> load(final Path worldPath, final RegistryKey<World> dimension) {
        final Path file = resolveFile(worldPath, dimension);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            final List<SpawnRecord> records = GSON.fromJson(reader, LIST_TYPE);
            if (records == null) {
                return new ArrayList<>();
            }
            for (final SpawnRecord record : records) {
                if (record.rules == null) {
                    record.rules = new ArrayList<>();
                }
            }
            return records;
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Failed to read spawns from {}", file, e);
            return new ArrayList<>();
        }
    }

    public static void save(final Path worldPath, final RegistryKey<World> dimension, final List<SpawnRecord> records) {
        final Path file = resolveFile(worldPath, dimension);
        try {
            Files.createDirectories(file.getParent());
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Unable to create spawn directory for {}", file, e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(records, LIST_TYPE, writer);
        } catch (final IOException e) {
            HellasWilds.LOGGER.error("Failed to save spawns to {}", file, e);
        }
    }

    private static Path resolveFile(final Path worldPath, final RegistryKey<World> dimension) {
        final String dimensionFolder = dimension.getLocation().toString().replace(':', '_');
        return worldPath.resolve("data/hellaswilds").resolve(dimensionFolder).resolve("spawns.json");
    }

    public static final class SpawnRecord {
        public UUID zoneId;
        public List<ZoneSpawnRule> rules = new ArrayList<>();
    }
}
