package com.xsasakihaise.hellaswilds.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.WildsConfig;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeTile;
import com.xsasakihaise.hellaswilds.gate.GateLinker;
import com.xsasakihaise.hellaswilds.registry.NetworkRegistry;
import com.xsasakihaise.hellaswilds.spawns.ZoneSpawnController;
import com.xsasakihaise.hellaswilds.spawns.ZoneSpawnRule;
import com.xsasakihaise.hellaswilds.webui.PlayitIntegration;
import com.xsasakihaise.hellaswilds.webui.WebServer;
import com.xsasakihaise.hellaswilds.zone.VisualOverlayS2CPacket;
import com.xsasakihaise.hellaswilds.zone.ZoneCache;
import com.xsasakihaise.hellaswilds.zone.ZoneData;
import com.xsasakihaise.hellaswilds.zone.ZoneDetector;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registers and implements the /hellas wilds command family, covering zone management, locking,
 * visualisation controls, and spawn configuration helpers.
 */
public final class WildsCommands {
    private static final SimpleCommandExceptionType FEATURES_DISABLED =
            new SimpleCommandExceptionType(new StringTextComponent("HellasWilds features are disabled because dependencies are missing."));
    private static final SimpleCommandExceptionType NO_GATE =
            new SimpleCommandExceptionType(new StringTextComponent("No linked gate badge found within 20 blocks."));
    private static final DynamicCommandExceptionType INVALID_OWNER =
            new DynamicCommandExceptionType(owner -> new StringTextComponent("Unknown owner type '" + owner + "'. Expected admin or player."));
    private static final SimpleCommandExceptionType NO_ZONE =
            new SimpleCommandExceptionType(new StringTextComponent("You are not standing inside a managed wild zone."));
    private static final SimpleCommandExceptionType ZONE_OVERFLOW =
            new SimpleCommandExceptionType(new StringTextComponent("Zone detection exceeded the safety limit. Ensure the gate is fully enclosed."));

    private static WebServer activeWebServer;

    private WildsCommands() {
    }

