package com.xsasakihaise.hellaswilds.webui;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xsasakihaise.hellaswilds.HellasWilds;
import com.xsasakihaise.hellaswilds.spawns.ZoneSpawnController;
import com.xsasakihaise.hellaswilds.spawns.ZoneSpawnRule;
import com.xsasakihaise.hellaswilds.zone.ZoneCache;
import com.xsasakihaise.hellaswilds.zone.ZoneData;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Embedded HTTP server that serves the spawn editor UI and exposes a tiny JSON API for reading and
 * updating zone spawn rules. All state mutations are marshalled back to the server thread.
 */
public final class WebServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HttpServer server;
    private final MinecraftServer minecraftServer;
    private final RegistryKey<World> dimension;
    private final UUID zoneId;
    private final String token;
    private final long tokenExpiryMillis;

    private WebServer(final HttpServer server,
                      final MinecraftServer minecraftServer,
                      final RegistryKey<World> dimension,
                      final UUID zoneId,
                      final String token,
                      final Duration timeout) {
        this.server = server;
        this.minecraftServer = minecraftServer;
        this.dimension = dimension;
        this.zoneId = zoneId;
        this.token = token;
        this.tokenExpiryMillis = timeout.isZero() ? Long.MAX_VALUE : System.currentTimeMillis() + timeout.toMillis();
    }

    public static WebServer start(final MinecraftServer server,
                                  final World world,
                                  final UUID zoneId,
                                  final int port,
                                  final String token,
                                  final Duration timeout) throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        final WebServer wrapper = new WebServer(httpServer, server, world.getDimensionKey(), zoneId, token, timeout);
        httpServer.createContext("/", wrapper::handleRequest);
        httpServer.setExecutor(null);
        httpServer.start();
        return wrapper;
    }

    public void stop() {
        server.stop(0);
    }

    private void handleRequest(final HttpExchange exchange) throws IOException {
        try {
            if (!Objects.equals(token, queryParam(exchange, "auth")) || System.currentTimeMillis() > tokenExpiryMillis) {
                respondText(exchange, 403, "Access denied");
                return;
            }

            final String path = exchange.getRequestURI().getPath();
            if ("/api/spawns".equals(path)) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respondJson(exchange, ImmutableMap.of(
                            "zone", callServer(this::describeZone),
                            "rules", callServer(this::readRules)));
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    final byte[] body = exchange.getRequestBody().readAllBytes();
                    final SpawnPayload payload = GSON.fromJson(new String(body, StandardCharsets.UTF_8), SpawnPayload.class);
                    if (payload == null || payload.rules == null) {
                        respondText(exchange, 400, "Invalid payload");
                        return;
                    }
                    callServer(world -> {
                        final ZoneData zone = ZoneCache.get().get(zoneId);
                        if (zone == null) {
                            throw new IllegalStateException("Zone not found");
                        }
                        ZoneSpawnController.setRules(zone, payload.rules);
                        ZoneCache.get().save(world);
                        return null;
                    });
                    respondJson(exchange, ImmutableMap.of(
                            "zone", callServer(this::describeZone),
                            "rules", callServer(this::readRules)));
                } else {
                    respondText(exchange, 405, "Method Not Allowed");
                }
                return;
            }

            final String resource = resolveResource(path);
            final byte[] body = loadResource(resource);
            final Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", contentType(resource));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (final Exception e) {
            HellasWilds.LOGGER.error("Web UI request failed", e);
            respondText(exchange, 500, "Internal server error");
        } finally {
            exchange.close();
        }
    }

    private Map<String, Object> describeZone(final ServerWorld world) {
        final ZoneData zone = ZoneCache.get().get(zoneId);
        if (zone == null) {
            throw new IllegalStateException("Zone not found");
        }
        return ImmutableMap.<String, Object>builder()
                .put("uuid", zone.getId().getUuid().toString())
                .put("displayNumber", zone.getId().getDisplayNumber())
                .put("spawnMode", zone.getSpawnMode())
                .put("spawnCap", zone.getSpawnCap())
                .build();
    }

    private List<ZoneSpawnRule> readRules(final ServerWorld world) {
        final ZoneData zone = ZoneCache.get().get(zoneId);
        if (zone == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ZoneSpawnController.getRules(zone));
    }

    private <T> T callServer(final ServerFunction<T> function) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        minecraftServer.execute(() -> {
            final ServerWorld world = minecraftServer.getWorld(dimension);
            if (world == null) {
                future.completeExceptionally(new IllegalStateException("World " + dimension.getLocation() + " not loaded"));
                return;
            }
            try {
                future.complete(function.apply(world));
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.join();
    }

    private static String queryParam(final HttpExchange exchange, final String key) {
        final String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        final String[] pairs = query.split("&");
        for (final String pair : pairs) {
            final int idx = pair.indexOf('=');
            if (idx > 0) {
                final String name = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    private static void respondText(final HttpExchange exchange, final int status, final String text) throws IOException {
        final byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void respondJson(final HttpExchange exchange, final Object payload) throws IOException {
        final byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String resolveResource(@Nullable final String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/hellaswilds-web/index.html";
        }
        return "/hellaswilds-web" + path;
    }

    private static byte[] loadResource(final String resource) throws IOException {
        try (InputStream stream = WebServer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return ("Missing resource: " + resource).getBytes(StandardCharsets.UTF_8);
            }
            final byte[] buffer = new byte[8192];
            int read;
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String contentType(final String resource) {
        if (resource.endsWith(".html")) {
            return "text/html; charset=utf-8";
        } else if (resource.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        } else if (resource.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        return "text/plain; charset=utf-8";
    }

    private interface ServerFunction<T> {
        T apply(ServerWorld world);
    }

    private static final class SpawnPayload {
        List<ZoneSpawnRule> rules = new ArrayList<>();
    }
}
