package com.xsasakihaise.hellaswilds.zone;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet instructing the client to render translucent overlays for a zone. The packet keeps the data
 * minimal by serialising a list of bounding boxes.
 */
public class VisualOverlayS2CPacket {
    private final List<AxisAlignedBB> boxes;
    private final int color;

    public VisualOverlayS2CPacket(final List<AxisAlignedBB> boxes, final int color) {
        this.boxes = boxes;
        this.color = color;
    }

    public VisualOverlayS2CPacket(final PacketBuffer buffer) {
        final int size = buffer.readVarInt();
        this.boxes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final double minX = buffer.readDouble();
            final double minY = buffer.readDouble();
            final double minZ = buffer.readDouble();
            final double maxX = buffer.readDouble();
            final double maxY = buffer.readDouble();
            final double maxZ = buffer.readDouble();
            boxes.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }
        this.color = buffer.readVarInt();
    }

    public void encode(final PacketBuffer buffer) {
        buffer.writeVarInt(boxes.size());
        for (final AxisAlignedBB box : boxes) {
            buffer.writeDouble(box.minX);
            buffer.writeDouble(box.minY);
            buffer.writeDouble(box.minZ);
            buffer.writeDouble(box.maxX);
            buffer.writeDouble(box.maxY);
            buffer.writeDouble(box.maxZ);
        }
        buffer.writeVarInt(color);
    }

    public List<AxisAlignedBB> getBoxes() {
        return boxes;
    }

    public int getColor() {
        return color;
    }
}
