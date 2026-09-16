package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Client presentation state for the general World Manager surface. */
public final class ClientWorldController {
    private static final String NATIVE_EXPORT_FORMAT = "JAVA_1_21_4";

    private final Consumer<String> completedExportHandler;
    private List<WorldControlWireProtocol.WorldSummary> worlds = List.of();
    private List<String> exportFormats = List.of(NATIVE_EXPORT_FORMAT);
    private final Map<UUID, WorldControlWireProtocol.SettingsSnapshot> settings = new HashMap<>();
    private final Set<String> discardWhenInspected = new HashSet<>();
    private WorldControlWireProtocol.ImportInspection importInspection;
    // Before the first authoritative WorldList arrives permission is unknown, not denied.
    // Map-first actions therefore remain discoverable; Paper still authorizes every request.
    private boolean canManage = true;
    private boolean canTeleport = true;
    private boolean worldListReady;
    private boolean worldListPending;
    private boolean teleportPending;
    private boolean exportPending;
    private String lastError;
    private String activityMessage;
    private long revision;

    public ClientWorldController(Consumer<String> completedExportHandler) {
        this.completedExportHandler = Objects.requireNonNull(completedExportHandler, "completedExportHandler");
    }

    public void refresh() {
        worldListPending = true;
        lastError = null;
        revision++;
        send(new WorldControlWireProtocol.ListWorlds());
    }

    public void requestExportFormats() { send(new WorldControlWireProtocol.GetExportFormats()); }

    public void inspectImport(String artifactName) {
        importInspection = null;
        discardWhenInspected.remove(artifactName);
        beginActivity("Inspecting world…");
        send(new WorldControlWireProtocol.InspectImport(artifactName));
    }

    /**
     * Best-effort cleanup for an uploaded artifact that the builder reviewed but
     * chose not to import. If inspection is still in flight, remember the intent
     * and issue the discard again when the authoritative inspection arrives.
     */
    public void discardImport(String artifactName) {
        if (artifactName == null || artifactName.isBlank()) return;
        String normalized = artifactName.strip();
        boolean inspectionKnown = importInspection != null
                && importInspection.artifactName().equals(normalized);
        if (inspectionKnown) {
            importInspection = null;
        } else {
            discardWhenInspected.add(normalized);
        }
        send(new WorldControlWireProtocol.DiscardImport(normalized));
    }

    public void create(String folderName, String displayName, String kind) {
        beginActivity("Creating world…");
        send(new WorldControlWireProtocol.CreateWorld(folderName, displayName, kind));
    }

    public void duplicateWorld(UUID sourceWorldId, String destinationFolder, String displayName) {
        beginActivity("Duplicating world…");
        send(new WorldControlWireProtocol.DuplicateWorld(sourceWorldId, destinationFolder, displayName));
    }

    public void deleteWorld(UUID worldId, String typedWorldName) {
        beginActivity("Deleting world…");
        send(new WorldControlWireProtocol.DeleteWorld(worldId, typedWorldName));
    }

    /** Legacy export path retained for the existing transfer screen. */
    public void exportWorld(UUID worldId, String targetFormat, String artifactName) {
        exportWorld(worldId, targetFormat, artifactName, ExportSettingsWire.Settings.inherit());
    }

    /** Export-workspace path. Settings are applied to the export artifact only. */
    public void exportWorld(
            UUID worldId,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings exportSettings
    ) {
        if (exportPending) throw new IllegalStateException("A world export is already active");
        exportPending = true;
        beginActivity("Preparing world export…");
        try {
            send(new WorldControlWireProtocol.ExportWorld(
                    worldId, targetFormat, artifactName, Objects.requireNonNull(exportSettings, "exportSettings")));
        } catch (RuntimeException exception) {
            exportPending = false;
            activityMessage = null;
            lastError = "Could not send world export request";
            revision++;
            throw exception;
        }
    }

    public void importWorld(String artifactName, String destinationFolder, String displayName) {
        discardWhenInspected.remove(artifactName);
        beginActivity("Validating and importing world…");
        send(new WorldControlWireProtocol.ImportWorld(artifactName, destinationFolder, displayName));
    }

    /** Teleport owns any required world load; manual load/unload is intentionally not a client action. */
    public void teleport(UUID worldId) {
        if (teleportPending) return;
        teleportPending = true;
        activityMessage = "Teleporting…";
        lastError = null;
        revision++;
        try {
            send(new WorldControlWireProtocol.TeleportWorld(worldId));
        } catch (RuntimeException exception) {
            teleportPending = false;
            activityMessage = null;
            lastError = "Could not send teleport request";
            revision++;
            throw exception;
        }
    }
    public void archive(UUID worldId) { send(new WorldControlWireProtocol.ArchiveWorld(worldId)); }
    public void restore(UUID worldId) { send(new WorldControlWireProtocol.RestoreWorld(worldId)); }
    public void requestSettings(UUID worldId) { send(new WorldControlWireProtocol.GetSettings(worldId)); }
    public void setDefaultMode(UUID worldId, String mode) { send(new WorldControlWireProtocol.SetDefaultMode(worldId, mode)); }
    public void setDifficulty(UUID worldId, String difficulty) { send(new WorldControlWireProtocol.SetDifficulty(worldId, difficulty)); }
    public void setPvp(UUID worldId, boolean enabled) { send(new WorldControlWireProtocol.SetPvp(worldId, enabled)); }
    public void resetBuildReady(UUID worldId) { send(new WorldControlWireProtocol.ResetBuildReady(worldId)); }
    public void setSpawnHere(UUID worldId) { send(new WorldControlWireProtocol.SetSpawnHere(worldId)); }

