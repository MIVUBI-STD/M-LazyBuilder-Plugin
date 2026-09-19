package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCreationServiceTest {
    @Test
    void flatAndVoidUseOneCreationPathAndPublishRegistryState() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeRuntime runtime = new FakeRuntime();
        BuildReadyPolicy policy = BuildReadyPolicy.defaults();
        WorldCreationService service = new WorldCreationService(registry, persistence, runtime, policy);

        WorldRecord flat = service.create("FlatBuild", "Flat Build", WorldKind.FLAT);
        WorldRecord empty = service.create("VoidBuild", "Void Build", WorldKind.VOID);

        assertEquals(List.of(flat, empty), registry.all());
        assertEquals(List.of(flat, empty), persistence.saved);
        assertEquals(List.of(WorldKind.FLAT, WorldKind.VOID), runtime.createdKinds);
        assertTrue(runtime.isLoaded(flat));
        assertTrue(runtime.isLoaded(empty));
        assertEquals(2, runtime.policies.size());
        assertEquals(policy, runtime.policies.getFirst());
        assertEquals(policy, runtime.policies.getLast());
    }

    @Test
    void createRejectsImportedKind() {
        WorldCreationService service = new WorldCreationService(
                new WorldRegistry(),
                new MemoryPersistence(),
                new FakeRuntime(),
                BuildReadyPolicy.defaults()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.create("Imported", "Imported", WorldKind.IMPORTED));
    }

    @Test
    void failedRuntimeRollbackPreservesCreateRecoveryMarker() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.failSave = true;
        FakeRuntime runtime = new FakeRuntime();
        runtime.failRollback = true;
        TrackingFiles files = new TrackingFiles();
        WorldCreationService service = new WorldCreationService(
                registry,
                persistence,
                runtime,
                files,
                BuildReadyPolicy.defaults()
        );

        assertThrows(IllegalStateException.class,
                () -> service.create("Build", "Build", WorldKind.FLAT));

        assertEquals(1, files.markCount);
        assertEquals(1, files.runtimeCreatedCount);
        assertEquals(0, files.clearCount,
                "failed runtime rollback must preserve durable create recovery authority");
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void persistenceFailureRollsBackRuntimeAndRegistry() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.failSave = true;
        FakeRuntime runtime = new FakeRuntime();
        WorldCreationService service = new WorldCreationService(
                registry,
                persistence,
                runtime,
                BuildReadyPolicy.defaults()
        );

        assertThrows(IllegalStateException.class,
                () -> service.create("Build", "Build", WorldKind.FLAT));

        assertTrue(registry.all().isEmpty());
        assertEquals(1, runtime.rollbackCount);
        assertFalse(runtime.createdKinds.isEmpty());
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private final List<WorldKind> createdKinds = new ArrayList<>();
        private final List<BuildReadyPolicy> policies = new ArrayList<>();
        private int rollbackCount;
        private boolean failRollback;

        @Override
        public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {
            createdKinds.add(world.kind());
            policies.add(policy);
        }

        @Override
        public void rollbackCreatedWorld(WorldRecord world) {
            rollbackCount++;
            if (failRollback) throw new IllegalStateException("rollback failed");
        }

        @Override
        public boolean isLoaded(WorldRecord world) {
            return createdKinds.contains(world.kind());
        }

        @Override public void loadWorld(WorldRecord world) { }
        @Override public void unloadWorld(WorldRecord world) { }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }

    private static final class TrackingFiles implements WorldFileRepository {
        private int markCount;
        private int runtimeCreatedCount;
        private int clearCount;

        @Override public void markCreatePending(UUID operationId, String folderName) { markCount++; }
        @Override public void markCreateRuntimeCreated(UUID operationId, String folderName) { runtimeCreatedCount++; }
        @Override public void clearCreatePending(UUID operationId, String folderName) { clearCount++; }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) {
            throw new UnsupportedOperationException();
        }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) {
            throw new UnsupportedOperationException();
        }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) {
            throw new UnsupportedOperationException();
        }
        @Override public void deleteWorld(WorldRecord world) {
            throw new UnsupportedOperationException();
        }
        @Override public void deleteWorkspace(Path workspace) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();
        private boolean failSave;

        @Override public List<WorldRecord> load() { return saved; }

        @Override
        public void save(List<WorldRecord> worlds) throws IOException {
            if (failSave) throw new IOException("test failure");
            saved = List.copyOf(worlds);
        }
    }
}
