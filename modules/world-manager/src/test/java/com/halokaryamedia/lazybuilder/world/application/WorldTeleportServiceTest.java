package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTeleportServiceTest {
    @Test
    void teleportAutoLoadsUnloadedWorldBeforeMovingPlayer() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime);
        WorldTeleportService teleportService = new WorldTeleportService(registry, runtimeService, runtime);
        UUID playerId = UUID.randomUUID();

        assertEquals(world, teleportService.teleportToWorld(playerId, world.id()));
        assertEquals(1, runtime.loadCount);
        assertEquals(1, runtime.teleportCount);
        assertEquals(playerId, runtime.lastPlayerId);
        assertTrue(runtime.loaded);
        assertTrue(runtimeService.isLoaded(world.id()));
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private int loadCount;
        private int teleportCount;
        private UUID lastPlayerId;

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; loadCount++; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {
            teleportCount++;
            lastPlayerId = playerId;
        }
    }
}
