package com.xsasakihaise.hellaswilds.registry;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Block item that encodes a colour index (0-15) inside the stack NBT. The associated block reads the
 * tag during placement and applies the matching block state property.
 */
public class ColorVariantBlockItem extends BlockItem {
    public static final String COLOR_TAG = "Color";

    public ColorVariantBlockItem(final Block block, final Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void fillItemGroup(final ItemGroup group, final NonNullList<ItemStack> items) {
        if (!this.isInGroup(group)) {
            return;
        }

        for (int color = 0; color <= 15; color++) {
            items.add(withColor(new ItemStack(this), color));
        }
    }

    @Override
    public void addInformation(final ItemStack stack, @Nullable final World world, final List<ITextComponent> tooltip, final ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(new StringTextComponent("Color: " + getColor(stack)));
    }

    public static ItemStack withColor(final ItemStack stack, final int color) {
        final CompoundNBT tag = stack.getOrCreateTag();
        tag.putInt(COLOR_TAG, color);
        return stack;
    }

    public static int getColor(final ItemStack stack) {
        final CompoundNBT tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        return tag.getInt(COLOR_TAG);
    }
}