    public void accept(WorldControlWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case WorldControlWireProtocol.WorldList list -> {
                worlds = list.worlds();
                canManage = list.canManage();
                canTeleport = list.canTeleport();
                worldListReady = true;
                worldListPending = false;
                lastError = null;
                revision++;
            }
            case WorldControlWireProtocol.WorldChanged changed -> {
                if ("DELETE".equals(changed.action())) {
                    remove(changed.world().worldId());
                    settings.remove(changed.world().worldId());
                } else replace(changed.world());
                if ("IMPORT".equals(changed.action())) importInspection = null;
                lastError = null;
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer(
                        "World " + changed.action().toLowerCase() + ": " + changed.world().displayName());
            }
            case WorldControlWireProtocol.TeleportOk ok -> {
                replace(ok.world());
                teleportPending = false;
                activityMessage = null;
                lastError = null;
                revision++;
            }
            case WorldControlWireProtocol.SettingsSnapshot snapshot -> {
                settings.put(snapshot.worldId(), snapshot);
                synchronizeSummary(snapshot);
                lastError = null;
                revision++;
            }
            case WorldControlWireProtocol.ExportReady export -> {
                exportPending = false;
                lastError = null;
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("Export ready: " + export.artifactName());
                completedExportHandler.accept(export.artifactName());
            }
            case WorldControlWireProtocol.ExportFormats formats -> {
                exportFormats = formats.formats();
                lastError = null;
                revision++;
            }
            case WorldControlWireProtocol.ImportInspection inspection -> {
                lastError = null;
                activityMessage = null;
                if (discardWhenInspected.remove(inspection.artifactName())) {
                    importInspection = null;
                    send(new WorldControlWireProtocol.DiscardImport(inspection.artifactName()));
                } else {
                    importInspection = inspection;
                }
                revision++;
            }
            case WorldControlWireProtocol.ErrorResponse error -> {
                if (worldListPending) worldListPending = false;
                teleportPending = false;
                exportPending = false;
                discardWhenInspected.clear();
                lastError = error.message();
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder world: " + error.message());
            }
        }
    }

    public void reset() {
        worlds = List.of();
        exportFormats = List.of(NATIVE_EXPORT_FORMAT);
        settings.clear();
        discardWhenInspected.clear();
        importInspection = null;
        canManage = true;
        canTeleport = true;
        worldListReady = false;
        worldListPending = false;
        teleportPending = false;
        exportPending = false;
        lastError = null;
        activityMessage = null;
        revision++;
    }

    public List<WorldControlWireProtocol.WorldSummary> worlds() { return worlds; }
    public List<String> exportFormats() { return exportFormats; }
    public boolean canManage() { return canManage; }
    public boolean canTeleport() { return canTeleport; }
    public boolean worldListReady() { return worldListReady; }
    public boolean worldListPending() { return worldListPending; }
    public boolean teleportPending() { return teleportPending; }
    public boolean exportPending() { return exportPending; }
    public WorldControlWireProtocol.SettingsSnapshot settings(UUID worldId) { return settings.get(worldId); }
    public WorldControlWireProtocol.ImportInspection importInspection() { return importInspection; }
    public String lastError() { return lastError; }
    public String activityMessage() { return activityMessage; }
    public long revision() { return revision; }

    private void beginActivity(String message) {
        activityMessage = message;
        lastError = null;
        revision++;
    }

    private void synchronizeSummary(WorldControlWireProtocol.SettingsSnapshot snapshot) {
        for (WorldControlWireProtocol.WorldSummary world : worlds) {
            if (!world.worldId().equals(snapshot.worldId())) continue;
            replace(new WorldControlWireProtocol.WorldSummary(
                    world.worldId(), world.folderName(), world.displayName(), world.kind(), world.lifecycle(),
                    snapshot.defaultGameMode()));
            return;
        }
    }

    private void replace(WorldControlWireProtocol.WorldSummary updated) {
        boolean found = false;
        var builder = new java.util.ArrayList<WorldControlWireProtocol.WorldSummary>(worlds.size() + 1);
        for (WorldControlWireProtocol.WorldSummary world : worlds) {
            if (world.worldId().equals(updated.worldId())) {
                builder.add(updated);
                found = true;
            } else builder.add(world);
        }
        if (!found) builder.add(updated);
        worlds = List.copyOf(builder);
    }

    private void remove(UUID worldId) {
        worlds = worlds.stream().filter(world -> !world.worldId().equals(worldId)).toList();
    }

    private static void send(WorldControlWireProtocol.Request request) {
        try {
            LazyBuilderClientNetworking.sendWorld(WorldControlWireProtocol.encodeRequest(request));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode world-control request", exception);
        }
    }
}
