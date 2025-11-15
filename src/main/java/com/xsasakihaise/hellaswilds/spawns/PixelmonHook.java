package com.xsasakihaise.hellaswilds.spawns;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.zone.ZoneCache;
import com.xsasakihaise.hellaswilds.zone.ZoneData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.UUID;

/**
 * Integrates with Forge lifecycle events to police Pixelmon spawns inside managed zones. The
 * heavy-lifting spawn injection still needs to call into Pixelmon's API, but this hook already
 * enforces override zones and tracks active entity counts for the controller.
 */
public final class PixelmonHook {
    private static final String TAG_ZONE = "HellasWildsZone";
    private static final String TAG_INTERNAL = "HellasWildsInternal";

    private PixelmonHook() {
    }

    /**
     * Registers the Forge listeners that monitor Pixelmon spawns and keep zone caps in sync.
     */
    public static void bootstrap() {
        if (!HellasWilds.isPixelmonPresent()) {
            HellasWilds.LOGGER.warn("Pixelmon is not present; HellasWilds spawn overrides remain inactive.");
            return;
        }

        MinecraftForge.EVENT_BUS.addListener(PixelmonHook::onEntityJoinWorld);
        MinecraftForge.EVENT_BUS.addListener(PixelmonHook::onEntityLeaveWorld);
        MinecraftForge.EVENT_BUS.addListener(PixelmonHook::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(ZoneSpawnController::onWorldTick);
        HellasWilds.LOGGER.info("Pixelmon integration active: override zones will cancel native spawns when required.");
    }

    /**
     * Cancels Pixelmon spawns when a zone is at cap or is configured in override mode.
     */
    private static void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote() || event.isCanceled() || event.isLoadedFromDisk()) {
            return;
        }
        if (!(event.getWorld() instanceof ServerWorld)) {
            return;
        }

        final Entity entity = event.getEntity();
        if (!isPixelmon(entity)) {
            return;
        }

        final ServerWorld world = (ServerWorld) event.getWorld();
        final ZoneData zone = ZoneCache.get().findZone(world, entity);
        if (zone == null) {
            return;
        }

        final CompoundNBT data = entity.getPersistentData();
        final boolean internalSpawn = data.getBoolean(TAG_INTERNAL);
        if (ZoneSpawnController.isAtCap(zone)) {
            event.setCanceled(true);
            return;
        }
        if ("override".equalsIgnoreCase(zone.getSpawnMode()) && !internalSpawn) {
            event.setCanceled(true);
            return;
        }

        data.putUniqueId(TAG_ZONE, zone.getId().getUuid());
        data.remove(TAG_INTERNAL);
        ZoneSpawnController.trackSpawn(zone, entity.getUniqueID());
    }

    /**
     * Releases slot reservations when an entity unloads naturally (e.g. chunks unloading).
     */
    private static void onEntityLeaveWorld(final EntityLeaveWorldEvent event) {
        if (event.getWorld().isRemote()) {
            return;
        }
        final Entity entity = event.getEntity();
        if (!isPixelmon(entity)) {
            return;
        }
        final CompoundNBT data = entity.getPersistentData();
        if (data.hasUniqueId(TAG_ZONE)) {
            ZoneSpawnController.releaseSpawn(data.getUniqueId(TAG_ZONE), entity.getUniqueID());
            data.remove(TAG_ZONE);
        }
    }

    /**
     * Ensures the spawn controller is notified when a tracked Pixelmon faints or dies.
     */
    private static void onLivingDeath(final LivingDeathEvent event) {
        final LivingEntity entity = event.getEntityLiving();
        if (entity.world.isRemote() || !isPixelmon(entity)) {
            return;
        }
        final CompoundNBT data = entity.getPersistentData();
        if (data.hasUniqueId(TAG_ZONE)) {
            ZoneSpawnController.releaseSpawn(data.getUniqueId(TAG_ZONE), entity.getUniqueID());
            data.remove(TAG_ZONE);
        }
    }

    private static boolean isPixelmon(final Entity entity) {
        return entity.getType().getRegistryName() != null && "pixelmon".equals(entity.getType().getRegistryName().getNamespace());
    }

    /**
     * Marks entities spawned by {@link ZoneSpawnController} so {@link #onEntityJoinWorld} knows to
     * accept them even inside override zones.
     */
    public static void markInternalSpawn(final Entity entity, final UUID zoneId) {
        final CompoundNBT data = entity.getPersistentData();
        data.putBoolean(TAG_INTERNAL, true);
        data.putUniqueId(TAG_ZONE, zoneId);
    }
}
