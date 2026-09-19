package com.halokaryamedia.lazybuilder.world.paper;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.halokaryamedia.lazybuilder.world.application.GameRuleSetting;
import com.halokaryamedia.lazybuilder.world.application.WorldBackupService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
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
import com.halokaryamedia.lazybuilder.world.task.WorldTaskWork;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loopback-only structured bridge for the LazyBuilder desktop application. */
public final class PaperLocalControlServer {
    public static final String TOKEN_ENV = "LAZYBUILDER_WORLD_CONTROL_TOKEN";
    public static final String PORT_ENV = "LAZYBUILDER_WORLD_CONTROL_PORT";
    public static final int DEFAULT_PORT = 17842;
    public static final int PROTOCOL_VERSION = 2;

    private static final Gson GSON = new Gson();
    private static final int MAX_JSON_BODY_BYTES = 256 * 1024;
    private static final long EXECUTOR_SHUTDOWN_GRACE_MILLIS = 2_000L;
    private static final String IMPORT_FILE_HEADER = "X-LazyBuilder-File-Name";
    private static final String IMPORT_SHA_HEADER = "X-LazyBuilder-Sha256";

    private final JavaPlugin plugin;
    private final PaperMainThreadDispatcher mainThread;
    private final WorldRegistry registry;
    private final WorldCreationService creation;
    private final WorldSettingsService settings;
    private final WorldLifecycleService lifecycle;
    private final WorldBackupService backupService;
    private final WorldHeavyOperationOrchestrator heavyOperations;
    private final LocalControlImportUploadService importUploads;
    private final WorldTaskRegistry tasks;
    private final WorldTaskRunner taskRunner;

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicBoolean stopping = new AtomicBoolean(true);

