package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loopback-only structured bridge for the LazyBuilder desktop application.
 *
 * <p>This adapter deliberately exposes only World-Manager state. The desktop app remains a client;
 * world lifecycle/business logic stays in World-Manager services.</p>
 */
public final class PaperLocalControlServer {
    public static final String TOKEN_ENV = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
    public static final String PORT_ENV = "LAZYBUILDER_WORLD_CONTROL_PORT";
    public static final int DEFAULT_PORT = 17842;

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final WorldRuntimeService runtime;

    private HttpServer server;
    private ExecutorService executor;

    public PaperLocalControlServer(JavaPlugin plugin, WorldRegistry registry, WorldRuntimeService runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public void start() {
        if (server != null) return;

        String token = System.getenv(TOKEN_ENV);
        if (token == null || token.isBlank()) {
            plugin.getLogger().fine("Desktop World control bridge disabled: no local control token was provided.");
            return;
        }

        int port = resolvePort(System.getenv(PORT_ENV));
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            ExecutorService createdExecutor = Executors.newVirtualThreadPerTaskExecutor();
            created.setExecutor(createdExecutor);
            created.createContext("/v1/status", exchange -> handleStatus(exchange, token));
            created.createContext("/v1/worlds", exchange -> handleWorlds(exchange, token));
            created.start();
            server = created;
            executor = createdExecutor;
            plugin.getLogger().info("World-Manager local desktop bridge ready on 127.0.0.1:" + port + ".");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start World-Manager local desktop bridge", exception);
        }
    }

    public void stop() {
        HttpServer current = server;
        server = null;
        if (current != null) current.stop(0);

        ExecutorService currentExecutor = executor;
        executor = null;
        if (currentExecutor != null) currentExecutor.close();
    }

    private void handleStatus(HttpExchange exchange, String token) throws IOException {
        if (!authorize(exchange, token)) return;
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        send(exchange, 200, "{\"status\":\"ready\",\"protocolVersion\":1}");
    }

    private void handleWorlds(HttpExchange exchange, String token) throws IOException {
        if (!authorize(exchange, token)) return;
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        List<WorldRecord> worlds = registry.all();
        StringBuilder json = new StringBuilder(128 + worlds.size() * 160);
        json.append("{\"worlds\":[");
        for (int index = 0; index < worlds.size(); index++) {
            if (index > 0) json.append(',');
            WorldRecord world = worlds.get(index);
            json.append('{')
                    .append("\"id\":\"").append(escape(world.id().toString())).append("\",")
                    .append("\"folderName\":\"").append(escape(world.folderName())).append("\",")
                    .append("\"displayName\":\"").append(escape(world.displayName())).append("\",")
                    .append("\"kind\":\"").append(world.kind().name()).append("\",")
                    .append("\"lifecycle\":\"").append(world.lifecycle().name()).append("\",")
                    .append("\"runtimeState\":\"").append(runtime.state(world.id()).name()).append("\",")
                    .append("\"autoLoad\":").append(world.autoLoad()).append(',')
                    .append("\"defaultGameMode\":\"").append(escape(world.defaultGameMode())).append("\"")
                    .append('}');
        }
        json.append("]}");
        send(exchange, 200, json.toString());
    }

    private static boolean authorize(HttpExchange exchange, String expectedToken) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!constantTimeEquals("Bearer " + expectedToken, authorization)) {
            send(exchange, 401, "{\"error\":\"unauthorized\"}");
            return false;
        }
        return true;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        int difference = left.length ^ right.length;
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            byte a = index < left.length ? left[index] : 0;
            byte b = index < right.length ? right[index] : 0;
            difference |= a ^ b;
        }
        return difference == 0;
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        } finally {
            exchange.close();
        }
    }

    private static int resolvePort(String value) {
        if (value == null || value.isBlank()) return DEFAULT_PORT;
        try {
            int port = Integer.parseInt(value);
            if (port < 1024 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + PORT_ENV + " value: " + value);
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format("\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.toString();
    }
}
