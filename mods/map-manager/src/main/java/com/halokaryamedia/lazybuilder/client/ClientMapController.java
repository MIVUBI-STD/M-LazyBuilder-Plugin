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
    private MapActionWireProtocol.CurrentWorldResult currentWorld;
    private boolean teleportPending;
    private boolean exportBusy;
    private String lastError;
    private long revision;

    public ClientMapController(Consumer<String> completedExportHandler) {
        this.completedExportHandler = Objects.requireNonNull(completedExportHandler, "completedExportHandler");
    }

    public void refreshCurrentWorld() {
        LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.currentWorldRequest());
    }

    public void teleportCurrent(int blockX, int blockZ) {
        if (teleportPending) return;
        MapActionWireProtocol.CurrentWorldResult current = currentWorld;
        if (current == null) {
            LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: current world is not managed yet.");
            return;
        }
        WorldId worldId = current.worldId();
        teleportPending = true;
        lastError = null;
        revision++;
        try {
            LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.teleportRequest(worldId, blockX, blockZ));
        } catch (RuntimeException exception) {
            teleportPending = false;
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
        if (exportBusy) throw new IllegalStateException("An Export Area request is already active");
        MapActionWireProtocol.CurrentWorldResult current = currentWorld;
        if (current == null) {
            throw new IllegalStateException("Current managed world is not available for area export");
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            throw new IllegalStateException("Current dimension is not available for area export");
        }

        WorldId worldId = current.worldId();
        String dimensionId = client.world.getRegistryKey().getValue().toString();
        exportBusy = true;
        lastError = null;
        revision++;
        try {
            LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.exportAreaRequest(
                    worldId, dimensionId, x1, z1, x2, z2, targetFormat, artifactName,
                    Objects.requireNonNull(settings, "settings")));
        } catch (RuntimeException exception) {
            exportBusy = false;
            lastError = "Could not send Export Area request";
            revision++;
            throw exception;
        }
    }

    public void accept(MapActionWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case MapActionWireProtocol.CurrentWorldResult current -> {
                currentWorld = current;
                lastError = null;
                revision++;
            }
            case MapActionWireProtocol.CurrentWorldCleared ignored -> {
                currentWorld = null;
                teleportPending = false;
                lastError = null;
                revision++;
            }
            case MapActionWireProtocol.TeleportOk teleport -> {
                teleportPending = false;
                lastError = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer(
                        "Teleported to " + floor(teleport.x()) + ", " + floor(teleport.y()) + ", " + floor(teleport.z()));
            }
            case MapActionWireProtocol.ExportAccepted ignored -> {
                lastError = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("Export started.");
            }
            case MapActionWireProtocol.ExportComplete complete -> {
                exportBusy = false;
                lastError = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("Export ready: " + complete.fileName());
                completedExportHandler.accept(complete.fileName());
            }
            case MapActionWireProtocol.ErrorResponse error -> {
                teleportPending = false;
                exportBusy = false;
                lastError = error.message();
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + error.message());
            }
        }
    }

    public MapActionWireProtocol.CurrentWorldResult currentWorld() {
        return currentWorld;
    }

    public boolean teleportPending() {
        return teleportPending;
    }

    public boolean exportBusy() {
        return exportBusy;
    }

    public String lastError() {
        return lastError;
    }

    public long revision() {
        return revision;
    }

    public void reset() {
        currentWorld = null;
        teleportPending = false;
        exportBusy = false;
        lastError = null;
        revision++;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
