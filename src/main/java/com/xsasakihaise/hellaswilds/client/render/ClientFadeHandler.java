package com.xsasakihaise.hellaswilds.client.render;

import com.xsasakihaise.hellaswilds.blocks.barrier.NonPlayerBarrierFieldBlock;
import com.xsasakihaise.hellaswilds.registry.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Tracks gate fade animations on the client. The handler watches the local player every tick and
 * triggers a fade whenever they step through an unlocked gate field. Rendering code can sample the
 * current fade progress to animate emissive badge text.
 */
public final class ClientFadeHandler {
    private static final double COLLISION_EPSILON = 0.05D;
    private static float fadeProgress;
    private static boolean registered;

    private ClientFadeHandler() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(ClientFadeHandler::onClientTick);
    }

    public static void triggerFade() {
        fadeProgress = 1.0F;
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        fadeProgress = Math.max(0.0F, fadeProgress - 0.05F);
        samplePlayerCollision();
    }

    private static void samplePlayerCollision() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientPlayerEntity player = minecraft.player;
        final World world = minecraft.world;
        if (player == null || world == null) {
            return;
        }
        final AxisAlignedBB box = player.getBoundingBox().grow(COLLISION_EPSILON);
        final BlockPos min = new BlockPos(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ));
        final BlockPos max = new BlockPos(MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ));
        final BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    mutable.setPos(x, y, z);
                    final BlockState state = world.getBlockState(mutable);
                    if (state.getBlock() == BlockRegistry.NON_PLAYER_FIELD.get() && !state.get(NonPlayerBarrierFieldBlock.LOCKED)) {
                        triggerFade();
                        return;
                    }
                }
            }
        }
    }

    public static float getFadeProgress() {
        return MathHelper.clamp(fadeProgress, 0.0F, 1.0F);
    }
}