    /**
     * Registers the /hellas wilds command tree which exposes zone creation, locking and spawn rule
     * tweaks to staff.
     */
    public static void register(final CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("hellas")
                .requires(source -> source.hasPermissionLevel(2))
                .then(Commands.literal("wilds")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(new StringTextComponent("HellasWilds " + HellasWilds.MOD_ID), false);
                            return 1;
                        })
                        .then(Commands.literal("createzone")
                                .then(Commands.argument("number", IntegerArgumentType.integer(0, 999))
                                        .then(Commands.argument("owner", StringArgumentType.word())
                                                .executes(ctx -> createZone(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "number"),
                                                        StringArgumentType.getString(ctx, "owner"),
                                                        ""))
                                                .then(Commands.argument("region", StringArgumentType.greedyString())
                                                        .executes(ctx -> createZone(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "number"),
                                                                StringArgumentType.getString(ctx, "owner"),
                                                                StringArgumentType.getString(ctx, "region")))))))
                        .then(Commands.literal("lock").executes(ctx -> setLock(ctx.getSource(), true)))
                        .then(Commands.literal("unlock").executes(ctx -> setLock(ctx.getSource(), false)))
                        .then(Commands.literal("visualize")
                                .then(Commands.literal("on").executes(ctx -> visualize(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> visualize(ctx.getSource(), false))))
                        .then(Commands.literal("spawns")
                                .executes(ctx -> listSpawns(ctx.getSource()))
                                .then(Commands.literal("edit").executes(ctx -> openEditor(ctx.getSource())))
                                .then(Commands.literal("mode")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                                .then(Commands.literal("cap")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 128))
                                                .executes(ctx -> setCap(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount"))))))));
    }

    /**
     * Shuts down the embedded HTTP server when the hosting world unloads or the command is rerun.
     */
    public static void stopWebServer() {
        if (activeWebServer != null) {
            activeWebServer.stop();
            activeWebServer = null;
        }
    }

    /**
     * Flood-fills the gate interior and persists a new {@link ZoneData} entry bound to the closest
     * gate.
     */
    private static int createZone(final CommandSource source, final int number, final String ownerArg, final String region) throws CommandSyntaxException {
        ensureFeatures();
        final String owner = ownerArg.toLowerCase(Locale.ROOT);
        if (!"admin".equals(owner) && !"player".equals(owner)) {
            throw INVALID_OWNER.create(ownerArg);
        }

        final ServerWorld world = source.getWorld();
        final GateContext gate = findGate(world, new BlockPos(source.getPos()));
        final ZoneDetector.Result detection = ZoneDetector.detect(world, gate.pos, gate.tile);
        if (detection.isOverflow() || detection.getOverlay().isEmpty()) {
            throw ZONE_OVERFLOW.create();
        }

        final UUID uuid = UUID.randomUUID();
        final ZoneId id = new ZoneId(uuid, number, gate.color, region == null ? "" : region, owner, world.getDimensionKey());
        final ZoneData zone = new ZoneData(id, gate.pos, detection.getBounds(), detection.getOverlay(), "additive", 20);

        gate.tile.setZoneId(id);
        gate.tile.setDisplayNumber(number);
        gate.tile.cacheBounds(detection.getBounds());
        gate.tile.markDirty();

        ZoneCache.get().put(zone);
        ZoneSpawnController.ensureZone(zone);
        ZoneCache.get().save(world);

        source.sendFeedback(new StringTextComponent("Created zone " + number + " (#" + uuid.toString().substring(0, 8) + ")"), true);
        return 1;
    }

    /**
     * Toggles barrier locking for the gate nearest to the executing source.
     */
    private static int setLock(final CommandSource source, final boolean locked) throws CommandSyntaxException {
        ensureFeatures();
        final ServerWorld world = source.getWorld();
        final GateContext gate = findGate(world, new BlockPos(source.getPos()));
        gate.tile.setLocked(locked);
        gate.tile.applyLockState();
        source.sendFeedback(new StringTextComponent((locked ? "Locked" : "Unlocked") + " gate " + gate.description()), true);
        return 1;
    }

    /**
     * Turns the translucent zone overlay on or off for the requesting staff member.
     */
    private static int visualize(final CommandSource source, final boolean enable) throws CommandSyntaxException {
        ensureFeatures();
        final ServerPlayerEntity player = source.asPlayer();
        final ServerWorld world = player.getServerWorld();
        if (!enable) {
            NetworkRegistry.getChannel().send(PacketDistributor.PLAYER.with(() -> player), new VisualOverlayS2CPacket(Collections.emptyList(), 0));
            source.sendFeedback(new StringTextComponent("Cleared HellasWilds visualization overlay."), false);
            return 1;
        }

        final ZoneData zone = ZoneCache.get().findZone(world, player.getPosition());
        if (zone == null) {
            throw NO_ZONE.create();
        }

        NetworkRegistry.getChannel().send(PacketDistributor.PLAYER.with(() -> player), new VisualOverlayS2CPacket(zone.getOverlay(), zone.getId().getColor()));
        source.sendFeedback(new StringTextComponent("Visualizing zone " + zone.getId().getDisplayNumber()), false);
        return 1;
    }

    /**
     * Lists all configured spawn rules for the zone the executor currently stands inside.
     */
    private static int listSpawns(final CommandSource source) throws CommandSyntaxException {
        ensureFeatures();
        final ServerPlayerEntity player = source.asPlayer();
        final ZoneData zone = ZoneCache.get().findZone(player.getServerWorld(), player.getPosition());
        if (zone == null) {
            throw NO_ZONE.create();
        }

        final List<ZoneSpawnRule> rules = ZoneSpawnController.getRules(zone);
        if (rules.isEmpty()) {
            source.sendFeedback(new StringTextComponent("Zone " + zone.getId().getDisplayNumber() + " has no custom spawn rules."), false);
            return 1;
        }

        final ITextComponent header = new StringTextComponent("Zone " + zone.getId().getDisplayNumber() + " rules (" + rules.size() + "):");
        source.sendFeedback(header.mergeStyle(TextFormatting.GOLD), false);
        for (int i = 0; i < rules.size(); i++) {
            final ZoneSpawnRule rule = rules.get(i);
            final ITextComponent line = new StringTextComponent(String.format(Locale.ROOT, " #%d %s L%d-%d w=%.2f %s", i + 1, rule.species,
                    rule.levelMin, rule.levelMax, rule.weight, rule.softDespawn ? "soft" : ""));
            source.sendFeedback(line.mergeStyle(TextFormatting.GRAY), false);
        }
        return 1;
    }

    /**
     * Launches the embedded web UI so staff can edit the active zone using a browser.
     */
    private static int openEditor(final CommandSource source) throws CommandSyntaxException {
        ensureFeatures();
        final ServerPlayerEntity player = source.asPlayer();
        final ServerWorld world = player.getServerWorld();
        final ZoneData zone = ZoneCache.get().findZone(world, player.getPosition());
        if (zone == null) {
            throw NO_ZONE.create();
        }

        stopWebServer();
        final String token = PlayitIntegration.generateToken();
        final int port = WildsConfig.WEB_PORT.get();
        try {
            activeWebServer = WebServer.start(player.getServer(), world, zone.getId().getUuid(), port, token, Duration.ofMinutes(WildsConfig.TOKEN_TIMEOUT_MINUTES.get()));
        } catch (final IOException e) {
            throw new SimpleCommandExceptionType(new StringTextComponent("Failed to start HellasWilds web UI: " + e.getMessage())).create();
        }

        final String url = PlayitIntegration.buildUrl(token);
        source.sendFeedback(new StringTextComponent("Spawn editor ready at " + url), false);
        return 1;
    }

    /**
     * Updates the spawn blending mode (additive/override) for the current zone.
     */
    private static int setMode(final CommandSource source, final String modeArg) throws CommandSyntaxException {
        ensureFeatures();
        final ServerPlayerEntity player = source.asPlayer();
        final ServerWorld world = player.getServerWorld();
        final ZoneData zone = requireZone(world, player.getPosition());
        final String mode = modeArg.toLowerCase(Locale.ROOT);
        if (!"additive".equals(mode) && !"override".equals(mode)) {
            throw new SimpleCommandExceptionType(new StringTextComponent("Unknown spawn mode '" + mode + "'."))
                    .create();
        }

        final ZoneData updated = zone.withSpawnMode(mode);
        ZoneCache.get().put(updated);
        ZoneSpawnController.updateZone(updated);
        ZoneCache.get().save(world);
        source.sendFeedback(new StringTextComponent("Zone " + zone.getId().getDisplayNumber() + " spawn mode set to " + mode), true);
        return 1;
    }

    /**
     * Persists a new spawn cap which limits the number of Pixelmon the controller may keep alive.
     */
    private static int setCap(final CommandSource source, final int cap) throws CommandSyntaxException {
        ensureFeatures();
        final ServerPlayerEntity player = source.asPlayer();
        final ServerWorld world = player.getServerWorld();
        final ZoneData zone = requireZone(world, player.getPosition());
        final ZoneData updated = zone.withSpawnCap(cap);
        ZoneCache.get().put(updated);
        ZoneSpawnController.updateZone(updated);
        ZoneCache.get().save(world);
        source.sendFeedback(new StringTextComponent("Zone " + zone.getId().getDisplayNumber() + " spawn cap set to " + cap), true);
        return 1;
    }

    private static void ensureFeatures() throws CommandSyntaxException {
        if (!HellasWilds.featuresEnabled()) {
            throw FEATURES_DISABLED.create();
        }
    }

    /**
     * Locates the nearest gate badge within a generous radius so staff do not need to stand on the
     * exact block when running commands.
     */
    private static GateContext findGate(final ServerWorld world, final BlockPos origin) throws CommandSyntaxException {
        final int radius = 20;
        GateContext best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final BlockPos pos : BlockPos.getAllInBoxMutable(origin.add(-radius, -3, -radius), origin.add(radius, 3, radius))) {
            final TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof GateBadgeTile) {
                final double distance = pos.distanceSq(origin);
                if (distance < bestDistance) {
                    final BlockState state = world.getBlockState(pos);
                    final GateBadgeTile gateTile = (GateBadgeTile) tile;
                    final int color = state.get(GateBadgeBlock.COLOR);
                    gateTile.setColor(color);
                    best = new GateContext(pos.toImmutable(), gateTile, color);
                    bestDistance = distance;
                }
            }
        }
        if (best == null) {
            throw NO_GATE.create();
        }
        GateLinker.tryLinkGate(world, best.pos, world.getBlockState(best.pos), null);
        return best;
    }

    /**
     * Helper that throws a friendly exception when commands are executed outside a managed zone.
     */
    private static ZoneData requireZone(final ServerWorld world, final BlockPos pos) throws CommandSyntaxException {
        final ZoneData zone = ZoneCache.get().findZone(world, pos);
        if (zone == null) {
            throw NO_ZONE.create();
        }
        return zone;
    }

    private static final class GateContext {
        final BlockPos pos;
        final GateBadgeTile tile;
        final int color;

        private GateContext(final BlockPos pos, final GateBadgeTile tile, final int color) {
            this.pos = pos;
            this.tile = tile;
            this.color = color;
        }

        private String description() {
            if (tile.getZoneId() != null) {
                final ZoneData zone = ZoneCache.get().get(tile.getZoneId());
                if (zone != null) {
                    return "#" + zone.getId().getDisplayNumber();
                }
            }
            return "gate";
        }
    }
}
