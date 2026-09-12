package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDeleteServiceTest {
    @Test
    void deleteStagesFolderThenRemovesDurableAndRuntimeOwnership() {
        Fixture fixture = fixture(true);

        WorldDeleteService.DeleteTask task = fixture.service.prepare(fixture.world.id(), "Build");
        assertEquals(WorldRuntimeState.UNLOADED, fixture.states.get(fixture.world.id()));

        fixture.service.executeFilePhase(task);
        fixture.service.finish(task);

        assertTrue(task.committed());
        assertTrue(fixture.registry.all().isEmpty());
        assertTrue(fixture.persistence.saved.isEmpty());
        assertTrue(fixture.files.stagedDelete);
        assertTrue(fixture.files.workspaceDeleted);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void persistenceFailureRestoresStagedFolderAndPreviousLoadedState() {
        Fixture fixture = fixture(true);
        fixture.persistence.failNextSave = true;
        WorldDeleteService.DeleteTask task = fixture.service.prepare(fixture.world.id(), "Build");

        assertThrows(IllegalStateException.class, () -> fixture.service.executeFilePhase(task));
        fixture.service.finish(task);

        assertFalse(task.committed());
        assertTrue(fixture.registry.find(fixture.world.id()).isPresent());
        assertTrue(fixture.files.restored);
        assertEquals(WorldRuntimeState.LOADED, fixture.states.get(fixture.world.id()));
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void deleteRequiresExactCanonicalFolderConfirmation() {
        Fixture fixture = fixture(false);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.prepare(fixture.world.id(), "build"));
    }

    private static Fixture fixture(boolean loaded) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE, true);
        registry.register(world);
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        states.initialize(world.id(), loaded ? WorldRuntimeState.LOADED : WorldRuntimeState.UNLOADED);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, states, runtime);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        FakeFiles files = new FakeFiles();
        WorldDeleteService service = new WorldDeleteService(
                registry, persistence, runtimeService, states, operations, files);
        return new Fixture(registry, states, persistence, operations, files, service, world);
    }

    private record Fixture(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
            MemoryPersistence persistence,
            WorldOperationCoordinator operations,
            FakeFiles files,
            WorldDeleteService service,
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

        private FakeRuntime(boolean loaded) {
            this.loaded = loaded;
        }

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }

    private static final class FakeFiles implements WorldFileRepository {
        private boolean stagedDelete;
        private boolean restored;
        private boolean workspaceDeleted;

        @Override
        public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path stageDelete(WorldRecord world, UUID operationId) {
            stagedDelete = true;
            return Path.of("work", operationId.toString()).toAbsolutePath();
        }

        @Override
        public void publishStagedWorld(Path stagedWorld, String destinationFolder) {
            restored = true;
        }

        @Override public void deleteWorld(WorldRecord world) { }

        @Override
        public void deleteWorkspace(Path workspace) {
            workspaceDeleted = true;
        }
    }
}
