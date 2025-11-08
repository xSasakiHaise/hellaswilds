package com.xsasakihaise.hellaswilds.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xsasakihaise.hellaswilds.blocks.barrier.GateBadgeTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;

import java.util.List;

/**
 * Simple tile entity renderer that projects the gate number onto both faces of the badge. The
 * rendering intentionally stays minimal while placeholder assets are used.
 */
public class GateBadgeRenderer extends TileEntityRenderer<GateBadgeTile> {
    public GateBadgeRenderer(final TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(final GateBadgeTile tile, final float partialTicks, final MatrixStack matrix,
                       final IRenderTypeBuffer buffer, final int combinedLight, final int combinedOverlay) {
        final int number = tile.getDisplayNumber();
        if (number < 0) {
            return;
        }
        final String text = Integer.toString(number);
        final float scale = 0.02F * Math.max(1, 3 - text.length() + 1);
        final float offset = 0.51F;
        final float y = 1.5F;

        final float rotation = determineRotation(tile);
        renderText(matrix, buffer, text, scale, offset, y, rotation);
        renderText(matrix, buffer, text, scale, offset, y, rotation + 180.0F);
    }

    private void renderText(final MatrixStack stack, final IRenderTypeBuffer buffer, final String text,
                            final float scale, final float offset, final float y, final float rotation) {
        stack.push();
        stack.translate(0.5D, y, 0.5D);
        stack.rotate(Vector3f.YP.rotationDegrees(rotation));
        stack.translate(0.0D, 0.0D, -offset);
        final float fade = ClientFadeHandler.getFadeProgress();
        final float animatedScale = scale * (1.0F + fade * 0.15F);
        stack.scale(-animatedScale, -animatedScale, animatedScale);
        final int packedLight = 0x00F000F0;
        final int alpha = (int) (MathHelper.clamp(0.35F + fade * 0.65F, 0.0F, 1.0F) * 255.0F) << 24;
        final int color = alpha | 0x00FFFFFF;
        Minecraft.getInstance().fontRenderer.renderString(text, -Minecraft.getInstance().fontRenderer.getStringWidth(text) / 2.0F,
                -4.0F, color, false, stack.getLast().getMatrix(), buffer, false, 0, packedLight);
        stack.pop();
    }

    private float determineRotation(final GateBadgeTile tile) {
        final List<BlockPos> pillars = tile.getLinkedPillars();
        if (pillars.size() >= 2) {
            final BlockPos a = pillars.get(0);
            final BlockPos b = pillars.get(1);
            if (a.getX() == b.getX()) {
                return 180.0F;
            }
            return 90.0F;
        }
        return 0.0F;
    }
}
