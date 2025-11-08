package com.xsasakihaise.hellaswilds.registry;

import com.xsasakihaise.hellaswilds.HellasWilds;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers the colour-aware block items for the barrier family.
 */
public final class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HellasWilds.MOD_ID);

    public static final RegistryObject<Item> BARRIER_SEGMENT_ITEM = ITEMS.register("barrier_segment",
            () -> new ColorVariantBlockItem(BlockRegistry.BARRIER_SEGMENT.get(), new Item.Properties().group(ItemGroup.DECORATIONS)));

    public static final RegistryObject<Item> PILLAR_ITEM = ITEMS.register("pillar",
            () -> new ColorVariantBlockItem(BlockRegistry.PILLAR.get(), new Item.Properties().group(ItemGroup.DECORATIONS)));

    public static final RegistryObject<Item> GATE_BADGE_ITEM = ITEMS.register("gate_badge",
            () -> new ColorVariantBlockItem(BlockRegistry.GATE_BADGE.get(), new Item.Properties().group(ItemGroup.DECORATIONS)));

    private ItemRegistry() {
    }
}
