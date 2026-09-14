package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdleUnloadServiceTest {
    @Test
    void unloadsOrdinaryWorldAfterIdleTimeoutUnderExclusiveLease() {
        Fixture fixture = fixture(false);

        fixture.service.tick(1_000L);
        assertTrue(fixture.runtime.loaded);

        fixture.service.tick(31_000L);

        assertFalse(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(WorldOperationType.IDLE_UNLOAD, fixture.runtime.operationObservedDuringUnload);
        assertNull(fixture.operations.activeOperation(fixture.world.id()));
    }

    @Test
    void protectedWorldIsNeverTrackedForIdleUnload() {
        Fixture fixture = fixture(true);

        fixture.service.tick(1_000L);
        fixture.service.tick(61_000L);
        fixture.service.tick(121_000L);

        assertTrue(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.unloadCount);
    }

    @Test
    void playerPresenceResetsIdleWindow() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = world();
        registry.register(world);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        FakeRuntime runtime = new FakeRuntime(operations, world);
        boolean[] occupied = {false};
        WorldRuntimeService runtimeService = new WorldRuntimeService(
                registry, runtime, operations, ignored -> occupied[0]);
        WorldIdleUnloadService service = new WorldIdleUnloadService(
                registry,
                runtimeService,
                operations,
                ignored -> runtime.loaded,
                ignored -> occupied[0],
                ignored -> false,
                Duration.ofSeconds(30));

        service.tick(1_000L);
        occupied[0] = true;
        service.tick(20_000L);
        occupied[0] = false;
        service.tick(21_000L);
        service.tick(50_000L);
        assertTrue(runtime.loaded);

        service.tick(51_000L);
        assertFalse(runtime.loaded);
        assertEquals(1, runtime.unloadCount);
    }

    @Test
    void activeTeleportPreventsIdleUnloadAndStartsFreshIdleWindow() {
        Fixture fixture = fixture(false);

        fixture.service.tick(1_000L);
        try (WorldOperationCoordinator.Lease ignored =
                     fixture.operations.acquire(fixture.world.id(), WorldOperationType.TELEPORT)) {
            fixture.service.tick(31_000L);
            assertTrue(fixture.runtime.loaded);
            assertEquals(0, fixture.runtime.unloadCount);
        }

        fixture.service.tick(32_000L);
        fixture.service.tick(61_000L);
        assertTrue(fixture.runtime.loaded);

        fixture.service.tick(62_000L);
        assertFalse(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.unloadCount);
    }

    private static Fixture fixture(boolean protectedWorld) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = world();
        registry.register(world);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        FakeRuntime runtime = new FakeRuntime(operations, world);
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations, ignored -> false);
        WorldIdleUnloadService service = new WorldIdleUnloadService(
                registry,
                runtimeService,
                operations,
                ignored -> runtime.loaded,
                ignored -> false,
                ignored -> protectedWorld,
                Duration.ofSeconds(30));
        return new Fixture(runtime, service, operations, world);
    }

    private static WorldRecord world() {
        return new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
    }

    private record Fixture(
            FakeRuntime runtime,
            WorldIdleUnloadService service,
            WorldOperationCoordinator operations,
            WorldRecord world
    ) { }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private final WorldOperationCoordinator operations;
        private final WorldRecord world;
        private boolean loaded = true;
        private int unloadCount;
        private WorldOperationType operationObservedDuringUnload;

        private FakeRuntime(WorldOperationCoordinator operations, WorldRecord world) {
            this.operations = operations;
            this.world = world;
        }

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; }
        @Override public void unloadWorld(WorldRecord world) {
            operationObservedDuringUnload = operations.activeOperation(this.world.id());
            loaded = false;
            unloadCount++;
        }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }
}