    public PaperLocalControlServer(
            JavaPlugin plugin,
            WorldRegistry registry,
            WorldCreationService creation,
            WorldSettingsService settings,
            WorldLifecycleService lifecycle,
            WorldBackupService backupService,
            WorldHeavyOperationOrchestrator heavyOperations,
            LocalControlImportUploadService importUploads,
            WorldTaskRegistry tasks,
            WorldTaskRunner taskRunner
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThread = new PaperMainThreadDispatcher(plugin);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.creation = Objects.requireNonNull(creation, "creation");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.backupService = Objects.requireNonNull(backupService, "backupService");
        this.heavyOperations = Objects.requireNonNull(heavyOperations, "heavyOperations");
        this.importUploads = Objects.requireNonNull(importUploads, "importUploads");
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
            created.createContext("/v1/imports/upload", exchange -> handleImportUpload(exchange, token));
            stopping.set(false);
            created.start();
            server = created;
            executor = createdExecutor;
            plugin.getLogger().info("World-Manager local desktop bridge ready on 127.0.0.1:" + port + ".");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start World-Manager local desktop bridge", exception);
        }
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true) && server == null && executor == null) {
            return;
        }

        HttpServer current = server;
        server = null;
        if (current != null) current.stop(0);
        importUploads.stop();

        ExecutorService currentExecutor = executor;
        executor = null;
        if (currentExecutor == null) return;

        currentExecutor.shutdown();
        try {
            if (currentExecutor.awaitTermination(
                    EXECUTOR_SHUTDOWN_GRACE_MILLIS,
                    TimeUnit.MILLISECONDS)) {
                return;
            }
            currentExecutor.shutdownNow();
            if (!currentExecutor.awaitTermination(
                    EXECUTOR_SHUTDOWN_GRACE_MILLIS,
                    TimeUnit.MILLISECONDS)) {
                plugin.getLogger().warning(
                        "Local World control handlers did not terminate after forced shutdown.");
            }
        } catch (InterruptedException interrupted) {
            currentExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            plugin.getLogger().warning(
                    "Interrupted while stopping Local World control handlers.");
        }
    }

    private void handleStatus(HttpExchange exchange, String token) throws IOException {
        if (rejectDuringShutdown(exchange)) return;
        if (!authorize(exchange, token)) return;
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method_not_allowed", "Only GET is supported.");
            return;
        }
        sendJson(exchange, 200, new StatusResponse("ready", PROTOCOL_VERSION));
    }

    private void handleWorlds(HttpExchange exchange, String token) throws IOException {
        if (rejectDuringShutdown(exchange)) return;
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
            if ("settings".equals(parts[1])) {
                handleSettings(exchange, worldId);
            } else {
                sendError(exchange, 404, "not_found", "Unknown World-Manager route.");
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

    private void handleImportUpload(HttpExchange exchange, String token) throws IOException {
        if (rejectDuringShutdown(exchange)) return;
        if (!authorize(exchange, token)) return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method_not_allowed", "Only POST is supported.");
            return;
        }

        try {
            String fileName = requireNonBlank(exchange.getRequestHeaders().getFirst(IMPORT_FILE_HEADER), "fileName");
            String sha256 = requireNonBlank(exchange.getRequestHeaders().getFirst(IMPORT_SHA_HEADER), "sha256");
            String contentLength = requireNonBlank(exchange.getRequestHeaders().getFirst("Content-Length"), "Content-Length");
            long totalBytes;
            try {
                totalBytes = Long.parseLong(contentLength);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Content-Length must be a valid positive integer", exception);
            }
            var result = importUploads.upload(fileName, totalBytes, sha256, exchange.getRequestBody());
            sendJson(exchange, 201, new ImportUploadResponse(result.fileName(), result.totalBytes()));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_upload", exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, "upload_conflict", exception.getMessage());
        } catch (Exception exception) {
            plugin.getLogger().warning("Local World import upload failed: " + exception.getMessage());
            sendError(exchange, 500, "upload_failed", "World-Manager could not store the import artifact.");
        }
    }

    private void handleTasks(HttpExchange exchange, String token) throws IOException {
        if (rejectDuringShutdown(exchange)) return;
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
            sendJson(exchange, 200, new TaskListResponse(tasks.recent().stream()
                    .map(PaperLocalControlServer::taskResponse).toList()));
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
        switch (relative) {
            case "/duplicate" -> handleDuplicateTaskStart(exchange);
            case "/backup" -> handleBackupTaskStart(exchange);
            case "/export" -> handleExportTaskStart(exchange);
            case "/import" -> handleImportTaskStart(exchange);
            case "/delete" -> handleDeleteTaskStart(exchange);
            case "/archive", "/restore" -> handleLifecycleTaskStart(exchange, relative);
            default -> sendError(exchange, 404, "not_found", "Unknown task operation.");
        }
    }

    private void handleLifecycleTaskStart(HttpExchange exchange, String relative) throws Exception {
        WorldTaskType type = "/archive".equals(relative) ? WorldTaskType.ARCHIVE : WorldTaskType.RESTORE;
        TaskStartRequest request = readJson(exchange, TaskStartRequest.class);
        WorldId worldId = requireManagedWorldId(request.worldId());
        WorldTaskSnapshot queued = taskRunner.submit(
                type,
                worldId,
                type == WorldTaskType.ARCHIVE ? "Archive queued." : "Restore queued.",
                progress -> {
                    progress.update(20, "Dispatching lifecycle change to Paper.");
                    WorldRecord updated = mainThread.call(() -> type == WorldTaskType.ARCHIVE
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

    private void handleDuplicateTaskStart(HttpExchange exchange) throws Exception {
        DuplicateTaskStartRequest request = readJson(exchange, DuplicateTaskStartRequest.class);
        WorldId sourceId = requireManagedWorldId(request.worldId());
        String destinationFolder = requireNonBlank(request.destinationFolder(), "destinationFolder");
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? destinationFolder
                : request.displayName().trim();
        WorldTaskSnapshot queued = taskRunner.submit(
                WorldTaskType.DUPLICATE,
                sourceId,
                "Duplicate queued.",
                progress -> heavyOperations.duplicateWorld(
                        sourceId, destinationFolder, displayName, progress::update).id().toString()
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private void handleBackupTaskStart(HttpExchange exchange) throws Exception {
        TaskStartRequest request = readJson(exchange, TaskStartRequest.class);
        WorldId worldId = requireManagedWorldId(request.worldId());
        WorldTaskSnapshot queued = taskRunner.submit(
                WorldTaskType.BACKUP,
                worldId,
                "Backup queued.",
                progress -> runBackupTask(worldId, progress)
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private void handleExportTaskStart(HttpExchange exchange) throws Exception {
        ExportTaskStartRequest request = readJson(exchange, ExportTaskStartRequest.class);
        WorldId worldId = requireManagedWorldId(request.worldId());
        String targetFormat = requireNonBlank(request.targetFormat(), "targetFormat");
        String artifactName = requireNonBlank(request.artifactName(), "artifactName");
        WorldTaskSnapshot queued = taskRunner.submit(
                WorldTaskType.EXPORT,
                worldId,
                "Export queued.",
                progress -> heavyOperations.exportWorld(
                        worldId, targetFormat, artifactName, progress::update).artifact().getFileName().toString()
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private void handleImportTaskStart(HttpExchange exchange) throws Exception {
        ImportTaskStartRequest request = readJson(exchange, ImportTaskStartRequest.class);
        String artifactName = requireNonBlank(request.artifactName(), "artifactName");
        String destinationFolder = requireNonBlank(request.destinationFolder(), "destinationFolder");
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? destinationFolder
                : request.displayName().trim();
        WorldTaskSnapshot queued = taskRunner.submit(
                WorldTaskType.IMPORT,
                null,
                "Import queued.",
                progress -> heavyOperations.importWorld(
                        artifactName, destinationFolder, displayName, progress::update).id().toString()
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private void handleDeleteTaskStart(HttpExchange exchange) throws Exception {
        DeleteTaskStartRequest request = readJson(exchange, DeleteTaskStartRequest.class);
        WorldId worldId = requireManagedWorldId(request.worldId());
        String confirmation = requireNonBlank(request.typedDisplayName(), "typedDisplayName");
        WorldTaskSnapshot queued = taskRunner.submit(
                WorldTaskType.DELETE,
                worldId,
                "Delete queued.",
                progress -> heavyOperations.deleteWorld(
                        worldId, confirmation, progress::update).displayName()
        );
        sendJson(exchange, 202, taskResponse(queued));
    }

    private String runBackupTask(WorldId worldId, WorldTaskWork.Progress progress) throws Exception {
        progress.update(10, "Preparing source world on Paper.");
        WorldBackupService.BackupTask backupTask = mainThread.call(() -> backupService.prepare(worldId));
        Exception failure = null;
        boolean postCommitWarning = false;
        WorldBackupService.BackupResult result = null;
        try {
            progress.update(30, "Creating consistent world snapshot.");
            result = backupService.executeFilePhase(backupTask);
            progress.update(85, "Backup stored; restoring source runtime state.");
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            mainThread.callCleanup(() -> {
                backupService.finish(backupTask);
                return null;
            });
        } catch (Exception finishFailure) {
            if (failure == null && backupTask.committed() && backupTask.closed() && result != null) {
                postCommitWarning = true;
                progress.update(99,
                        "Backup committed, but source runtime restoration failed; check the source world state.");
            } else {
                failure = combine(failure, finishFailure);
            }
        }
        if (failure != null) throw failure;
        if (!postCommitWarning) progress.update(95, "Backup finalized.");
        return Objects.requireNonNull(result, "result").backupId();
    }

    private static Exception combine(Exception primary, Exception secondary) {
        if (primary == null) return secondary;
        primary.addSuppressed(secondary);
        return primary;
    }

    private WorldId requireManagedWorldId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("worldId must not be blank");
        WorldId worldId;
        try {
            worldId = WorldId.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("World id is invalid.", exception);
        }
        if (registry.find(worldId).isEmpty()) throw new IllegalArgumentException("World is not managed: " + worldId);
        return worldId;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private void handleWorldCollection(HttpExchange exchange) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, new WorldListResponse(mainThread.call(() -> registry.all().stream()
                    .map(PaperLocalControlServer::summary).toList())));
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
            WorldRecord created = mainThread.call(() -> creation.create(request.folderName().trim(), displayName, kind));
            sendJson(exchange, 201, summary(created));
            return;
        }
        sendError(exchange, 405, "method_not_allowed", "Only GET and POST are supported.");
    }

    private void handleSettings(HttpExchange exchange, WorldId worldId) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 200, settingsResponse(mainThread.call(() -> settings.snapshot(worldId))));
            return;
        }
        if ("PATCH".equals(exchange.getRequestMethod())) {
            UpdateWorldSettingsRequest request = readJson(exchange, UpdateWorldSettingsRequest.class);
            sendJson(exchange, 200, settingsResponse(mainThread.call(() -> applySettings(worldId, request))));
            return;
        }
        sendError(exchange, 405, "method_not_allowed", "Only GET and PATCH are supported.");
    }

    private WorldSettingsSnapshot applySettings(WorldId worldId, UpdateWorldSettingsRequest request) {
        WorldGameMode defaultMode = request.defaultGameMode() == null
                ? null
                : WorldGameMode.valueOf(request.defaultGameMode().trim().toUpperCase());
        WorldWeather weather = request.weather() == null
                ? null
                : WorldWeather.valueOf(request.weather().trim().toUpperCase());
        return settings.applyBatch(
                worldId,
                defaultMode,
                request.timeOfDayTicks(),
                weather,
                request.naturalSpawning(),
                request.daylightCycle(),
                request.weatherCycle()
        );
    }

    private static ManagedWorldResponse summary(WorldRecord world) {
        return new ManagedWorldResponse(
                world.id().toString(), world.displayName(), world.kind().name(),
                world.lifecycle().name(), world.defaultGameMode());
    }

    private static TaskResponse taskResponse(WorldTaskSnapshot snapshot) {
        return new TaskResponse(
                snapshot.taskId().toString(), snapshot.type().name(),
                snapshot.worldId() == null ? null : snapshot.worldId().toString(), snapshot.state().name(),
                snapshot.progressPercent(), snapshot.message(), snapshot.result(), snapshot.error(),
                snapshot.createdAt().toString(), snapshot.updatedAt().toString());
    }

    private static WorldKind parseCreateKind(String value) {
        if (value == null || value.isBlank()) return WorldKind.FLAT;
        WorldKind kind = WorldKind.valueOf(value.trim().toUpperCase());
        if (kind == WorldKind.IMPORTED) throw new IllegalArgumentException("Create World supports only FLAT or VOID.");
        return kind;
    }

    private static WorldSettingsResponse settingsResponse(WorldSettingsSnapshot snapshot) {
        return new WorldSettingsResponse(
                snapshot.world().id().toString(), snapshot.world().displayName(),
                snapshot.defaultGameMode().name(), snapshot.runtime().timeOfDayTicks(),
                snapshot.runtime().weather().name(), snapshot.runtime().spawning().naturalSpawning(),
                gameRuleBoolean(snapshot, "doDaylightCycle"), gameRuleBoolean(snapshot, "doWeatherCycle"));
    }

    private static boolean gameRuleBoolean(WorldSettingsSnapshot snapshot, String name) {
        return snapshot.runtime().gamerules().stream()
                .filter(rule -> rule.name().equalsIgnoreCase(name))
                .findFirst().map(GameRuleSetting::value).map(Boolean::parseBoolean).orElse(false);
    }

    private static <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_JSON_BODY_BYTES + 1);
        if (body.length > MAX_JSON_BODY_BYTES) {
            throw new IllegalArgumentException("Request body exceeds the local control JSON limit.");
        }
        if (body.length == 0) throw new IllegalArgumentException("Request body is required.");
        try {
            T payload = GSON.fromJson(new String(body, StandardCharsets.UTF_8), type);
            if (payload == null) throw new IllegalArgumentException("Request body is required.");
            return payload;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Request body contains invalid JSON.", exception);
        }
    }

    private boolean rejectDuringShutdown(HttpExchange exchange) throws IOException {
        if (!stopping.get()) return false;
        sendError(exchange, 503, "world_manager_stopping", "World-Manager is stopping.");
        return true;
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
    private record ImportUploadResponse(String fileName, long totalBytes) {}
    private record WorldListResponse(List<ManagedWorldResponse> worlds) {}
    private record TaskListResponse(List<TaskResponse> tasks) {}
    private record ManagedWorldResponse(String id, String displayName, String kind, String lifecycle,
                                        String defaultGameMode) {}
    private record TaskResponse(String taskId, String type, String worldId, String state, int progressPercent,
                                String message, String result, String error, String createdAt, String updatedAt) {}
    private record TaskStartRequest(String worldId) {}
    private record DuplicateTaskStartRequest(String worldId, String destinationFolder, String displayName) {}
    private record ExportTaskStartRequest(String worldId, String targetFormat, String artifactName) {}
    private record ImportTaskStartRequest(String artifactName, String destinationFolder, String displayName) {}
    private record DeleteTaskStartRequest(String worldId, String typedDisplayName) {}
    private record CreateWorldRequest(String folderName, String displayName, String kind) {}
    private record UpdateWorldSettingsRequest(String defaultGameMode, Long timeOfDayTicks,
                                               String weather, Boolean naturalSpawning, Boolean daylightCycle,
                                               Boolean weatherCycle) {}
    private record WorldSettingsResponse(String id, String displayName, String defaultGameMode,
                                         long timeOfDayTicks, String weather, boolean naturalSpawning,
                                         boolean daylightCycle, boolean weatherCycle) {}
}
