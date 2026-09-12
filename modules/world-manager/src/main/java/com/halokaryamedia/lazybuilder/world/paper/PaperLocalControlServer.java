package com.halokaryamedia.lazybuilder.world.paper;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.halokaryamedia.lazybuilder.world.application.GameRuleSetting;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsSnapshot;
import com.halokaryamedia.lazybuilder.world.application.WorldSpawnControl;
import com.halokaryamedia.lazybuilder.world.application.WorldWeather;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRegistry;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRunner;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskSnapshot;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Loopback-only structured bridge for the LazyBuilder desktop application.
 *
 * <p>The HTTP layer is deliberately thin. All world mutations are delegated to the existing
 * World-Manager application services and marshalled onto the Paper main thread when they reach
 * Bukkit/Paper state.</p>
 */
public final class PaperLocalControlServer {
    public static final String TOKEN_ENV = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
    public static final String PORT_ENV = "LAZYBUILDER_WORLD_CONTROL_PORT";
    public static final int DEFAULT_PORT = 17842;
    private static final Gson GSON = new Gson();
    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 30L;

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final WorldRuntimeService runtime;
    private final WorldCreationService creation;
    private final WorldSettingsService settings;
    private final WorldLifecycleService lifecycle;
    private final WorldTaskRegistry tasks;
    private final WorldTaskRunner taskRunner;

    private HttpServer server;
    private ExecutorService executor;

