package com.xsasakihaise.hellaswilds;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common configuration values for HellasWilds. The production design expects a more exhaustive
 * configuration surface, but only the options required by the current scaffolding are provided.
 */
public final class WildsConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue WEB_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> WEB_EXPOSURE;
    public static final ForgeConfigSpec.ConfigValue<String> WEB_PUBLIC_URL;
    public static final ForgeConfigSpec.IntValue TOKEN_TIMEOUT_MINUTES;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("webui");
        WEB_PORT = builder.comment("Local port used by the embedded spawn editor web server.")
                .defineInRange("port", 4567, 1024, 65535);
        WEB_EXPOSURE = builder.comment("Exposure strategy: 'local' keeps the URL loopback-only, 'playit' prints the configured public URL.")
                .define("exposure", "local");
        WEB_PUBLIC_URL = builder.comment("When exposure=playit this URL will be printed to operators so they can reach the Playit tunnel.")
                .define("public_url", "");
        TOKEN_TIMEOUT_MINUTES = builder.comment("Duration in minutes before web UI tokens expire.")
                .defineInRange("token_timeout_minutes", 10, 1, 120);
        builder.pop();

        SPEC = builder.build();
    }

    private WildsConfig() {
    }
}
