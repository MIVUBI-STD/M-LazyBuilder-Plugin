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

class WorldLifecycleServiceTest {
    @Test
    void archiveUnloadsDisablesAutoLoadAndPersistsWithoutMovingFiles() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, true);

        WorldRecord archived = fixture.service.archive(fixture.world.id());

        assertEquals(WorldLifecycle.ARCHIVED, archived.lifecycle());
        assertFalse(archived.autoLoad());
        assertEquals(WorldRuntimeState.UNLOADED, fixture.states.get(fixture.world.id()));
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(archived, fixture.persistence.saved.getFirst());
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void restoreKeepsWorldUnloadedAndAutoLoadOff() {
        Fixture fixture = fixture(WorldLifecycle.ARCHIVED, false, false);

        WorldRecord restored = fixture.service.restore(fixture.world.id());

        assertEquals(WorldLifecycle.ACTIVE, restored.lifecycle());
        assertFalse(restored.autoLoad());
        assertEquals(WorldRuntimeState.UNLOADED, fixture.states.get(fixture.world.id()));
        assertEquals(0, fixture.runtime.loadCount);
    }

    @Test
    void archivePersistenceFailureRestoresMetadataAndPreviousLoadedState() {
        Fixture fixture = fixture(WorldLifecycle.ACTIVE, true, true);
        fixture.persistence.failNextSave = true;

        assertThrows(IllegalStateException.class, () -> fixture.service.archive(fixture.world.id()));

        WorldRecord current = fixture.registry.find(fixture.world.id()).orElseThrow();
        assertEquals(WorldLifecycle.ACTIVE, current.lifecycle());
        assertEquals(WorldRuntimeState.LOADED, fixture.states.get(fixture.world.id()));
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    private static Fixture fixture(WorldLifecycle lifecycle, boolean autoLoad, boolean loaded) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(),
                "Build",
                "Build",
                WorldKind.FLAT,
                lifecycle,
                autoLoad
        );
        registry.register(world);

        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        states.initialize(world.id(), loaded ? WorldRuntimeState.LOADED : WorldRuntimeState.UNLOADED);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, states, runtime);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldLifecycleService service = new WorldLifecycleService(
                registry,
                persistence,
                runtimeService,
                states,
                operations
        );
        return new Fixture(registry, states, runtime, persistence, operations, service, world);
    }

    private record Fixture(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
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