    public PaperLocalControlServer(
            JavaPlugin plugin,
            WorldRegistry registry,
            WorldRuntimeService runtime,
            WorldCreationService creation,
            WorldSettingsService settings,
            WorldLifecycleService lifecycle,
            WorldTaskRegistry tasks,
            WorldTaskRunner taskRunner
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.creation = Objects.requireNonNull(creation, "creation");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
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
            created.createContext("/v1/tasks", exchange -> handleTasks(exchange, token));
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
            sendError(exchange, 405, "method_not_allowed", "Only GET is supported.");
            return;
        }
        sendJson(exchange, 200, new StatusResponse("ready", 1));
    }

    private void handleWorlds(HttpExchange exchange, String token) throws IOException {
        if (!authorize(exchange, token)) return;

        String relative = exchange.getRequestURI().getPath().substring("/v1/worlds".length());
        try {
            if (relative.isEmpty() || "/".equals(relative)) {
                handleWorldCollection(exchange);
                return;
            }

            String[] parts = relative.substring(1).split("/");
            if (parts.length != 2) {
                sendError(exchange, 404, "not_found", "Unknown World-Manager route.");
                return;
            }

            WorldId worldId;
            try {
                worldId = WorldId.parse(parts[0]);
            } catch (RuntimeException exception) {
                sendError(exchange, 400, "invalid_world_id", "World id is invalid.");
                return;
            }

            switch (parts[1]) {
                case "load" -> handleLoad(exchange, worldId);
                case "unload" -> handleUnload(exchange, worldId);
                case "settings" -> handleSettings(exchange, worldId);
                default -> sendError(exchange, 404, "not_found", "Unknown World-Manager route.");
            }
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_request", exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, "world_state_conflict", exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().warning("Local World control request failed: " + exception.getMessage());
            sendError(exchange, 500, "world_operation_failed", "World-Manager could not complete the request.");
        }
    }

    private void handleTasks(HttpExchange exchange, String token) throws IOException {
        if (!authorize(exchange, token)) return;

        String relative = exchange.getRequestURI().getPath().substring("/v1/tasks".length());
        try {
            if ("GET".equals(exchange.getRequestMethod())) {
                handleTaskRead(exchange, relative);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                handleTaskStart(exchange, relative);
                return;
            }
            sendError(exchange, 405, "method_not_allowed", "Only GET and POST are supported.");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_request", exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, "task_state_conflict", exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().warning("Local World task request failed: " + exception.getMessage());
            sendError(exchange, 500, "task_operation_failed", "World-Manager could not start the task.");
        }
    }

    private void handleTaskRead(HttpExchange exchange, String relative) throws IOException {
        if (relative.isEmpty() || "/".equals(relative)) {
            sendJson(exchange, 200, new TaskListResponse(
                    tasks.recent().stream().map(PaperLocalControlServer::taskResponse).toList()
            ));
            return;
        }
        if (!relative.startsWith("/") || relative.indexOf('/', 1) >= 0) {
            sendError(exchange, 404, "not_found", "Unknown task route.");
            return;
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(relative.substring(1));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_task_id", "Task id is invalid.");
            return;
        }

        WorldTaskSnapshot snapshot = tasks.find(taskId).orElse(null);
        if (snapshot == null) {
            sendError(exchange, 404, "task_not_found", "World task was not found.");
            return;
        }
        sendJson(exchange, 200, taskResponse(snapshot));
    }

    private void handleTaskStart(HttpExchange exchange, String relative) throws Exception {
        WorldTaskType type = switch (relative) {
            case "/archive" -> WorldTaskType.ARCHIVE;
            case "/restore" -> WorldTaskType.RESTORE;
            default -> null;
        };
        if (type == null) {
            sendError(exchange, 404, "not_found", "Unknown task operation.");
            return;
        }

        TaskStartRequest request = readJson(exchange, TaskStartRequest.class);
        if (request.worldId() == null || request.worldId().isBlank()) {
            throw new IllegalArgumentException("worldId must not be blank");
        }

        WorldId worldId;
        try {
            worldId = WorldId.parse(request.worldId().trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("World id is invalid.", exception);
        }
        if (registry.find(worldId).isEmpty()) {
            throw new IllegalArgumentException("World is not managed: " + worldId);
        }

        WorldTaskSnapshot queued = taskRunner.submit(
                type,
                worldId,
                type == WorldTaskType.ARCHIVE ? "Archive queued." : "Restore queued.",
                progress -> {
                    progress.update(20, "Dispatching lifecycle change to Paper.");
                    WorldRecord updated = sync(() -> type == WorldTaskType.ARCHIVE
                            ? lifecycle.archive(worldId)
                            : lifecycle.restore(worldId));
                    progress.update(90, type == WorldTaskType.ARCHIVE
                            ? "World archived; finalizing task."
                            : "World restored; finalizing task.");
                    return updated.lifecycle().name();
                }
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private void handleWorldCollection(HttpExchange exchange) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, new WorldListResponse(sync(() -> registry.all().stream()
                    .map(this::summary)
                    .toList())));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            CreateWorldRequest request = readJson(exchange, CreateWorldRequest.class);
            if (request.folderName() == null || request.folderName().isBlank()) {
                throw new IllegalArgumentException("folderName must not be blank");
            }
            String displayName = request.displayName() == null || request.displayName().isBlank()
                    ? request.folderName()
                    : request.displayName().trim();
            WorldKind kind = parseCreateKind(request.kind());
            WorldRecord created = sync(() -> creation.create(request.folderName().trim(), displayName, kind));
            sendJson(exchange, 201, summary(created));
            return;
        }
        sendError(exchange, 405, "method_not_allowed", "Only GET and POST are supported.");
    }

    private void handleLoad(HttpExchange exchange, WorldId worldId) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method_not_allowed", "Only POST is supported.");
            return;
        }
        WorldRecord world = sync(() -> runtime.load(worldId));
        sendJson(exchange, 200, summary(world));
    }

    private void handleUnload(HttpExchange exchange, WorldId worldId) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method_not_allowed", "Only POST is supported.");
            return;
        }
        WorldRecord world = sync(() -> runtime.unload(worldId));
        sendJson(exchange, 200, summary(world));
    }

    private void handleSettings(HttpExchange exchange, WorldId worldId) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            WorldSettingsSnapshot snapshot = sync(() -> settings.snapshot(worldId));
            sendJson(exchange, 200, settingsResponse(snapshot));
            return;
        }
        if ("PATCH".equals(exchange.getRequestMethod())) {
            UpdateWorldSettingsRequest request = readJson(exchange, UpdateWorldSettingsRequest.class);
            WorldSettingsSnapshot snapshot = sync(() -> applySettings(worldId, request));
            sendJson(exchange, 200, settingsResponse(snapshot));
            return;
        }
        sendError(exchange, 405, "method_not_allowed", "Only GET and PATCH are supported.");
    }

    private WorldSettingsSnapshot applySettings(WorldId worldId, UpdateWorldSettingsRequest request) {
        if (request.autoLoad() != null) settings.setAutoLoad(worldId, request.autoLoad());
        if (request.defaultGameMode() != null) {
            settings.setDefaultGameMode(worldId, WorldGameMode.valueOf(request.defaultGameMode().trim().toUpperCase()));
        }
        if (request.timeOfDayTicks() != null) settings.setTime(worldId, request.timeOfDayTicks());
        if (request.weather() != null) {
            settings.setWeather(worldId, WorldWeather.valueOf(request.weather().trim().toUpperCase()));
        }
        if (request.naturalSpawning() != null) {
            settings.setSpawning(worldId, WorldSpawnControl.NATURAL, request.naturalSpawning());
        }
        if (request.daylightCycle() != null) {
            settings.setGameRule(worldId, "doDaylightCycle", request.daylightCycle().toString());
        }
        if (request.weatherCycle() != null) {
            settings.setGameRule(worldId, "doWeatherCycle", request.weatherCycle().toString());
        }
        return settings.snapshot(worldId);
    }

    private ManagedWorldResponse summary(WorldRecord world) {
        return new ManagedWorldResponse(
                world.id().toString(),
                world.displayName(),
                world.kind().name(),
                world.lifecycle().name(),
                runtime.state(world.id()).name(),
                world.autoLoad(),
                world.defaultGameMode()
        );
    }

    private static TaskResponse taskResponse(WorldTaskSnapshot snapshot) {
        return new TaskResponse(
                snapshot.taskId().toString(),
                snapshot.type().name(),
                snapshot.worldId() == null ? null : snapshot.worldId().toString(),
                snapshot.state().name(),
                snapshot.progressPercent(),
                snapshot.message(),
                snapshot.result(),
                snapshot.error(),
                snapshot.createdAt().toString(),
                snapshot.updatedAt().toString()
        );
    }

    private static WorldKind parseCreateKind(String value) {
        if (value == null || value.isBlank()) return WorldKind.FLAT;
        WorldKind kind = WorldKind.valueOf(value.trim().toUpperCase());
        if (kind == WorldKind.IMPORTED) {
            throw new IllegalArgumentException("Create World supports only FLAT or VOID.");
        }
        return kind;
    }

    private static WorldSettingsResponse settingsResponse(WorldSettingsSnapshot snapshot) {
        return new WorldSettingsResponse(
                snapshot.world().id().toString(),
                snapshot.world().displayName(),
                snapshot.world().autoLoad(),
                snapshot.defaultGameMode().name(),
                snapshot.runtime().timeOfDayTicks(),
                snapshot.runtime().weather().name(),
                snapshot.runtime().spawning().naturalSpawning(),
                gameRuleBoolean(snapshot, "doDaylightCycle"),
                gameRuleBoolean(snapshot, "doWeatherCycle")
        );
    }

    private static boolean gameRuleBoolean(WorldSettingsSnapshot snapshot, String name) {
        return snapshot.runtime().gamerules().stream()
                .filter(rule -> rule.name().equalsIgnoreCase(name))
                .findFirst()
                .map(GameRuleSetting::value)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private <T> T sync(Callable<T> action) throws Exception {
        if (plugin.getServer().isPrimaryThread()) return action.call();
        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, action);
        return future.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        try (var reader = new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            T payload = GSON.fromJson(reader, type);
            if (payload == null) throw new IllegalArgumentException("Request body is required.");
            return payload;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Request body contains invalid JSON.", exception);
        }
    }

    private static boolean authorize(HttpExchange exchange, String expectedToken) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!constantTimeEquals("Bearer " + expectedToken, authorization)) {
            sendError(exchange, 401, "unauthorized", "Authentication failed.");
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

    private static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, GSON.toJson(payload));
    }

    private static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        sendJson(exchange, status, new ErrorResponse(code, message == null ? "" : message));
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

    private record StatusResponse(String status, int protocolVersion) {}
    private record ErrorResponse(String error, String message) {}
    private record WorldListResponse(List<ManagedWorldResponse> worlds) {}
    private record TaskListResponse(List<TaskResponse> tasks) {}
    private record ManagedWorldResponse(
            String id,
            String displayName,
            String kind,
            String lifecycle,
            String runtimeState,
            boolean autoLoad,
            String defaultGameMode
    ) {}
    private record TaskResponse(
            String taskId,
            String type,
            String worldId,
            String state,
            int progressPercent,
            String message,
            String result,
            String error,
            String createdAt,
            String updatedAt
    ) {}
    private record TaskStartRequest(String worldId) {}
    private record CreateWorldRequest(String folderName, String displayName, String kind) {}
    private record UpdateWorldSettingsRequest(
            Boolean autoLoad,
            String defaultGameMode,
            Long timeOfDayTicks,
            String weather,
            Boolean naturalSpawning,
            Boolean daylightCycle,
            Boolean weatherCycle
    ) {}
    private record WorldSettingsResponse(
            String id,
            String displayName,
            boolean autoLoad,
            String defaultGameMode,
            long timeOfDayTicks,
            String weather,
            boolean naturalSpawning,
            boolean daylightCycle,
            boolean weatherCycle
    ) {}
}
