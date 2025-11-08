package com.xsasakihaise.hellaswilds.spawns;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.zone.ZoneCache;
import com.xsasakihaise.hellaswilds.zone.ZoneData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Runtime controller that owns per-zone spawning state, evaluates configured rules and pushes
 * Pokemon entities into the world using Pixelmon's factory APIs. The implementation avoids direct
 * compile-time dependencies on Pixelmon by leaning on reflection; in environments where the API
 * surface cannot be resolved, the controller degrades gracefully while logging the failure.
 */
public final class ZoneSpawnController {
    private static final ConcurrentMap<UUID, ZoneRuntime> RUNTIMES = Maps.newConcurrentMap();
    private static final Random RANDOM = new Random();
    private static final int MAX_SPAWN_ATTEMPTS = 4;
    private static final int RULE_RETRY_DELAY = 20;
    private static final int SOFT_DESPAWN_GRACE_TICKS = 20 * 30;

    private ZoneSpawnController() {
    }

    public static void ensureZone(final ZoneData zone) {
        RUNTIMES.computeIfAbsent(zone.getId().getUuid(), id -> new ZoneRuntime(zone));
    }

    public static void updateZone(final ZoneData zone) {
        RUNTIMES.compute(zone.getId().getUuid(), (id, existing) -> existing == null ? new ZoneRuntime(zone) : existing.update(zone));
    }

    public static void removeZone(final ZoneData zone) {
        RUNTIMES.remove(zone.getId().getUuid());
    }

    public static void setRules(final ZoneData zone, final List<ZoneSpawnRule> rules) {
        ensureZone(zone);
        RUNTIMES.get(zone.getId().getUuid()).setRules(rules);
    }

    public static List<ZoneSpawnRule> getRules(final ZoneData zone) {
        final ZoneRuntime runtime = RUNTIMES.get(zone.getId().getUuid());
        return runtime == null ? Collections.emptyList() : runtime.getRules();
    }

    public static boolean isAtCap(final ZoneData zone) {
        final ZoneRuntime runtime = RUNTIMES.get(zone.getId().getUuid());
        return runtime != null && runtime.getActiveCount() >= zone.getSpawnCap();
    }

    public static void trackSpawn(final ZoneData zone, final UUID entityId) {
        ensureZone(zone);
        RUNTIMES.get(zone.getId().getUuid()).onSpawn(entityId);
    }

    public static void releaseSpawn(final ZoneData zone, final UUID entityId) {
        final ZoneRuntime runtime = RUNTIMES.get(zone.getId().getUuid());
        if (runtime != null) {
            runtime.onRelease(entityId);
        }
    }

    public static void releaseSpawn(final UUID zoneId, final UUID entityId) {
        final ZoneRuntime runtime = RUNTIMES.get(zoneId);
        if (runtime != null) {
            runtime.onRelease(entityId);
        }
    }

