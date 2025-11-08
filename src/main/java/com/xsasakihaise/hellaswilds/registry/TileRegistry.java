package com.xsasakihaise.hellaswilds.registry;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeTile;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Tile entity registrations for HellasWilds.
 */
public final class TileRegistry {
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, HellasWilds.MOD_ID);

    public static final RegistryObject<TileEntityType<GateBadgeTile>> GATE_BADGE_TILE = TILES.register("gate_badge",
            () -> TileEntityType.Builder.create(GateBadgeTile::new, BlockRegistry.GATE_BADGE.get()).build(null));

    private TileRegistry() {
    }
}
