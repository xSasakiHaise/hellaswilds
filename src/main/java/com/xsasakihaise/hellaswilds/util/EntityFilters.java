package com.xsasakihaise.hellaswilds.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Utility predicates for filtering entities when applying barrier logic or spawn management.
 */
public final class EntityFilters {
    private EntityFilters() {
    }

    public static boolean isPlayer(final Entity entity) {
        return entity instanceof PlayerEntity;
    }

    public static boolean isNonPlayerOrProjectile(final Entity entity) {
        // TODO: expand once projectile checks are in place.
        return !isPlayer(entity);
    }
}
