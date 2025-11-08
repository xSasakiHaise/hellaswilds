package com.xsasakihaise.hellaswilds.registry;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.blocks.barrier.BarrierSegmentBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.PillarBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Declares all HellasWilds blocks. Each block uses the colour-aware block state defined in the
 * barrier family classes, enabling the creative tab to show sixteen variants through
 * {@link ColorVariantBlockItem}.
 */
public final class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HellasWilds.MOD_ID);

    public static final RegistryObject<BarrierSegmentBlock> BARRIER_SEGMENT = BLOCKS.register("barrier_segment",
            () -> new BarrierSegmentBlock(AbstractBlock.Properties.create(Material.GLASS)
                    .hardnessAndResistance(0.3F)
                    .notSolid()
                    .sound(SoundType.GLASS)
                    .setLightLevel(state -> 15)));

    public static final RegistryObject<PillarBlock> PILLAR = BLOCKS.register("pillar",
            () -> new PillarBlock(AbstractBlock.Properties.create(Material.ROCK)
                    .hardnessAndResistance(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .setLightLevel(state -> 15)));

    public static final RegistryObject<GateBadgeBlock> GATE_BADGE = BLOCKS.register("gate_badge",
            () -> new GateBadgeBlock(AbstractBlock.Properties.create(Material.IRON)
                    .hardnessAndResistance(4.0F)
                    .notSolid()
                    .sound(SoundType.METAL)
                    .setLightLevel(state -> 15)));

    public static final RegistryObject<NonPlayerBarrierFieldBlock> NON_PLAYER_FIELD = BLOCKS.register("non_player_field",
            NonPlayerBarrierFieldBlock::new);

    private BlockRegistry() {
    }
}
