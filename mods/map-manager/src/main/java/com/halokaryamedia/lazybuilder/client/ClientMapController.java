package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Client-side presentation state for map actions. The server remains the
 * authority for managed-world identity, teleport resolution, and export work.
 */
public final class ClientMapController {
    private final Consumer<String> completedExportHandler;
    private final Consumer<byte[]> mapSender;
    private final Consumer<String> notifier;
    private MapActionWireProtocol.CurrentWorldResult currentWorld;
    private long currentWorldRequestId;
    private long teleportRequestId;
    private long exportRequestId;
    private long nextRequestId = 1L;
    private String lastError;
    private long revision;

    public ClientMapController(Consumer<String> completedExportHandler) {
        this(completedExportHandler, LazyBuilderClientNetworking::sendMap, LazyBuilderClientNetworking::notifyPlayer);
    }

    ClientMapController(
            Consumer<String> completedExportHandler,
            Consumer<byte[]> mapSender,
            Consumer<String> notifier
    ) {
        this.completedExportHandler = Objects.requireNonNull(completedExportHandler, "completedExportHandler");
        this.mapSender = Objects.requireNonNull(mapSender, "mapSender");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    public void refreshCurrentWorld() {
        long requestId = newRequestId();
        currentWorldRequestId = requestId;
        try {
            mapSender.accept(MapActionWireProtocol.currentWorldRequest(requestId));
        } catch (RuntimeException exception) {
            currentWorldRequestId = 0L;
            lastError = "Could not refresh current world";
            revision++;
            throw exception;
        }
    }

    public void teleportCurrent(int blockX, int blockZ) {
        if (teleportRequestId != 0L) return;
        MapActionWireProtocol.CurrentWorldResult current = currentWorld;
        if (current == null) {
            notifier.accept("LazyBuilder: current world is not managed yet.");
            return;
        }
        WorldId worldId = current.worldId();
        long requestId = newRequestId();
        teleportRequestId = requestId;
        lastError = null;
        revision++;
        try {
            mapSender.accept(MapActionWireProtocol.teleportRequest(requestId, worldId, blockX, blockZ));
        } catch (RuntimeException exception) {
            teleportRequestId = 0L;
            lastError = "Could not send teleport request";
            revision++;
            throw exception;
        }
    }

    /** Legacy export path retained for compatibility with the old transfer screen. */
    public void exportAreaCurrent(
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName
    ) {
        exportAreaCurrent(x1, z1, x2, z2, targetFormat, artifactName, ExportSettingsWire.Settings.inherit());
    }

    /** Export-workspace path. Settings are export-only and never mutate the source world. */
    public void exportAreaCurrent(
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings settings
    ) {
        MapActionWireProtocol.CurrentWorldResult current = currentWorld;
        if (current == null) {
            throw new IllegalStateException("Current managed world is not available for area export");
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            throw new IllegalStateException("Current dimension is not available for area export");
        }
        exportArea(
                current.worldId(),
                client.world.getRegistryKey().getValue().toString(),
                x1, z1, x2, z2, targetFormat, artifactName, settings
        );
    }

    void exportArea(
            WorldId worldId,
            String dimensionId,
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings settings
    ) {
        if (exportRequestId != 0L) throw new IllegalStateException("An Export Area request is already active");
        long requestId = newRequestId();
        exportRequestId = requestId;
        lastError = null;
        revision++;
        try {
            mapSender.accept(MapActionWireProtocol.exportAreaRequest(
                    requestId,
                    Objects.requireNonNull(worldId, "worldId"),
                    Objects.requireNonNull(dimensionId, "dimensionId"),
                    x1, z1, x2, z2, targetFormat, artifactName,
                    Objects.requireNonNull(settings, "settings")));
        } catch (RuntimeException exception) {
            exportRequestId = 0L;
            lastError = "Could not send Export Area request";
            revision++;
            throw exception;
        }
    }

    public void accept(MapActionWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case MapActionWireProtocol.CurrentWorldResult current -> {
                if (!acceptCurrentWorldResponse(current.requestId())) return;
                currentWorld = current;
                lastError = null;
                revision++;
            }
            case MapActionWireProtocol.CurrentWorldCleared cleared -> {
                if (!acceptCurrentWorldResponse(cleared.requestId())) return;
                currentWorld = null;
                if (cleared.requestId() == 0L) {
                    teleportRequestId = 0L;
                }
                lastError = null;
                revision++;
            }
            case MapActionWireProtocol.TeleportOk teleport -> {
                if (teleportRequestId == 0L || teleport.requestId() != teleportRequestId) return;
                teleportRequestId = 0L;
                lastError = null;
                revision++;
                notifier.accept(
                        "Teleported to " + floor(teleport.x()) + ", " + floor(teleport.y()) + ", " + floor(teleport.z()));
            }
            case MapActionWireProtocol.ExportAccepted accepted -> {
                if (exportRequestId == 0L || accepted.requestId() != exportRequestId) return;
                lastError = null;
                revision++;
                notifier.accept("Export started.");
            }
            case MapActionWireProtocol.ExportComplete complete -> {
                if (exportRequestId != 0L) {
                    if (complete.requestId() != exportRequestId) return;
                    exportRequestId = 0L;
                    lastError = null;
                    revision++;
                } else if (complete.requestId() == 0L) {
                    return;
                }
                notifier.accept("Export ready: " + complete.fileName());
                completedExportHandler.accept(complete.fileName());
            }
            case MapActionWireProtocol.ErrorResponse error -> {
                boolean matched = error.requestId() == 0L;
                boolean changed = false;
                if (teleportRequestId != 0L && error.requestId() == teleportRequestId) {
                    teleportRequestId = 0L;
                    matched = true;
                    changed = true;
                }
                if (exportRequestId != 0L && error.requestId() == exportRequestId) {
                    exportRequestId = 0L;
                    matched = true;
                    changed = true;
                }
                if (currentWorldRequestId != 0L && error.requestId() == currentWorldRequestId) {
                    currentWorldRequestId = 0L;
                    matched = true;
                    changed = true;
                }
                if (!matched) return;
                if (changed) {
                    lastError = error.message();
                    revision++;
                }
                notifier.accept("LazyBuilder: " + error.message());
            }
        }
    }

