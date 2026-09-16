package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRuntimeServiceTest {
    @Test
    void loadAndUnloadUseRuntimeTruthAndRemainIdempotent() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime);

        service.load(world.id());
        assertTrue(service.isLoaded(world.id()));
        assertEquals(1, runtime.loadCount);

        service.load(world.id());
        assertEquals(1, runtime.loadCount);

        service.unload(world.id());
        assertFalse(service.isLoaded(world.id()));
        assertEquals(1, runtime.unloadCount);

        service.unload(world.id());
        assertEquals(1, runtime.unloadCount);
    }

    @Test
    void runtimeFailuresLeavePaperTruthUnchanged() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime);

        runtime.failLoad = true;
        assertThrows(IllegalStateException.class, () -> service.load(world.id()));
        assertFalse(service.isLoaded(world.id()));

        runtime.failLoad = false;
        service.load(world.id());
        runtime.failUnload = true;
        assertThrows(IllegalStateException.class, () -> service.unload(world.id()));
        assertTrue(service.isLoaded(world.id()));
    }

    @Test
    void archivedWorldCannotLoad() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ARCHIVED);
        registry.register(world);
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime);

        assertThrows(IllegalStateException.class, () -> service.load(world.id()));
        assertEquals(0, runtime.loadCount);
    }

    @Test
    void externalLoadAndUnloadAreBlockedWhileHeavyOperationOwnsWorld() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        runtime.loaded = true;
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime, operations);

        try (WorldOperationCoordinator.Lease ignored = operations.acquire(world.id(), WorldOperationType.EXPORT)) {
            assertThrows(IllegalStateException.class, () -> service.unload(world.id()));
            assertTrue(service.isLoaded(world.id()));

            service.unloadDuringOperation(world.id());
            assertFalse(service.isLoaded(world.id()));

            assertThrows(IllegalStateException.class, () -> service.load(world.id()));
            service.loadDuringOperation(world.id());
            assertTrue(service.isLoaded(world.id()));
        }
    }

    @Test
    void runtimeFamilyPresenceCountsAsManagedWorldOccupancy() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        runtime.loaded = true;
        runtime.familyHasPlayers = true;
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime);

        assertTrue(service.hasPlayers(world.id()));
        assertThrows(IllegalStateException.class, () -> service.unload(world.id()));
        assertEquals(0, runtime.unloadCount);
    }

    @Test
    void liveSnapshotStateRoundTripsWithoutCollapsingDimensionAutosave() {
        WorldRegistry registry = new WorldRegistry();
        FakeRuntime runtime = new FakeRuntime();
        runtime.loaded = true;
        runtime.snapshotState = new WorldRuntimeGateway.LiveSnapshotState(Map.of(
                "Build", true,
                "Build_nether", false,
                "Build_the_end", true
        ));
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        WorldRuntimeService service = new WorldRuntimeService(registry, runtime);

        WorldRuntimeGateway.LiveSnapshotState state = service.beginLiveSnapshotDuringOperation(world.id());
        assertEquals(runtime.snapshotState, state);

        service.endLiveSnapshotDuringOperation(world.id(), state);
        assertEquals(state, runtime.restoredSnapshotState);
    }

    private static WorldRecord world(WorldLifecycle lifecycle) {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, lifecycle);
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private int loadCount;
        private int unloadCount;
        private boolean loaded;
        private boolean failLoad;
        private boolean failUnload;
        private boolean familyHasPlayers;
        private LiveSnapshotState snapshotState = LiveSnapshotState.single(true);
        private LiveSnapshotState restoredSnapshotState;

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {}
        @Override public void rollbackCreatedWorld(WorldRecord world) {}
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public boolean hasPlayers(WorldRecord world) { return familyHasPlayers; }

        @Override
        public void loadWorld(WorldRecord world) {
            loadCount++;
            if (failLoad) throw new IllegalStateException("load failed");
            loaded = true;
        }

        @Override
        public void unloadWorld(WorldRecord world) {
            unloadCount++;
            if (failUnload) throw new IllegalStateException("unload failed");
            loaded = false;
        }

        @Override public LiveSnapshotState beginManagedLiveSnapshot(WorldRecord world) { return snapshotState; }
        @Override public void endManagedLiveSnapshot(WorldRecord world, LiveSnapshotState state) { restoredSnapshotState = state; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {}
    }
}
