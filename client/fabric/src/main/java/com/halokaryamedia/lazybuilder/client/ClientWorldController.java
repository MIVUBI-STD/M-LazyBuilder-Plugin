package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Client presentation state for the general World Manager surface. */
public final class ClientWorldController {
    private final Consumer<String> completedExportHandler;
    private List<WorldControlWireProtocol.WorldSummary> worlds = List.of();
    private final Map<UUID, WorldControlWireProtocol.SettingsSnapshot> settings = new HashMap<>();
    private String lastError;
    private String activityMessage;
    private long revision;

    public ClientWorldController(Consumer<String> completedExportHandler) {
        this.completedExportHandler = Objects.requireNonNull(completedExportHandler, "completedExportHandler");
    }

    public void refresh() { send(new WorldControlWireProtocol.ListWorlds()); }

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

    public void exportWorld(UUID worldId, String targetFormat, String artifactName) {
        beginActivity("Preparing world export…");
        send(new WorldControlWireProtocol.ExportWorld(worldId, targetFormat, artifactName));
    }

    public void importWorld(String artifactName, String destinationFolder, String displayName) {
        beginActivity("Validating and importing world…");
        send(new WorldControlWireProtocol.ImportWorld(artifactName, destinationFolder, displayName));
    }

    /** Teleport owns any required world load; manual load/unload is intentionally not a client action. */
    public void teleport(UUID worldId) { send(new WorldControlWireProtocol.TeleportWorld(worldId)); }
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
                lastError = null;
                revision++;
            }
            case WorldControlWireProtocol.WorldChanged changed -> {
                if ("DELETE".equals(changed.action())) {
                    remove(changed.world().worldId());
                    settings.remove(changed.world().worldId());
                } else replace(changed.world());
                lastError = null;
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer(
                        "World " + changed.action().toLowerCase() + ": " + changed.world().displayName());
            }
            case WorldControlWireProtocol.TeleportOk ok -> {
                replace(ok.world());
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
                lastError = null;
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("Export ready: " + export.artifactName());
                completedExportHandler.accept(export.artifactName());
            }
            case WorldControlWireProtocol.ErrorResponse error -> {
                lastError = error.message();
                activityMessage = null;
                revision++;
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder world: " + error.message());
            }
        }
    }

    public void reset() {
        worlds = List.of();
        settings.clear();
        lastError = null;
        activityMessage = null;
        revision++;
    }

    public List<WorldControlWireProtocol.WorldSummary> worlds() { return worlds; }
    public WorldControlWireProtocol.SettingsSnapshot settings(UUID worldId) { return settings.get(worldId); }
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
