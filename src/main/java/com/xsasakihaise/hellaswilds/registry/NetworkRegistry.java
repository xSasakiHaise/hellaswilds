package com.xsasakihaise.hellaswilds.registry;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.client.render.VisualOverlayRenderer;
import com.xsasakihaise.hellaswilds.zone.VisualOverlayS2CPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Handles simple network packet registration.
 */
public final class NetworkRegistry {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;

    private NetworkRegistry() {
    }

    public static void bootstrap() {
        if (channel != null) {
            return;
        }

        channel = net.minecraftforge.fml.network.NetworkRegistry.newSimpleChannel(
                new ResourceLocation(HellasWilds.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals);

        channel.registerMessage(0, VisualOverlayS2CPacket.class,
                VisualOverlayS2CPacket::encode,
                VisualOverlayS2CPacket::new,
                NetworkRegistry::handleVisualOverlay);
    }

    public static void prepareClient() {
        // Placeholder – no client-only initialisation required yet.
    }

    private static void handleVisualOverlay(final VisualOverlayS2CPacket packet, final Supplier<NetworkEvent.Context> ctxSupplier) {
        final NetworkEvent.Context context = ctxSupplier.get();
        context.enqueueWork(() -> VisualOverlayRenderer.setOverlay(packet));
        context.setPacketHandled(true);
    }

    public static SimpleChannel getChannel() {
        return channel;
    }
}
