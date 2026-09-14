package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLifecycleServiceTest {
    @Test
    void archiveUnloadsAndPersistsWithoutMovingFiles() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, false);

        WorldRecord archived = fixture.service.archive(fixture.world.id());

        assertEquals(WorldLifecycle.ARCHIVED, archived.lifecycle());
        assertFalse(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(archived, fixture.persistence.saved.getFirst());
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void archiveRejectsProtectedFallbackWorldBeforeUnload() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, true);

        assertThrows(IllegalStateException.class, () -> fixture.service.archive(fixture.world.id()));

        assertEquals(WorldLifecycle.ACTIVE, fixture.registry.find(fixture.world.id()).orElseThrow().lifecycle());
        assertTrue(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.unloadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void restoreKeepsWorldUnloaded() {
        Fixture fixture = fixture(WorldLifecycle.ARCHIVED, false, false);

        WorldRecord restored = fixture.service.restore(fixture.world.id());

        assertEquals(WorldLifecycle.ACTIVE, restored.lifecycle());
        assertFalse(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.loadCount);
    }

    @Test
    void archivePersistenceFailureRestoresMetadataAndPreviousLoadedState() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, false);
        fixture.persistence.failNextSave = true;

        assertThrows(IllegalStateException.class, () -> fixture.service.archive(fixture.world.id()));

        WorldRecord current = fixture.registry.find(fixture.world.id()).orElseThrow();
        assertEquals(WorldLifecycle.ACTIVE, current.lifecycle());
        assertTrue(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void startupReconciliationUnloadsPersistedArchivedWorld() {
        Fixture fixture = fixture(WorldLifecycle.ARCHIVED, true, false);

        int reconciled = fixture.service.reconcilePersistedRuntimeState();

        assertEquals(1, reconciled);
        assertFalse(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(WorldLifecycle.ARCHIVED,
                fixture.registry.find(fixture.world.id()).orElseThrow().lifecycle());
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void startupReconciliationLeavesActiveWorldRuntimeUntouched() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, false);

        int reconciled = fixture.service.reconcilePersistedRuntimeState();

        assertEquals(0, reconciled);
        assertTrue(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.unloadCount);
    }

    @Test
    void startupReconciliationFailsClosedWhenBuildersAreInsideArchivedWorld() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ARCHIVED);
        registry.register(world);
        FakeRuntime runtime = new FakeRuntime(true);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(
                registry, runtime, operations, ignored -> true);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        WorldLifecycleService service = new WorldLifecycleService(
                registry, persistence, runtimeService, operations, ignored -> false);

        assertThrows(IllegalStateException.class, service::reconcilePersistedRuntimeState);
        assertTrue(runtime.loaded);
        assertEquals(0, runtime.unloadCount);
        assertFalse(operations.isBusy(world.id()));
    }

    private static Fixture fixture(WorldLifecycle lifecycle, boolean loaded, boolean protectedWorld) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(),
                "Build",
                "Build",
                WorldKind.FLAT,
                lifecycle
        );
        registry.register(world);

        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        WorldLifecycleService service = new WorldLifecycleService(
                registry,
                persistence,
                runtimeService,
                operations,
                ignored -> protectedWorld
        );
        return new Fixture(registry, runtime, persistence, operations, service, world);
    }

    private record Fixture(
            WorldRegistry registry,
            FakeRuntime runtime,
            MemoryPersistence persistence,
            WorldOperationCoordinator operations,
            WorldLifecycleService service,
            WorldRecord world
    ) {
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();
        private boolean failNextSave;

        @Override
        public List<WorldRecord> load() {
            return saved;
        }

        @Override
        public void save(List<WorldRecord> worlds) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("test failure");
            }
            saved = List.copyOf(worlds);
        }
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private int loadCount;
        private int unloadCount;

        private FakeRuntime(boolean loaded) {
            this.loaded = loaded;
        }

        @Override
        public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {
        }

        @Override
        public void rollbackCreatedWorld(WorldRecord world) {
        }

        @Override
        public boolean isLoaded(WorldRecord world) {
            return loaded;
        }

        @Override
        public void loadWorld(WorldRecord world) {
            loaded = true;
            loadCount++;
        }

        @Override
        public void unloadWorld(WorldRecord world) {
            loaded = false;
            unloadCount++;
        }

        @Override
        public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {
        }
    }
}
