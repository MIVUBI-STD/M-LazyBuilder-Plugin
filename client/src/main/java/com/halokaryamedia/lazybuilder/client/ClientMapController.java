package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.util.Objects;

/**
 * Client-side presentation state for map actions. The server remains the
 * authority for managed-world identity, teleport resolution, and export work.
 */
public final class ClientMapController {
    private MapActionWireProtocol.CurrentWorldResult currentWorld;
    private boolean exportBusy;

    public void refreshCurrentWorld() {
        LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.currentWorldRequest());
    }

    public void teleportCurrent(int blockX, int blockZ) {
        WorldId worldId = requireCurrentWorld().worldId();
        LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.teleportRequest(worldId, blockX, blockZ));
    }

    public void exportAreaCurrent(
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName
    ) {
        if (exportBusy) throw new IllegalStateException("An Export Area request is already active");
        WorldId worldId = requireCurrentWorld().worldId();
        exportBusy = true;
        LazyBuilderClientNetworking.sendMap(MapActionWireProtocol.exportAreaRequest(
                worldId, x1, z1, x2, z2, targetFormat, artifactName));
    }

    public void accept(MapActionWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case MapActionWireProtocol.CurrentWorldResult current -> currentWorld = current;
            case MapActionWireProtocol.TeleportOk teleport -> LazyBuilderClientNetworking.notifyPlayer(
                    "Teleported to " + floor(teleport.x()) + ", " + floor(teleport.y()) + ", " + floor(teleport.z()));
            case MapActionWireProtocol.ExportAccepted ignored -> LazyBuilderClientNetworking.notifyPlayer(
                    "Export Area started.");
            case MapActionWireProtocol.ExportComplete complete -> {
                exportBusy = false;
                LazyBuilderClientNetworking.notifyPlayer(
                        "Export Area ready: " + complete.fileName());
            }
            case MapActionWireProtocol.ErrorResponse error -> {
                exportBusy = false;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder: " + error.message());
            }
        }
    }

    public MapActionWireProtocol.CurrentWorldResult currentWorld() {
        return currentWorld;
    }

    public void reset() {
        currentWorld = null;
        exportBusy = false;
    }

    private MapActionWireProtocol.CurrentWorldResult requireCurrentWorld() {
        if (currentWorld == null) throw new IllegalStateException("Current managed world is not resolved yet");
        return currentWorld;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
