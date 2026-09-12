package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Client presentation state for the general World Manager surface. */
public final class ClientWorldController {
    private List<WorldControlWireProtocol.WorldSummary> worlds = List.of();
    private String lastError;

    public void refresh() {
        send(new WorldControlWireProtocol.ListWorlds());
    }

    public void create(String folderName, String displayName, String kind) {
        send(new WorldControlWireProtocol.CreateWorld(folderName, displayName, kind));
    }

    public void load(UUID worldId) { send(new WorldControlWireProtocol.LoadWorld(worldId)); }
    public void unload(UUID worldId) { send(new WorldControlWireProtocol.UnloadWorld(worldId)); }
    public void teleport(UUID worldId) { send(new WorldControlWireProtocol.TeleportWorld(worldId)); }
    public void archive(UUID worldId) { send(new WorldControlWireProtocol.ArchiveWorld(worldId)); }
    public void restore(UUID worldId) { send(new WorldControlWireProtocol.RestoreWorld(worldId)); }

    public void accept(WorldControlWireProtocol.Response response) {
        Objects.requireNonNull(response, "response");
        switch (response) {
            case WorldControlWireProtocol.WorldList list -> {
                worlds = list.worlds();
                lastError = null;
            }
            case WorldControlWireProtocol.WorldChanged changed -> {
                replace(changed.world());
                lastError = null;
                LazyBuilderClientNetworking.notifyPlayer(
                        "World " + changed.action().toLowerCase() + ": " + changed.world().displayName());
            }
            case WorldControlWireProtocol.TeleportOk ok -> {
                replace(ok.world());
                lastError = null;
            }
            case WorldControlWireProtocol.ErrorResponse error -> {
                lastError = error.message();
                LazyBuilderClientNetworking.notifyPlayer("LazyBuilder world: " + error.message());
            }
        }
    }

    public void reset() {
        worlds = List.of();
        lastError = null;
    }

    public List<WorldControlWireProtocol.WorldSummary> worlds() { return worlds; }
    public String lastError() { return lastError; }

    private void replace(WorldControlWireProtocol.WorldSummary updated) {
        boolean found = false;
        var builder = new java.util.ArrayList<WorldControlWireProtocol.WorldSummary>(worlds.size() + 1);
        for (WorldControlWireProtocol.WorldSummary world : worlds) {
            if (world.worldId().equals(updated.worldId())) {
                builder.add(updated);
                found = true;
            } else {
                builder.add(world);
            }
        }
        if (!found) builder.add(updated);
        worlds = List.copyOf(builder);
    }

    private static void send(WorldControlWireProtocol.Request request) {
        try {
            LazyBuilderClientNetworking.sendWorld(WorldControlWireProtocol.encodeRequest(request));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode world-control request", exception);
        }
    }
}
