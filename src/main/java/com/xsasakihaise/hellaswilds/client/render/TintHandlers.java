package com.xsasakihaise.hellaswilds.client.render;

import com.xsasakihaise.hellaswilds.blocks.barrier.BarrierSegmentBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeBlock;
import com.xsasakihaise.hellaswilds.blocks.barrier.PillarBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import com.xsasakihaise.hellaswilds.registry.ColorVariantBlockItem;
import com.xsasakihaise.hellaswilds.registry.ItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.item.DyeColor;

/**
 * Registers tint handlers so the colour blockstate and items render using the expected dye palette.
 */
public final class TintHandlers {
    private TintHandlers() {
    }

    public static void register() {
        final Minecraft minecraft = Minecraft.getInstance();
        final BlockColors blockColors = minecraft.getBlockColors();
        blockColors.register((state, world, pos, tintIndex) -> {
            if (state == null) {
                return 0xFFFFFF;
            }
            if (state.getBlock() instanceof BarrierSegmentBlock) {
                return colour(state.get(BarrierSegmentBlock.COLOR));
            }
            if (state.getBlock() instanceof PillarBlock) {
                return colour(state.get(PillarBlock.COLOR));
            }
            if (state.getBlock() instanceof GateBadgeBlock) {
                return colour(state.get(GateBadgeBlock.COLOR));
            }
            return 0xFFFFFF;
        }, BlockRegistry.BARRIER_SEGMENT.get(), BlockRegistry.PILLAR.get(), BlockRegistry.GATE_BADGE.get());

        final ItemColors itemColors = minecraft.getItemColors();
        itemColors.register((stack, tintIndex) -> tintIndex == 0 ? colour(ColorVariantBlockItem.getColor(stack)) : 0xFFFFFF,
                ItemRegistry.BARRIER_SEGMENT_ITEM.get(),
                ItemRegistry.PILLAR_ITEM.get(),
                ItemRegistry.GATE_BADGE_ITEM.get());
    }

    private static int colour(final int id) {
        final DyeColor dye = DyeColor.byId(id);
        return dye == null ? 0xFFFFFF : dye.getColorValue();
    }
}
