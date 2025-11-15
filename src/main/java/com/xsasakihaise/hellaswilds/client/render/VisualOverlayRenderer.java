package com.xsasakihaise.hellaswilds.client.render;

import com.xsasakihaise.hellaswilds.zone.VisualOverlayS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * Renders cached zone overlays using translucent ground quads. The server sends geometry snapshots so
 * no expensive computation is required client side.
 */
public final class VisualOverlayRenderer {
    private static final List<AxisAlignedBB> BOXES = new CopyOnWriteArrayList<>();
    private static int colourIndex;

    private VisualOverlayRenderer() {
    }

    /**
     * Hooks client events that drive overlay rendering and automatic cleanup when the player leaves
     * a server.
     */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(VisualOverlayRenderer::onRenderWorldLast);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedOutEvent event) -> clear());
    }

    /**
     * Accepts overlay data from the server and caches it for rendering in subsequent frames.
     */
    public static void setOverlay(final VisualOverlayS2CPacket packet) {
        BOXES.clear();
        if (packet != null) {
            BOXES.addAll(packet.getBoxes());
            colourIndex = packet.getColor();
        } else {
            colourIndex = 0;
        }
    }

    /**
     * Clears the cached geometry so the renderer stops drawing overlays immediately.
     */
    public static void clear() {
        BOXES.clear();
    }

    private static void onRenderWorldLast(final RenderWorldLastEvent event) {
        if (BOXES.isEmpty()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final ActiveRenderInfo info = minecraft.gameRenderer.getActiveRenderInfo();
        final Vector3d camera = info.getProjectedView();

        final MatrixStack stack = event.getMatrixStack();
        stack.push();
        stack.translate(-camera.x, -camera.y, -camera.z);
        final Matrix4f matrix = stack.getLast().getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        final Tessellator tessellator = Tessellator.getInstance();
        final com.mojang.blaze3d.vertex.BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        final int rgb = colourFromIndex(colourIndex);
        final float r = ((rgb >> 16) & 0xFF) / 255.0F;
        final float g = ((rgb >> 8) & 0xFF) / 255.0F;
        final float b = (rgb & 0xFF) / 255.0F;
        final float alpha = 0.4F;

        for (final AxisAlignedBB box : BOXES) {
            final float minX = (float) box.minX;
            final float maxX = (float) box.maxX;
            final float minZ = (float) box.minZ;
            final float maxZ = (float) box.maxZ;
            final float y = (float) (box.minY + 0.1);

            buffer.pos(matrix, minX, y, minZ).color(r, g, b, alpha).endVertex();
            buffer.pos(matrix, minX, y, maxZ).color(r, g, b, alpha).endVertex();
            buffer.pos(matrix, maxX, y, maxZ).color(r, g, b, alpha).endVertex();
            buffer.pos(matrix, maxX, y, minZ).color(r, g, b, alpha).endVertex();
        }

        tessellator.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        stack.pop();
    }

    private static int colourFromIndex(final int index) {
        final net.minecraft.item.DyeColor dye = net.minecraft.item.DyeColor.byId(index);
        return dye == null ? 0x33FFAA : dye.getColorValue();
    }
}