    public static void onWorldTick(final TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.world instanceof ServerWorld)) {
            return;
        }
        final ServerWorld world = (ServerWorld) event.world;
        final RegistryKey<World> dimension = world.getDimensionKey();
        final Collection<ZoneData> zones = ZoneCache.get().values(dimension);
        if (zones.isEmpty()) {
            return;
        }
        for (final ZoneData zone : zones) {
            final ZoneRuntime runtime = RUNTIMES.get(zone.getId().getUuid());
            if (runtime != null) {
                runtime.update(zone);
                runtime.tick(world);
            }
        }
    }

    private static boolean matchesTime(final ZoneSpawnRule rule, final ServerWorld world) {
        if (rule.time == null || rule.time.isEmpty()) {
            return true;
        }
        final long dayTime = world.getDayTime() % 24000L;
        for (final String entry : rule.time) {
            final String token = entry == null ? "" : entry.toLowerCase();
            switch (token) {
                case "day":
                    if (dayTime >= 1000 && dayTime < 12000) {
                        return true;
                    }
                    break;
                case "night":
                    if (dayTime >= 13000 || dayTime < 1000) {
                        return true;
                    }
                    break;
                case "dusk":
                    if (dayTime >= 12000 && dayTime < 14000) {
                        return true;
                    }
                    break;
                case "dawn":
                    if (dayTime >= 22000 || dayTime < 1000) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean matchesWeather(final ZoneSpawnRule rule, final ServerWorld world) {
        if (rule.weather == null || rule.weather.isEmpty()) {
            return true;
        }
        final boolean isRaining = world.isRaining();
        final boolean isThundering = world.isThundering();
        for (final String entry : rule.weather) {
            final String token = entry == null ? "" : entry.toLowerCase();
            switch (token) {
                case "clear":
                    if (!isRaining && !isThundering) {
                        return true;
                    }
                    break;
                case "rain":
                    if (isRaining && !isThundering) {
                        return true;
                    }
                    break;
                case "storm":
                case "thunder":
                    if (isThundering) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean matchesZone(final ZoneData zone, final Entity entity) {
        if (entity == null) {
            return false;
        }
        final AxisAlignedBB box = zone.getBounds();
        return box.grow(0.1D).intersects(entity.getBoundingBox());
    }

    private static BlockPos pickSpawnPosition(final ServerWorld world, final ZoneData zone) {
        final AxisAlignedBB bounds = zone.getBounds();
        for (int attempt = 0; attempt < 20; attempt++) {
            final double x = MathHelper.nextDouble(RANDOM, bounds.minX, bounds.maxX);
            final double z = MathHelper.nextDouble(RANDOM, bounds.minZ, bounds.maxZ);
            final int blockX = MathHelper.floor(x);
            final int blockZ = MathHelper.floor(z);
            if (!world.isBlockPresent(new BlockPos(blockX, (int) bounds.minY, blockZ))) {
                continue;
            }
            final int topY = world.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            if (topY < bounds.minY || topY > bounds.maxY) {
                continue;
            }
            final BlockPos pos = new BlockPos(blockX, topY, blockZ);
            final Vector3d vec = new Vector3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (bounds.contains(vec)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean trySpawn(final ServerWorld world, final ZoneRuntime runtime, final RuleRuntime ruleRuntime) {
        final ZoneSpawnRule rule = ruleRuntime.rule;
        final BlockPos spawnPos = pickSpawnPosition(world, runtime.zone);
        if (spawnPos == null) {
            return false;
        }
        final LivingEntity entity = PixelmonFactory.createPixelmon(world, rule);
        if (entity == null) {
            return false;
        }
        PixelmonHook.markInternalSpawn(entity, runtime.zone.getId().getUuid());
        runtime.registerPendingSpawn(entity.getUniqueID(), rule);
        entity.setLocationAndAngles(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                RANDOM.nextFloat() * 360.0F, 0.0F);
        return world.addEntity(entity);
    }

    private static final class ZoneRuntime {
        private ZoneData zone;
        private List<ZoneSpawnRule> rules = Lists.newArrayList();
        private List<RuleRuntime> compiledRules = Lists.newArrayList();
        private final Set<UUID> activeEntities = Sets.newConcurrentHashSet();
        private final Map<UUID, TrackedSpawn> tracked = Maps.newConcurrentMap();
        private final Map<UUID, ZoneSpawnRule> pending = Maps.newConcurrentMap();

        private ZoneRuntime(final ZoneData zone) {
            this.zone = zone;
        }

        private ZoneRuntime update(final ZoneData zone) {
            this.zone = zone;
            return this;
        }

        private void setRules(final List<ZoneSpawnRule> rules) {
            this.rules = Lists.newArrayList(rules);
            this.compiledRules = this.rules.stream().map(RuleRuntime::new).collect(Collectors.toList());
        }

        private List<ZoneSpawnRule> getRules() {
            return Collections.unmodifiableList(rules);
        }

        private int getActiveCount() {
            return activeEntities.size();
        }

        private void onSpawn(final UUID entityId) {
            final ZoneSpawnRule origin = pending.remove(entityId);
            activeEntities.add(entityId);
            if (origin != null) {
                tracked.put(entityId, new TrackedSpawn(origin));
            }
        }

        private void registerPendingSpawn(final UUID entityId, final ZoneSpawnRule rule) {
            pending.put(entityId, rule);
        }

        private void onRelease(final UUID entityId) {
            activeEntities.remove(entityId);
            pending.remove(entityId);
            tracked.remove(entityId);
        }

        private void tick(final ServerWorld world) {
            prune(world);
            if (zone.getSpawnCap() <= activeEntities.size()) {
                return;
            }
            for (final RuleRuntime runtime : compiledRules) {
                runtime.cooldown();
            }
            final int freeSlots = Math.max(0, zone.getSpawnCap() - activeEntities.size());
            if (freeSlots == 0) {
                return;
            }
            int attempts = Math.min(MAX_SPAWN_ATTEMPTS, freeSlots);
            while (attempts-- > 0) {
                final RuleRuntime runtime = selectRule(world);
                if (runtime == null) {
                    break;
                }
                if (trySpawn(world, this, runtime)) {
                    runtime.onSpawned();
                } else {
                    runtime.onFailed();
                }
            }
        }

        private void prune(final ServerWorld world) {
            final Iterator<UUID> iterator = activeEntities.iterator();
            while (iterator.hasNext()) {
                final UUID id = iterator.next();
                final Entity entity = world.getEntityByUuid(id);
                if (!(entity instanceof LivingEntity) || entity.isRemoved()) {
                    iterator.remove();
                    tracked.remove(id);
                    pending.remove(id);
                    continue;
                }
                final TrackedSpawn trackedSpawn = tracked.get(id);
                if (trackedSpawn != null) {
                    if (!matchesZone(zone, entity)) {
                        iterator.remove();
                        tracked.remove(id);
                        pending.remove(id);
                        continue;
                    }
                    if (trackedSpawn.tick(world, zone, (LivingEntity) entity)) {
                        if (!(entity instanceof PlayerEntity)) {
                            entity.remove();
                        }
                        iterator.remove();
                        tracked.remove(id);
                        pending.remove(id);
                    }
                }
            }
        }

        private RuleRuntime selectRule(final ServerWorld world) {
            final List<RuleRuntime> eligible = new ArrayList<>();
            double totalWeight = 0.0D;
            for (final RuleRuntime runtime : compiledRules) {
                if (runtime.isEligible(world, zone)) {
                    eligible.add(runtime);
                    totalWeight += Math.max(0.0D, runtime.rule.weight);
                }
            }
            if (eligible.isEmpty() || totalWeight <= 0.0D) {
                return null;
            }
            double roll = RANDOM.nextDouble() * totalWeight;
            for (final RuleRuntime runtime : eligible) {
                roll -= Math.max(0.0D, runtime.rule.weight);
                if (roll <= 0.0D) {
                    return runtime;
                }
            }
            return eligible.get(eligible.size() - 1);
        }
    }

    private static final class RuleRuntime {
        private final ZoneSpawnRule rule;
        private int cooldownTicks;
        private int retryTicks;

        private RuleRuntime(final ZoneSpawnRule rule) {
            this.rule = rule;
        }

        private void cooldown() {
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }
            if (retryTicks > 0) {
                retryTicks--;
            }
        }

        private boolean isEligible(final ServerWorld world, final ZoneData zone) {
            if (rule == null) {
                return false;
            }
            if (cooldownTicks > 0 || retryTicks > 0) {
                return false;
            }
            if (rule.weight <= 0.0D) {
                return false;
            }
            if (!matchesTime(rule, world)) {
                return false;
            }
            return matchesWeather(rule, world);
        }

        private void onSpawned() {
            cooldownTicks = Math.max(0, rule.cooldownSeconds == null ? 0 : rule.cooldownSeconds * 20);
            retryTicks = 0;
        }

        private void onFailed() {
            retryTicks = RULE_RETRY_DELAY;
        }
    }

    private static final class TrackedSpawn {
        private final ZoneSpawnRule rule;
        private int graceTicks;

        private TrackedSpawn(final ZoneSpawnRule rule) {
            this.rule = rule;
            this.graceTicks = rule != null && rule.softDespawn ? SOFT_DESPAWN_GRACE_TICKS : -1;
        }

        private boolean tick(final ServerWorld world, final ZoneData zone, final LivingEntity entity) {
            if (rule == null || !rule.softDespawn) {
                return false;
            }
            final boolean conditionsMet = matchesTime(rule, world) && matchesWeather(rule, world) && matchesZone(zone, entity);
            if (conditionsMet) {
                graceTicks = SOFT_DESPAWN_GRACE_TICKS;
                return false;
            }
            if (graceTicks < 0) {
                return false;
            }
            graceTicks--;
            return graceTicks <= 0;
        }
    }

    private static final class PixelmonFactory {
        private static final ResourceLocation PIXELMON_ENTITY_ID = new ResourceLocation("pixelmon", "pixelmon");
        private static boolean loggedFailure;
        private static Class<?> pokemonClass;
        private static Class<?> builderClass;
        private static Method builderCreate;
        private static Method builderBuild;
        private static Method pokemonSetLevel;
        private static Method pokemonSetForm;
        private static Method pokemonSetGrowth;
        private static Method pokemonAddRibbon;
        private static Method pokemonSetPalette;

        private PixelmonFactory() {
        }

        @Nullable
        private static LivingEntity createPixelmon(final ServerWorld world, final ZoneSpawnRule rule) {
            if (!HellasWilds.isPixelmonPresent()) {
                return null;
            }
            if (rule == null || rule.species == null || rule.species.isEmpty()) {
                logFailureOnce("Zone spawn rule missing species identifier");
                return null;
            }
            final Entity entity;
            if (ForgeRegistries.ENTITIES.getValue(PIXELMON_ENTITY_ID) == null) {
                entity = null;
            } else {
                entity = ForgeRegistries.ENTITIES.getValue(PIXELMON_ENTITY_ID).create(world);
            }
            if (!(entity instanceof LivingEntity)) {
                logFailureOnce("Unable to resolve Pixelmon entity type");
                return null;
            }

            final Object pokemon = buildPokemon(rule);
            if (pokemon == null) {
                logFailureOnce("Failed to construct Pixelmon Pokemon instance");
                return null;
            }
            if (!applyPokemonToEntity(entity, pokemon)) {
                logFailureOnce("Failed to attach Pokemon data to entity");
                return null;
            }
            return (LivingEntity) entity;
        }

        @Nullable
        private static Object buildPokemon(final ZoneSpawnRule rule) {
            try {
                ensureBuilderMethods();
                if (builderClass == null || builderCreate == null || builderBuild == null) {
                    return null;
                }
                final Object builder = builderCreate.invoke(null, rule.species);
                final Object pokemon = builderBuild.invoke(builder);
                configurePokemon(pokemon, rule);
                return pokemon;
            } catch (final IllegalAccessException | InvocationTargetException e) {
                logFailureOnce("Pixelmon builder invocation failed: " + e.getMessage());
                return null;
            }
        }

        private static void configurePokemon(final Object pokemon, final ZoneSpawnRule rule) {
            if (pokemon == null) {
                return;
            }
            try {
                ensureBuilderMethods();
                if (pokemonClass == null) {
                    pokemonClass = pokemon.getClass();
                }
                if (pokemonSetLevel == null) {
                    pokemonSetLevel = findMethod(pokemon.getClass(), new String[]{"setLevel", "setLevelAndRefresh"}, int.class);
                }
                if (pokemonSetForm == null) {
                    pokemonSetForm = findMethod(pokemon.getClass(), new String[]{"setForm", "setPokemonForm"}, String.class);
                }
                if (pokemonSetGrowth == null) {
                    pokemonSetGrowth = findMethod(pokemon.getClass(), new String[]{"setGrowth", "setSize"}, (Class<?>) null);
                }
                if (pokemonAddRibbon == null) {
                    pokemonAddRibbon = findMethod(pokemon.getClass(), new String[]{"addRibbon", "giveRibbon"}, String.class);
                }
                if (pokemonSetPalette == null) {
                    pokemonSetPalette = findMethod(pokemon.getClass(), new String[]{"setPalette", "setTexture"}, String.class);
                }

                final int min = Math.max(1, rule.levelMin);
                final int max = Math.max(min, rule.levelMax <= 0 ? min : rule.levelMax);
                final int level = MathHelper.nextInt(RANDOM, min, max);
                if (pokemonSetLevel != null) {
                    pokemonSetLevel.invoke(pokemon, level);
                }
                if (rule.form != null && !rule.form.isEmpty() && pokemonSetForm != null) {
                    pokemonSetForm.invoke(pokemon, rule.form);
                }
                if (rule.size != null && !rule.size.isEmpty() && pokemonSetGrowth != null) {
                    final Class<?> growthClass = pokemonSetGrowth.getParameterTypes()[0];
                    final Object growth = findEnumConstant(growthClass, rule.size);
                    if (growth != null) {
                        pokemonSetGrowth.invoke(pokemon, growth);
                    }
                }
                if (rule.ribbons != null && !rule.ribbons.isEmpty() && pokemonAddRibbon != null) {
                    for (final String ribbon : rule.ribbons) {
                        if (ribbon != null && !ribbon.isEmpty()) {
                            pokemonAddRibbon.invoke(pokemon, ribbon);
                        }
                    }
                }
                if (rule.alphaRibbon && pokemonSetPalette != null) {
                    pokemonSetPalette.invoke(pokemon, "alpha");
                }
            } catch (final IllegalAccessException | InvocationTargetException e) {
                logFailureOnce("Failed to configure Pokemon instance: " + e.getMessage());
            }
        }

        private static boolean applyPokemonToEntity(final Entity entity, final Object pokemon) {
            try {
                final Method setPokemon = findMethod(entity.getClass(), new String[]{"setPokemon", "setPokemonData"}, (Class<?>) null);
                if (setPokemon == null) {
                    return false;
                }
                setPokemon.invoke(entity, pokemon);
                return true;
            } catch (final IllegalAccessException | InvocationTargetException e) {
                logFailureOnce("Unable to bind Pokemon to entity: " + e.getMessage());
                return false;
            }
        }

        private static void ensureBuilderMethods() {
            if (builderClass != null && builderCreate != null && builderBuild != null) {
                return;
            }
            try {
                builderClass = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonBuilder");
                builderCreate = findMethod(builderClass, new String[]{"create", "builder"}, String.class);
                builderBuild = findMethod(builderClass, new String[]{"build", "toPokemon"});
            } catch (final ClassNotFoundException e) {
                logFailureOnce("Pixelmon PokemonBuilder class unavailable");
            }
        }

        @Nullable
        private static Method findMethod(final Class<?> owner, final String[] names, final Class<?>... params) {
            if (owner == null) {
                return null;
            }
            outer:
            for (final String name : names) {
                for (final Method method : owner.getMethods()) {
                    if (!method.getName().equals(name)) {
                        continue;
                    }
                    final Class<?>[] methodParams = method.getParameterTypes();
                    if (params == null) {
                        method.setAccessible(true);
                        return method;
                    }
                    if (methodParams.length != params.length) {
                        continue;
                    }
                    for (int i = 0; i < params.length; i++) {
                        final Class<?> expected = params[i];
                        if (expected == null) {
                            continue;
                        }
                        if (!methodParams[i].isAssignableFrom(expected)) {
                            continue outer;
                        }
                    }
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }

        @Nullable
        private static Object findEnumConstant(final Class<?> type, final String name) {
            if (type == null || !type.isEnum()) {
                return null;
            }
            for (final Object constant : type.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
            return null;
        }

        private static void logFailureOnce(final String message) {
            if (!loggedFailure) {
                HellasWilds.LOGGER.error(message);
                loggedFailure = true;
            }
        }
    }
}
