package com.xsasakihaise.hellaswilds.registry;

import com.xsasakihaise.hellaswilds.HellasWilds;

/**
 * Placeholder for future command registration hooks. Logic is currently implemented directly inside
 * {@link com.xsasakihaise.hellaswilds.commands.WildsCommands}.
 */
public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static void bootstrap() {
        HellasWilds.LOGGER.debug("CommandRegistry bootstrap invoked (placeholder).");
    }
}
