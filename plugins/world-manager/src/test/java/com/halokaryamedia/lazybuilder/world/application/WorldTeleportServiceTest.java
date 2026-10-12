package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTeleportServiceTest {
    @Test
    void teleportAutoLoadsUnloadedWorldBeforeMovingPlayer() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        WorldTeleportService teleportService = new WorldTeleportService(registry, runtimeService, runtime, operations);
        UUID playerId = UUID.randomUUID();

        assertEquals(world, teleportService.teleportToWorld(playerId, world.id()));
        assertEquals(1, runtime.loadCount);
        assertEquals(1, runtime.teleportCount);
        assertEquals(playerId, runtime.lastPlayerId);
        assertTrue(runtime.loaded);
        assertTrue(runtimeService.isLoaded(world.id()));
    }

    @Test
    void teleportCannotInterleaveWithArchiveOperation() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        WorldTeleportService teleportService = new WorldTeleportService(registry, runtimeService, runtime, operations);

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(world.id(), WorldOperationType.ARCHIVE)) {
            assertThrows(IllegalStateException.class,
                    () -> teleportService.teleportToWorld(UUID.randomUUID(), world.id()));
        }

        assertEquals(0, runtime.loadCount);
        assertEquals(0, runtime.teleportCount);
    }

    @Test
    void asyncTeleportHoldsWorldAndPlayerOwnershipUntilArrival() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        runtime.pendingTeleport = new CompletableFuture<>();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        WorldTeleportService teleport = new WorldTeleportService(registry, runtimeService, runtime, operations);
        UUID player = UUID.randomUUID();

        var future = teleport.teleportToWorldAsync(player, world.id());
        assertTrue(!future.isDone());
        assertEquals(WorldOperationType.TELEPORT, operations.activeOperation(world.id()));
        assertThrows(IllegalStateException.class, () -> teleport.teleportToWorldAsync(player, world.id()));
        runtime.pendingTeleport.complete(null);

        assertEquals(world, future.join());
        assertTrue(!operations.isBusy(world.id()));
        assertEquals(world, teleport.teleportToWorldAsync(player, world.id()).join());
    }

    @Test
    void failedAsyncTeleportReleasesAllLeasesWithoutReportingSuccess() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        runtime.pendingTeleport = new CompletableFuture<>();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        WorldTeleportService teleport = new WorldTeleportService(registry, runtimeService, runtime, operations);
        UUID player = UUID.randomUUID();

        var future = teleport.teleportToWorldAsync(player, world.id());
        runtime.pendingTeleport.completeExceptionally(new IllegalStateException("unsafe landing"));
        assertThrows(java.util.concurrent.CompletionException.class, future::join);
        assertTrue(!operations.isBusy(world.id()));
        runtime.pendingTeleport = null;
        assertEquals(world, teleport.teleportToWorldAsync(player, world.id()).join());
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private int loadCount;
        private int teleportCount;
        private UUID lastPlayerId;
        private CompletableFuture<Void> pendingTeleport;

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; loadCount++; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {
            teleportCount++;
            lastPlayerId = playerId;
        }
        @Override public CompletableFuture<Void> teleportPlayerToSpawnAsync(
                UUID playerId, WorldRecord world, WorldGameMode gameMode) {
            if (pendingTeleport != null) return pendingTeleport;
            return WorldRuntimeGateway.super.teleportPlayerToSpawnAsync(playerId, world, gameMode);
        }
    }
}
