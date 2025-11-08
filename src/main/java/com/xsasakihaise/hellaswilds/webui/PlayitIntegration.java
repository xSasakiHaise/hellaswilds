package com.xsasakihaise.hellaswilds.webui;

import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.WildsConfig;

import java.util.UUID;

/**
 * Convenience helper that decides which URL to print when the spawn editor is opened. The actual
 * Playit tunnelling is outside the scope of this scaffolding – this class focuses on respecting the
 * configured exposure mode and token lifetime.
 */
public final class PlayitIntegration {
    private PlayitIntegration() {
    }

    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String buildUrl(final String token) {
        final String exposure = WildsConfig.WEB_EXPOSURE.get();
        if ("playit".equalsIgnoreCase(exposure)) {
            final String url = WildsConfig.WEB_PUBLIC_URL.get();
            if (url != null && !url.isEmpty()) {
                return url + "?auth=" + token;
            }
            HellasWilds.LOGGER.warn("Exposure is set to playit but no public_url was configured; falling back to localhost.");
        }
        return "http://127.0.0.1:" + WildsConfig.WEB_PORT.get() + "/?auth=" + token;
    }
}