    private boolean acceptCurrentWorldResponse(long requestId) {
        if (requestId == 0L) {
            currentWorldRequestId = 0L;
            return true;
        }
        if (requestId != currentWorldRequestId) return false;
        currentWorldRequestId = 0L;
        return true;
    }

    public MapActionWireProtocol.CurrentWorldResult currentWorld() {
        return currentWorld;
    }

    public boolean teleportPending() {
        return teleportRequestId != 0L;
    }

    public boolean exportBusy() {
        return exportRequestId != 0L;
    }

    public String lastError() {
        return lastError;
    }

    public long revision() {
        return revision;
    }

    long activeCurrentWorldRequestId() {
        return currentWorldRequestId;
    }

    long activeTeleportRequestId() {
        return teleportRequestId;
    }

    long activeExportRequestId() {
        return exportRequestId;
    }

    /**
     * Invalidates presentation identity on a connected world/dimension edge without
     * cancelling request-bound work that may legitimately continue in the background.
     */
    void clearCurrentWorldForTransition() {
        currentWorld = null;
        currentWorldRequestId = 0L;
        revision++;
    }

    public void reset() {
        currentWorld = null;
        currentWorldRequestId = 0L;
        teleportRequestId = 0L;
        exportRequestId = 0L;
        lastError = null;
        revision++;
    }

    private long newRequestId() {
        long id = nextRequestId++;
        if (id > 0L) return id;
        nextRequestId = 2L;
        return 1L;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
