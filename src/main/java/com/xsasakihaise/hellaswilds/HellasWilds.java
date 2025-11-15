package com.xsasakihaise.hellaswilds;

import com.xsasakihaise.hellaswilds.client.render.ClientFadeHandler;
import com.xsasakihaise.hellaswilds.client.render.GateBadgeRenderer;
import com.xsasakihaise.hellaswilds.client.render.TintHandlers;
import com.xsasakihaise.hellaswilds.client.render.VisualOverlayRenderer;
import com.xsasakihaise.hellaswilds.commands.WildsCommands;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import com.xsasakihaise.hellaswilds.registry.CommandRegistry;
import com.xsasakihaise.hellaswilds.registry.ItemRegistry;
import com.xsasakihaise.hellaswilds.registry.NetworkRegistry;
import com.xsasakihaise.hellaswilds.registry.TileRegistry;
import com.xsasakihaise.hellaswilds.spawns.PixelmonHook;
import com.xsasakihaise.hellaswilds.zone.ZoneCache;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for the HellasWilds mod. The implementation delivered here focuses on providing the
 * scaffolding, registries and configuration hooks that the full feature set will later plug into.
 * Complex behaviour – including Pixelmon integration, flood-fill zone detection, multi-block
 * placement and the remote management UI – is stubbed out with carefully documented TODO markers so
 * that future updates can drop the production-ready logic in place without reshaping the codebase.
 */
@Mod(HellasWilds.MOD_ID)
public final class HellasWilds {
    public static final String MOD_ID = "hellaswilds";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static boolean pixelmonPresent;
    private static boolean hellasFormsPresent;
    private static boolean featureGateOpen;

    /**
     * Constructs the mod entrypoint and registers every Forge listener as early as possible so both
     * logical sides receive the expected callbacks.
     */
    public HellasWilds() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BlockRegistry.BLOCKS.register(modBus);
        ItemRegistry.ITEMS.register(modBus);
        TileRegistry.TILES.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onWorldLoad);
        MinecraftForge.EVENT_BUS.addListener(this::onWorldSave);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WildsConfig.SPEC);

        evaluateDependencies();
    }

    /**
     * @return {@code true} when both Pixelmon and HellasForms are present which allows the mod to
     * perform its registrations.
     */
    public static boolean featuresEnabled() {
        return featureGateOpen;
    }

    /**
     * @return {@code true} once the dependency scan detects the Pixelmon mod. Helper utilities rely
     * on this check before invoking reflection heavy Pixelmon APIs.
     */
    public static boolean isPixelmonPresent() {
        return pixelmonPresent;
    }

    /**
     * Utility for generating a namespaced identifier within the HellasWilds domain.
     */
    public static ResourceLocation id(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /**
     * Inspects the loaded mod list and toggles the feature gate. This avoids partial initialisation
     * when the supporting mods are missing.
     */
    private void evaluateDependencies() {
        pixelmonPresent = ModList.get().isLoaded("pixelmon");
        hellasFormsPresent = ModList.get().isLoaded("hellasforms");

        if (!pixelmonPresent || !hellasFormsPresent) {
            featureGateOpen = false;
            if (!pixelmonPresent) {
                LOGGER.error("Pixelmon 9.1.13+ is required for HellasWilds. Features will remain disabled.");
            }
            if (!hellasFormsPresent) {
                LOGGER.error("HellasForms must be loaded before HellasWilds. Features will remain disabled.");
            }
        } else {
            featureGateOpen = true;
        }
    }

    /**
     * Registers shared side infrastructure (commands, networking, spawn hooks) once the mod loading
     * pipeline reaches the common setup phase.
     */
    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!featuresEnabled()) {
                LOGGER.warn("HellasWilds initialised in dependency-limited mode. Registrations are skipped.");
                return;
            }

            CommandRegistry.bootstrap();
            NetworkRegistry.bootstrap();
            PixelmonHook.bootstrap();
        });
    }

    /**
     * Performs client-only registrations such as tint handlers and tile renderers.
     */
    private void onClientSetup(final FMLClientSetupEvent event) {
        NetworkRegistry.prepareClient();
        event.enqueueWork(() -> {
            TintHandlers.register();
            ClientFadeHandler.register();
            ClientRegistry.bindTileEntityRenderer(TileRegistry.GATE_BADGE_TILE.get(), GateBadgeRenderer::new);
            VisualOverlayRenderer.register();
        });
    }

    /**
     * Clears transient zone caches whenever a dedicated server instance is about to boot.
     */
    private void onServerAboutToStart(final FMLServerAboutToStartEvent event) {
        ZoneCache.get().invalidateAll();
    }

    /**
     * Loads zone data for each world as it becomes available.
     */
    private void onWorldLoad(final WorldEvent.Load event) {
        if (event.getWorld() instanceof ServerWorld) {
            ZoneCache.get().load((ServerWorld) event.getWorld());
        }
    }

    /**
     * Persists zone definitions whenever a world flushes to disk.
     */
    private void onWorldSave(final WorldEvent.Save event) {
        if (event.getWorld() instanceof ServerWorld) {
            ZoneCache.get().save((ServerWorld) event.getWorld());
        }
    }

    /**
     * Saves every zone and shuts down the optional web UI when the server stops.
     */
    private void onServerStopping(final FMLServerStoppingEvent event) {
        for (final ServerWorld world : event.getServer().getWorlds()) {
            ZoneCache.get().save(world);
        }
        WildsCommands.stopWebServer();
    }

    /**
     * Hooks into Forge's command registration event and adds the /hellas wilds namespace.
     */
    private void onRegisterCommands(final RegisterCommandsEvent event) {
        WildsCommands.register(event.getDispatcher());
    }
}
